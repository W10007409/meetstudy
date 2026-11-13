package com.wjthinkbig.meetstudy.platform

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.viewinterop.UIKitView
import com.wjthinkbig.meetstudy.domain.DebugFrame
import com.wjthinkbig.meetstudy.domain.FaceBox
import com.wjthinkbig.meetstudy.domain.FrameSignals
import com.wjthinkbig.meetstudy.domain.eyeOpenFromAspect
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.get
import kotlinx.cinterop.useContents
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDevicePositionFront
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPreset640x480
import platform.AVFoundation.AVCaptureVideoDataOutput
import platform.AVFoundation.AVCaptureVideoDataOutputSampleBufferDelegateProtocol
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.QuartzCore.CATransaction
import platform.UIKit.UIView
import platform.Vision.VNDetectFaceLandmarksRequest
import platform.Vision.VNDetectHumanBodyPoseRequest
import platform.Vision.VNFaceLandmarkRegion2D
import platform.Vision.VNFaceObservation
import platform.Vision.VNHumanBodyPoseObservation
import platform.Vision.VNHumanBodyPoseObservationJointNameLeftShoulder
import platform.Vision.VNHumanBodyPoseObservationJointNameNose
import platform.Vision.VNHumanBodyPoseObservationJointNameRightShoulder
import platform.Vision.VNImageRequestHandler
import platform.darwin.NSObject
import platform.darwin.QOS_CLASS_USER_INITIATED
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create
import kotlin.math.PI

private const val MIN_FRAME_GAP_MS = 200L
private const val POSE_GAP_MS = 500L
private const val RAD_TO_DEG = (180.0 / PI).toFloat()

// ponytail: assumes Vision pitch is positive when the head tilts down (ML Kit is the opposite). Verify on a device with
// the recognition view: looking down must make pitch negative; flip this sign if it does not.
private const val VISION_PITCH_SIGN = -1f

/** Session shared by the frame source (analysis) and CameraPreview. Null until permission is granted. */
private var activeSession by mutableStateOf<AVCaptureSession?>(null)

private class IosFrameSource : FrameSource {
    val flow = MutableSharedFlow<FrameSignals>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val signals: Flow<FrameSignals> = flow
    override val latestFace = MutableStateFlow<FaceCrop?>(null) // stays null: no identity model on iOS
}

private fun background(block: () -> Unit) = dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED.toLong(), 0u), block)

// ponytail: enablePhone is ignored, phone detection needs the TFLite EfficientDet model on iOS.
@Composable
actual fun rememberFrameSource(enablePhone: Boolean): FrameSource {
    val source = remember { IosFrameSource() }
    var granted by remember { mutableStateOf(AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) == AVAuthorizationStatusAuthorized) }
    DisposableEffect(Unit) {
        if (!granted) AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { ok -> dispatch_async(dispatch_get_main_queue()) { granted = ok } }
        onDispose {}
    }
    var output by remember { mutableStateOf<AVCaptureVideoDataOutput?>(null) }
    DisposableEffect(granted) {
        if (!granted) return@DisposableEffect onDispose {}
        val device = AVCaptureDevice.defaultDeviceWithDeviceType(AVCaptureDeviceTypeBuiltInWideAngleCamera, AVMediaTypeVideo, AVCaptureDevicePositionFront)
        val input = device?.let { AVCaptureDeviceInput.deviceInputWithDevice(it, null) }
            ?: return@DisposableEffect onDispose {} // no front camera (simulator): no signals, like a covered lens
        val session = AVCaptureSession()
        val out = AVCaptureVideoDataOutput().apply { alwaysDiscardsLateVideoFrames = true }
        val analyzer = FrameAnalyzer(source)
        session.beginConfiguration()
        session.sessionPreset = AVCaptureSessionPreset640x480
        if (session.canAddInput(input)) session.addInput(input)
        if (session.canAddOutput(out)) session.addOutput(out)
        // Unmirrored upright frames, like the Android analysis images.
        out.connectionWithMediaType(AVMediaTypeVideo)?.let { c ->
            if (c.supportsVideoMirroring) { c.automaticallyAdjustsVideoMirroring = false; c.videoMirrored = false }
            c.videoOrientation = currentVideoOrientation()
        }
        session.commitConfiguration()
        out.setSampleBufferDelegate(analyzer, dispatch_queue_create("meetstudy.frames", null))
        output = out
        activeSession = session
        background { session.startRunning() }
        onDispose {
            activeSession = null
            output = null
            out.setSampleBufferDelegate(null, null)
            background { session.stopRunning() }
        }
    }
    // The window resizes on rotation: keep analysis frames upright.
    val window = LocalWindowInfo.current.containerSize
    LaunchedEffect(window, output) {
        output?.connectionWithMediaType(AVMediaTypeVideo)?.videoOrientation = currentVideoOrientation()
    }
    return source
}

/** Runs on the capture queue; Vision requests are reused across frames. */
private class FrameAnalyzer(private val source: IosFrameSource) : NSObject(), AVCaptureVideoDataOutputSampleBufferDelegateProtocol {
    private val faceRequest = VNDetectFaceLandmarksRequest()
    private val poseRequest = VNDetectHumanBodyPoseRequest()
    private var lastFrameMs = 0L
    private var lastPoseMs = 0L
    private var lastPose: PoseReading? = null

    @ObjCSignatureOverride
    override fun captureOutput(output: AVCaptureOutput, didOutputSampleBuffer: CMSampleBufferRef?, fromConnection: AVCaptureConnection) {
        try {
            val now = monotonicNowMs()
            if (now - lastFrameMs < MIN_FRAME_GAP_MS) return
            lastFrameMs = now
            val pixels = CMSampleBufferGetImageBuffer(didOutputSampleBuffer) ?: return
            val w = CVPixelBufferGetWidth(pixels).toDouble()
            val h = CVPixelBufferGetHeight(pixels).toDouble()
            val handler = VNImageRequestHandler(cVPixelBuffer = pixels, options = emptyMap<Any?, Any?>())
            if (!handler.performRequests(listOf(faceRequest), null)) return
            val face = faceRequest.results?.filterIsInstance<VNFaceObservation>()
                ?.maxByOrNull { o -> o.boundingBox.useContents { size.width * size.height } }
            // Pose only matters without a face; the recognition view wants it all the time.
            val debugOn = DebugFlags.recognitionView
            if ((face == null || debugOn) && now - lastPoseMs >= POSE_GAP_MS) {
                lastPoseMs = now
                if (handler.performRequests(listOf(poseRequest), null)) {
                    lastPose = poseRequest.results?.firstOrNull()?.let { poseReading(it as VNHumanBodyPoseObservation, h) }
                }
            }
            val reading = face?.let {
                FaceReading(
                    (it.yaw?.floatValue ?: 0f) * RAD_TO_DEG,
                    (it.pitch?.floatValue ?: 0f) * RAD_TO_DEG * VISION_PITCH_SIGN,
                    eyeOpen(it.landmarks?.leftEye, w, h),
                    eyeOpen(it.landmarks?.rightEye, w, h),
                )
            }
            val base = toSignals(now, reading, lastPose)
            // Vision boxes are normalized with a bottom-left origin; FaceBox / DebugFrame use top-left.
            val box = face?.boundingBox?.let(::topLeftBox)
            val debug = if (!debugOn) null else DebugFrame(
                w.toInt(), h.toInt(),
                box?.let { floatArrayOf(it[0] * w.toFloat(), it[1] * h.toFloat(), it[2] * w.toFloat(), it[3] * h.toFloat()) },
                null, // ponytail: no skeleton overlay on iOS; map Vision joints to the 33 ML Kit slots if needed
            )
            source.flow.tryEmit(
                base.copy(
                    faceBox = box?.let { FaceBox((it[0] + it[2]) / 2, (it[1] + it[3]) / 2, it[3] - it[1]) },
                    debug = debug,
                ),
            )
        } catch (e: Exception) {
            // Drop this frame; a detector failure must not kill the analyzer.
            println("MeetStudyFrames: frame dropped: " + e::class.simpleName)
        }
    }
}

/** left, top, right, bottom in 0..1, top-left origin. */
private fun topLeftBox(r: CValue<CGRect>): FloatArray = r.useContents {
    floatArrayOf(
        origin.x.toFloat(), (1 - origin.y - size.height).toFloat(),
        (origin.x + size.width).toFloat(), (1 - origin.y).toFloat(),
    )
}

private fun eyeOpen(region: VNFaceLandmarkRegion2D?, w: Double, h: Double): Float? {
    val n = region?.pointCount?.toInt() ?: return null
    if (n < 4) return null
    val pts = region.pointsInImageOfSize(CGSizeMake(w, h)) ?: return null
    var minX = Double.MAX_VALUE; var maxX = -Double.MAX_VALUE
    var minY = Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
    for (i in 0 until n) {
        val p = pts[i]
        minX = minOf(minX, p.x); maxX = maxOf(maxX, p.x)
        minY = minOf(minY, p.y); maxY = maxOf(maxY, p.y)
    }
    val width = maxX - minX
    return if (width <= 0) null else eyeOpenFromAspect(((maxY - minY) / width).toFloat())
}

/** Shoulders and nose in image pixels, y down (Vision points are normalized, y up). */
private fun poseReading(obs: VNHumanBodyPoseObservation, h: Double): PoseReading? {
    val l = obs.recognizedPointForJointName(VNHumanBodyPoseObservationJointNameLeftShoulder, null) ?: return null
    val r = obs.recognizedPointForJointName(VNHumanBodyPoseObservationJointNameRightShoulder, null) ?: return null
    val nose = obs.recognizedPointForJointName(VNHumanBodyPoseObservationJointNameNose, null)?.takeIf { it.confidence > 0.1f }
    return PoseReading(
        l.confidence, r.confidence,
        (((1 - l.y) + (1 - r.y)) / 2 * h).toFloat(),
        nose?.let { ((1 - it.y) * h).toFloat() },
    )
}

@Composable
actual fun CameraPreview(modifier: Modifier) {
    val session = activeSession
    if (session == null) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("카메라 권한이 필요해요") }
        return
    }
    key(session) { UIKitView(factory = { PreviewView(session) }, modifier = modifier) }
}

/** Mirrored, aspect-filled preview like the Android PreviewView; follows rotation on layout. */
private class PreviewView(session: AVCaptureSession) : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    private val preview = AVCaptureVideoPreviewLayer(session = session).apply { videoGravity = AVLayerVideoGravityResizeAspectFill }

    init { layer.addSublayer(preview) }

    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        preview.frame = bounds
        preview.connection?.videoOrientation = currentVideoOrientation()
        CATransaction.commit()
    }
}
