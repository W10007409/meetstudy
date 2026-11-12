package com.wjthinkbig.meetstudy.platform

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter

private const val MODEL = "mobile_face_net.tflite"
internal const val FACE_TAG = "MeetStudyFace"

/**
 * MobileFaceNet (Qualcomm AI Hub export, see assets/MODELS.md). Inputs are RGB in [0,1]
 * (the model normalizes and flip-averages internally). It has two image inputs (img1, img2)
 * and outputs one embedding row per input, so we feed the same crop to both and take row 0.
 */
class FaceEmbedder(context: Context) : FaceIdentity, Closeable {
    private var interpreter: Interpreter? = null
    private var channelsFirst = false
    private var side = 0
    private var dim = 0

    init {
        try {
            val bytes = context.assets.open(MODEL).use { it.readBytes() }
            val buf = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes)
            buf.rewind()
            val i = Interpreter(buf, Interpreter.Options().setNumThreads(2))
            val inShapes = (0 until i.inputTensorCount).map { i.getInputTensor(it).shape() }
            val outShape = i.getOutputTensor(0).shape()
            Log.i(FACE_TAG, "model loaded inputs=" + inShapes.joinToString { it.contentToString() } + " output=" + outShape.contentToString())
            val s = inShapes[0]
            if (s.size != 4 || !inShapes.all { it.contentEquals(s) } || outShape.size != 2) {
                i.close()
                error("unexpected shapes")
            }
            channelsFirst = s[1] == 3
            side = if (channelsFirst) s[2] else s[1]
            dim = outShape[1]
            interpreter = i
        } catch (e: Exception) {
            Log.w(FACE_TAG, "model load failed: " + e.javaClass.simpleName)
        }
    }

    override val available: Boolean get() = interpreter != null

    override suspend fun embed(face: FaceCrop): FloatArray = withContext(Dispatchers.Default) { embedBlocking(face) }

    /** Interpreter is not thread-safe; the lock also keeps close() from racing a run. */
    @Synchronized
    fun embedBlocking(face: FaceCrop): FloatArray {
        val i = checkNotNull(interpreter) { "model unavailable" }
        require(face.size == side) { "crop size" }
        val inputs = Array<Any>(i.inputTensorCount) { toInput(face) }
        val out = Array(i.getOutputTensor(0).shape()[0]) { FloatArray(dim) }
        i.runForMultipleInputsOutputs(inputs, mapOf(0 to out))
        return out[0]
    }

    private fun toInput(face: FaceCrop): ByteBuffer {
        val n = side * side
        val buf = ByteBuffer.allocateDirect(n * 3 * 4).order(ByteOrder.nativeOrder())
        val fb = buf.asFloatBuffer()
        for (p in 0 until n) {
            val c = face.argb[p]
            val r = (c shr 16 and 0xFF) / 255f
            val g = (c shr 8 and 0xFF) / 255f
            val b = (c and 0xFF) / 255f
            if (channelsFirst) {
                fb.put(p, r); fb.put(n + p, g); fb.put(2 * n + p, b)
            } else {
                fb.put(3 * p, r); fb.put(3 * p + 1, g); fb.put(3 * p + 2, b)
            }
        }
        return buf
    }

    @Synchronized
    override fun close() {
        interpreter?.close()
        interpreter = null
    }
}

@Composable
actual fun rememberFaceIdentity(): FaceIdentity {
    val context = LocalContext.current
    val embedder = remember { FaceEmbedder(context.applicationContext) }
    DisposableEffect(embedder) { onDispose { embedder.close() } }
    return embedder
}
