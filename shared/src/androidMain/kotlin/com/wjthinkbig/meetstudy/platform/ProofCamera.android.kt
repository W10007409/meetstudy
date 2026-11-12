package com.wjthinkbig.meetstudy.platform

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.wjthinkbig.meetstudy.domain.AutoCaptureGate
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "MeetStudyProof"
private const val ANALYSIS_GAP_MS = 200L // ~5 fps
private const val OCR_GAP_MS = 400L
private const val MIN_CHARS = 10
private val GREEN = Color(0xFF4CAF50)

/** Upright analysis-frame size and the quad found in it (image pixels); null quad = nothing found. */
private class Overlay(val imgW: Int, val imgH: Int, val quad: FloatArray?, val progress: Float, val chars: Int)

private fun uprightFrom(proxy: ImageProxy): Bitmap {
    val src = proxy.toBitmap()
    val deg = proxy.imageInfo.rotationDegrees
    if (deg == 0) return src
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, Matrix().apply { postRotate(deg.toFloat()) }, false).also { src.recycle() }
}

@Composable
actual fun ProofCamera(sessionId: String, onCaptured: (photoPath: String) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val captured = rememberUpdatedState(onCaptured)
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; denied = !it }
    DisposableEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
        onDispose {}
    }
    if (!granted) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(if (denied) "카메라 권한이 없어서 인증할 수 없어요" else "카메라 권한이 필요해요", fontSize = 22.sp)
            CancelButton(onCancel)
        }
        return
    }

    var overlay by remember { mutableStateOf<Overlay?>(null) }
    var shot by remember { mutableStateOf(false) }
    val controller = remember { LifecycleCameraController(context) }

    DisposableEffect(Unit) {
        val analysisExec = Executors.newSingleThreadExecutor()
        val captureExec = Executors.newSingleThreadExecutor()
        val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        val gate = AutoCaptureGate(minChars = MIN_CHARS)
        val capturing = AtomicBoolean(false)
        val disposed = AtomicBoolean(false)
        // Analysis-space quad + frame size, the fallback if full-res detection fails.
        class Last(val quad: FloatArray, val w: Int, val h: Int)
        val last = AtomicReference<Last?>(null)
        var lastFrameMs = 0L
        var lastOcrMs = 0L
        var chars = 0

        fun finish(path: String?) {
            ContextCompat.getMainExecutor(context).execute {
                if (disposed.get()) return@execute
                if (path != null) captured.value(path) else { capturing.set(false); shot = false; gate.reset() }
            }
        }

        fun process(image: ImageProxy) {
            try {
                val t0 = SystemClock.elapsedRealtime()
                val bmp = try { uprightFrom(image) } finally { image.close() }
                val fallback = last.get()
                val q = QuadDetector.find(bmp) ?: fallback?.let { f ->
                    val sx = bmp.width.toFloat() / f.w; val sy = bmp.height.toFloat() / f.h
                    FloatArray(8) { f.quad[it] * (if (it % 2 == 0) sx else sy) }
                }
                if (q == null) { bmp.recycle(); return finish(null) }
                val warped = try { QuadDetector.warp(bmp, q) } finally { bmp.recycle() }
                val file = File(context.filesDir, "proofs").apply { mkdirs() }.resolve("$sessionId.jpg")
                try {
                    file.outputStream().use { warped.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                } finally { warped.recycle() }
                Log.i(TAG, "captured totalMs=${SystemClock.elapsedRealtime() - t0}")
                finish(file.path)
            } catch (e: Exception) {
                Log.w(TAG, "capture failed: " + e.javaClass.simpleName)
                finish(null)
            }
        }

        controller.apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(LifecycleCameraController.IMAGE_ANALYSIS or LifecycleCameraController.IMAGE_CAPTURE)
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            // Higher than the default 640x480 so small workbook print stays legible for OCR.
            imageAnalysisResolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(ResolutionStrategy(Size(1280, 960), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)).build()
            setImageAnalysisAnalyzer(analysisExec) { proxy ->
                try {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastFrameMs < ANALYSIS_GAP_MS || capturing.get() || disposed.get()) return@setImageAnalysisAnalyzer
                    lastFrameMs = now
                    val bmp = uprightFrom(proxy)
                    try {
                        val q = QuadDetector.find(bmp)
                        Log.d(TAG, "quad ms=${SystemClock.elapsedRealtime() - now} found=${q != null} frame=${bmp.width}x${bmp.height}")
                        if (q == null) {
                            chars = 0; lastOcrMs = 0
                        } else if (now - lastOcrMs >= OCR_GAP_MS) {
                            lastOcrMs = now
                            val t = SystemClock.elapsedRealtime()
                            chars = try {
                                countChars(Tasks.await(recognizer.process(InputImage.fromBitmap(bmp, 0)), 2, TimeUnit.SECONDS), q)
                            } catch (e: Exception) {
                                Log.w(TAG, "ocr failed: " + e.javaClass.simpleName); 0
                            }
                            Log.d(TAG, "ocr ms=${SystemClock.elapsedRealtime() - t} chars=$chars")
                        }
                        val progress = gate.onFrame(now, q != null, chars)
                        overlay = Overlay(bmp.width, bmp.height, q, progress, chars)
                        if (q != null) last.set(Last(q, bmp.width, bmp.height))
                        if (progress >= 1f && capturing.compareAndSet(false, true)) {
                            shot = true
                            ContextCompat.getMainExecutor(context).execute {
                                if (disposed.get()) return@execute
                                takePicture(captureExec, object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(image: ImageProxy) = process(image)
                                    override fun onError(exception: ImageCaptureException) {
                                        Log.w(TAG, "takePicture failed: " + exception.javaClass.simpleName)
                                        finish(null)
                                    }
                                })
                            }
                        }
                    } finally { bmp.recycle() }
                } catch (e: Exception) {
                    Log.w(TAG, "frame dropped: " + e.javaClass.simpleName)
                } finally {
                    proxy.close()
                }
            }
            bindToLifecycle(lifecycleOwner)
        }
        onDispose {
            disposed.set(true)
            controller.unbind()
            analysisExec.shutdown()
            captureExec.shutdown()
            // Let an in-flight frame finish before the recognizer closes.
            analysisExec.awaitTermination(1, TimeUnit.SECONDS)
            recognizer.close()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { PreviewView(it).apply { this.controller = controller } })
        val o = overlay
        val ready = o?.quad != null && o.chars >= MIN_CHARS
        Canvas(Modifier.fillMaxSize()) {
            if (o == null) return@Canvas
            // PreviewView default FILL_CENTER: uniform scale to cover, centered.
            val s = maxOf(size.width / o.imgW, size.height / o.imgH)
            val ox = (size.width - o.imgW * s) / 2; val oy = (size.height - o.imgH * s) / 2
            o.quad?.let { q ->
                val path = Path().apply {
                    moveTo(q[0] * s + ox, q[1] * s + oy)
                    for (i in 1..3) lineTo(q[i * 2] * s + ox, q[i * 2 + 1] * s + oy)
                    close()
                }
                drawPath(path, if (ready) GREEN else Color.White, style = Stroke(width = 6.dp.toPx()))
            }
            if (o.progress > 0f) {
                val r = 48.dp.toPx()
                drawArc(GREEN, -90f, 360f * o.progress, false, Offset(center.x - r, center.y - r), GSize(r * 2, r * 2), style = Stroke(10.dp.toPx()))
            }
        }
        val hint = when {
            shot -> "찰칵!"
            o?.quad == null -> "문제집을 네모 안에 담아 주세요"
            !ready -> "글자가 잘 보이게 가까이"
            else -> "그대로 가만히 있어요"
        }
        Text(hint, color = Color.White, fontSize = 24.sp, modifier = Modifier.align(Alignment.TopCenter).padding(24.dp).background(Color(0x99000000)).padding(12.dp))
        CancelButton(onCancel)
    }
}

@Composable
private fun BoxScope.CancelButton(onCancel: () -> Unit) =
    Button(onClick = onCancel, modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp)) { Text("다음에 할게요", fontSize = 20.sp) }

/** Non-whitespace characters of recognized elements whose centers lie inside the quad's bounding box. */
private fun countChars(text: com.google.mlkit.vision.text.Text, quad: FloatArray): Int {
    val xs = listOf(quad[0], quad[2], quad[4], quad[6]); val ys = listOf(quad[1], quad[3], quad[5], quad[7])
    val l = xs.min(); val r = xs.max(); val t = ys.min(); val b = ys.max()
    return text.textBlocks.flatMap { it.lines }.flatMap { it.elements }
        .filter { e -> e.boundingBox?.let { it.centerX() >= l && it.centerX() <= r && it.centerY() >= t && it.centerY() <= b } == true }
        .sumOf { e -> e.text.count { !it.isWhitespace() } }
}
