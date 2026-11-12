package com.wjthinkbig.meetstudy.platform

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.wjthinkbig.meetstudy.domain.FrameSignals
import com.wjthinkbig.meetstudy.domain.IDENTITY_THRESHOLD
import com.wjthinkbig.meetstudy.domain.cosine
import com.google.android.gms.tasks.Task
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

private const val MIN_FRAME_GAP_MS = 200L
private const val POSE_GAP_MS = 500L
private const val CROP_SIZE = 112
private const val TAG = "MeetStudyFrames"

/** Null on timeout so a stuck detector task cannot block the analyzer thread forever. */
private fun <T> awaitOrNull(task: Task<T>): T? =
    try { Tasks.await(task, 1, TimeUnit.SECONDS) } catch (e: TimeoutException) { null }

/** Camera controller shared by the frame source (analysis) and CameraPreview. Null until permission is granted. */
private var activeController by mutableStateOf<LifecycleCameraController?>(null)

private class AndroidFrameSource : FrameSource {
    val flow = MutableSharedFlow<FrameSignals>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val signals: Flow<FrameSignals> = flow
    override val latestFace = MutableStateFlow<FaceCrop?>(null)
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
actual fun rememberFrameSource(enablePhone: Boolean): FrameSource {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val source = remember { AndroidFrameSource() }
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    DisposableEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
        onDispose {}
    }
    DisposableEffect(granted) {
        if (!granted) return@DisposableEffect onDispose {}
        val faceDetector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setMinFaceSize(0.15f)
                .build(),
        )
        val poseDetector = PoseDetection.getClient(
            PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build(),
        )
        val executor = Executors.newSingleThreadExecutor()
        // Identity check only when someone is enrolled; read once, so re-enrolling needs a new frame source.
        val enrolled = FaceVault.load()
        val embedder = enrolled?.let { FaceEmbedder(context.applicationContext) }?.takeIf { it.available }
        val schedule = IdentitySchedule()
        var lastFrameMs = 0L
        var lastPoseMs = 0L
        var lastPose: PoseReading? = null
        val controller = LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            setEnabledUseCases(LifecycleCameraController.IMAGE_ANALYSIS)
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            setImageAnalysisAnalyzer(executor) { proxy ->
                try {
                    val now = SystemClock.elapsedRealtime()
                    val media = proxy.image
                    if (now - lastFrameMs < MIN_FRAME_GAP_MS || media == null) return@setImageAnalysisAnalyzer
                    lastFrameMs = now
                    val input = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                    val faces = awaitOrNull(faceDetector.process(input)) ?: return@setImageAnalysisAnalyzer
                    val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                    var identityMatch: Boolean? = null
                    if (face != null) {
                        val crop = try {
                            cropFace(proxy, face.boundingBox)
                        } catch (e: Exception) {
                            Log.w(TAG, "crop failed: " + e.javaClass.simpleName)
                            null
                        }
                        source.latestFace.value = crop
                        if (embedder != null && enrolled != null && crop != null && schedule.due(now)) {
                            schedule.checked(now)
                            identityMatch = try {
                                val sim = cosine(embedder.embedBlocking(crop), enrolled)
                                Log.i(FACE_TAG, "similarity=$sim")
                                sim >= IDENTITY_THRESHOLD
                            } catch (e: Exception) {
                                Log.w(FACE_TAG, "embed failed: " + e.javaClass.simpleName)
                                null
                            }
                        }
                    } else {
                        source.latestFace.value = null
                        schedule.faceGone()
                        if (now - lastPoseMs >= POSE_GAP_MS) {
                            lastPoseMs = now
                            awaitOrNull(poseDetector.process(input))?.let { lastPose = poseReading(it) }
                        }
                    }
                    val reading = face?.let {
                        FaceReading(it.headEulerAngleY, it.headEulerAngleX, it.leftEyeOpenProbability, it.rightEyeOpenProbability)
                    }
                    source.flow.tryEmit(toSignals(now, reading, lastPose).copy(identityMatch = identityMatch))
                } catch (e: Exception) {
                    // Drop this frame; a detector failure must not kill the analyzer.
                    Log.w(TAG, "frame dropped: " + e.javaClass.simpleName)
                } finally {
                    proxy.close()
                }
            }
            bindToLifecycle(lifecycleOwner)
        }
        activeController = controller
        onDispose {
            activeController = null
            controller.unbind()
            executor.shutdown()
            // embedBlocking/close share a lock, so an in-flight embed finishes before the interpreter closes.
            embedder?.close()
            faceDetector.close()
            poseDetector.close()
        }
    }
    return source
}

private fun poseReading(pose: Pose): PoseReading? {
    val l = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER) ?: return null
    val r = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER) ?: return null
    return PoseReading(
        l.inFrameLikelihood, r.inFrameLikelihood,
        (l.position.y + r.position.y) / 2f,
        pose.getPoseLandmark(PoseLandmark.NOSE)?.position?.y,
    )
}

/** Upright bounding-box crop scaled to 112x112 (no landmark alignment yet; Task 6 may refine). */
private fun cropFace(proxy: ImageProxy, box: Rect): FaceCrop? {
    val m = Matrix().apply { postRotate(proxy.imageInfo.rotationDegrees.toFloat()) }
    val src = proxy.toBitmap()
    val upright = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, false)
    val c = clipRect(box.left, box.top, box.right, box.bottom, upright.width, upright.height) ?: return null
    val scaled = Bitmap.createScaledBitmap(Bitmap.createBitmap(upright, c[0], c[1], c[2], c[3]), CROP_SIZE, CROP_SIZE, true)
    val px = IntArray(CROP_SIZE * CROP_SIZE)
    scaled.getPixels(px, 0, CROP_SIZE, 0, 0, CROP_SIZE, CROP_SIZE)
    return FaceCrop(CROP_SIZE, px)
}

@Composable
actual fun CameraPreview(modifier: Modifier) {
    val controller = activeController
    if (controller == null) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("카메라 권한이 필요해요") }
        return
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx -> PreviewView(ctx) },
        update = { it.controller = controller },
    )
}
