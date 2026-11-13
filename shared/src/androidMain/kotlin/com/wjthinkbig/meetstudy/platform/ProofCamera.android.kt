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
import com.wjthinkbig.meetstudy.domain.looksLikeDocument
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
private class Overlay(val imgW: Int, val imgH: Int, val quad: FloatArray?, val progress: Float, val ready: Boolean)

/** Recognized text counted for the gate: characters and the union of their boxes (image pixels), or null box if none. */
private class TextStats(val chars: Int, val box: android.graphics.Rect?)

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
        // fromText = the page edges were out of frame and the quad is the text bounds; full-res edge search is skipped then.
        class Last(val quad: FloatArray, val w: Int, val h: Int, val fromText: Boolean)
        val last = AtomicReference<Last?>(null)
        var lastFrameMs = 0L
        var lastOcrMs = 0L
        var text = TextStats(0, null)

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
                val scaled = fallback?.let { f ->
                    val sx = bmp.width.toFloat() / f.w; val sy = bmp.height.toFloat() / f.h
                    FloatArray(8) { f.quad[it] * (if (it % 2 == 0) sx else sy) }
                }
                val q = if (fallback?.fromText == true) scaled else QuadDetector.find(bmp) ?: scaled
                if (q == null) { bmp.recycle(); return finish(null) }
                val warped = try { QuadDetector.warp(bmp, q) } finally { bmp.recycle() }
                val file = File(context.filesDir, "proofs").apply { mkdirs() }.resolve("$sessionId.jpg")
                try {
                    file.outputStream().use { warped.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                } finally { warped.recycle() }
                Log.i(TAG, "captured src=${bmp.width}x${bmp.height} out=${warped.width}x${warped.height} totalMs=${SystemClock.elapsedRealtime() - t0}")
                finish(file.path)
            } catch (e: Exception) {
                Log.w(TAG, "capture failed: " + e.javaClass.simpleName)
                finish(null)
            } catch (e: OutOfMemoryError) {
                Log.w(TAG, "capture failed: OutOfMemoryError")
                finish(null)
            }
        }

        controller.apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(LifecycleCameraController.IMAGE_ANALYSIS or LifecycleCameraController.IMAGE_CAPTURE)
            // Output is capped at 1600px anyway; sensor-max capture risks OOM on low-end tablets.
            imageCaptureResolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(ResolutionStrategy(Size(1920, 1440), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)).build()
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
                        // OCR runs with or without edges: up close the page edges leave the frame but the text is clearest.
                        if (now - lastOcrMs >= OCR_GAP_MS) {
                            lastOcrMs = now
                            val t = SystemClock.elapsedRealtime()
                            text = try {
                                textStats(Tasks.await(recognizer.process(InputImage.fromBitmap(bmp, 0)), 2, TimeUnit.SECONDS), q)
                            } catch (e: Exception) {
                                Log.w(TAG, "ocr failed: " + e.javaClass.simpleName); TextStats(0, null)
                            }
                            Log.d(TAG, "ocr ms=${SystemClock.elapsedRealtime() - t} chars=${text.chars} box=${text.box}")
                        }
                        val areaFrac = text.box?.let { it.width().toFloat() * it.height() / (bmp.width * bmp.height) } ?: 0f
                        val ready = looksLikeDocument(q != null, text.chars, areaFrac, MIN_CHARS)
                        val textQuad = if (q == null && ready) text.box?.let { expandedRectQuad(it, bmp.width, bmp.height) } else null
                        val shown = q ?: textQuad
                        val progress = gate.onFrame(now, q != null, text.chars, areaFrac)
                        overlay = Overlay(bmp.width, bmp.height, shown, progress, ready)
                        if (shown != null) last.set(Last(shown, bmp.width, bmp.height, fromText = q == null))
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
        val ready = o?.ready == true
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
            ready -> "그대로 가만히 있어요"
            o?.quad == null -> "공부한 문제집이나 책을 비춰 주세요"
            else -> "글자가 잘 보이게 조금 더 가까이"
        }
        Text(hint, color = Color.White, fontSize = 24.sp, modifier = Modifier.align(Alignment.TopCenter).padding(24.dp).background(Color(0x99000000)).padding(12.dp))
        CancelButton(onCancel)
    }
}

@Composable
private fun BoxScope.CancelButton(onCancel: () -> Unit) =
    Button(onClick = onCancel, modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp)) { Text("다음에 할게요", fontSize = 20.sp) }

/** Counts non-whitespace characters of recognized elements (inside [quad]'s bounding box when given) and unions their boxes. */
private fun textStats(text: com.google.mlkit.vision.text.Text, quad: FloatArray?): TextStats {
    val xs = quad?.let { listOf(it[0], it[2], it[4], it[6]) }
    val ys = quad?.let { listOf(it[1], it[3], it[5], it[7]) }
    fun inside(r: android.graphics.Rect) = xs == null || ys == null ||
        (r.centerX() >= xs.min() && r.centerX() <= xs.max() && r.centerY() >= ys.min() && r.centerY() <= ys.max())
    var chars = 0
    var box: android.graphics.Rect? = null
    for (e in text.textBlocks.flatMap { it.lines }.flatMap { it.elements }) {
        val bb = e.boundingBox ?: continue
        if (!inside(bb)) continue
        chars += e.text.count { !it.isWhitespace() }
        box = box?.apply { union(bb) } ?: android.graphics.Rect(bb)
    }
    return TextStats(chars, box)
}

/** Text bounds grown by 6% per side (page margins), clipped to the frame, as a TL,TR,BR,BL quad. */
private fun expandedRectQuad(r: android.graphics.Rect, w: Int, h: Int): FloatArray {
    val dx = r.width() * 0.06f; val dy = r.height() * 0.06f
    val l = maxOf(0f, r.left - dx); val t = maxOf(0f, r.top - dy)
    val rr = minOf(w.toFloat(), r.right + dx); val b = minOf(h.toFloat(), r.bottom + dy)
    return floatArrayOf(l, t, rr, t, rr, b, l, b)
}

actual fun logProof(msg: String) { Log.w(TAG, msg) }
