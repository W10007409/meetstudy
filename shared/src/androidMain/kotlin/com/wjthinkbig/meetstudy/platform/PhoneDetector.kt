package com.wjthinkbig.meetstudy.platform

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter

private const val MODEL = "efficientdet_lite0.tflite"
private const val TAG = "MeetStudyFrames"
private const val CELL_PHONE = 76 // 0-based line of "cell phone" in the model's embedded labels.txt (see assets/MODELS.md)
private const val MIN_SCORE = 0.5f

/**
 * EfficientDet-Lite0 int8 (MediaPipe) on LiteRT. The model is the raw head: no NMS, outputs are
 * [1,19206,90] class scores (already sigmoid'd, range 0..1, verified on device) and [1,19206,4] box deltas.
 * We only need "is there a phone", so we skip boxes and NMS and look for any anchor scoring >= 0.5.
 * The lock also keeps close() from racing a run.
 */
class PhoneDetector(context: Context) : Closeable {
    private var interpreter: Interpreter? = null
    private var side = 0
    private var floatInput = false
    private var anchors = 0
    private var classes = 0
    private lateinit var out: ByteBuffer
    private var scoreOut = 0
    private val budget = InferenceBudget()

    val avgInferenceMs: Long @Synchronized get() = budget.avgMs
    val disabled: Boolean get() = interpreter == null

    init {
        try {
            val bytes = context.assets.open(MODEL).use { it.readBytes() }
            val buf = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes)
            buf.rewind()
            val i = Interpreter(buf, Interpreter.Options().setNumThreads(4))
            val inT = i.getInputTensor(0)
            val outs = (0 until i.outputTensorCount).map { i.getOutputTensor(it) }
            Log.i(TAG, "phone model loaded input=" + inT.shape().contentToString() + " " + inT.dataType() +
                " outputs=" + outs.joinToString { it.shape().contentToString() + " " + it.dataType() })
            val s = inT.shape()
            val si = outs.indexOfFirst { it.shape().size == 3 && it.shape()[2] > CELL_PHONE }
            if (s.size != 4 || s[3] != 3 || si < 0) {
                i.close()
                error("unexpected shapes")
            }
            side = s[1]
            floatInput = inT.dataType() == DataType.FLOAT32
            scoreOut = si
            anchors = outs[si].shape()[1]
            classes = outs[si].shape()[2]
            out = ByteBuffer.allocateDirect(anchors * classes * 4).order(ByteOrder.nativeOrder())
            interpreter = i
        } catch (e: Exception) {
            Log.w(TAG, "phone model load failed: " + e.javaClass.simpleName)
        }
    }

    /** True if any "cell phone" detection scores >= 0.5. Disables itself permanently when over the time budget. */
    @Synchronized
    fun detect(bitmap: Bitmap): Boolean {
        val i = interpreter ?: return false
        val start = SystemClock.elapsedRealtime()
        val px = IntArray(side * side)
        Bitmap.createScaledBitmap(bitmap, side, side, true).getPixels(px, 0, side, 0, 0, side, side)
        val input = ByteBuffer.allocateDirect(px.size * 3 * if (floatInput) 4 else 1).order(ByteOrder.nativeOrder())
        for (c in px) {
            val r = c shr 16 and 0xFF; val g = c shr 8 and 0xFF; val b = c and 0xFF
            if (floatInput) input.putFloat(r.toFloat()).putFloat(g.toFloat()).putFloat(b.toFloat())
            else input.put(r.toByte()).put(g.toByte()).put(b.toByte())
        }
        input.rewind()
        out.rewind()
        i.runForMultipleInputsOutputs(arrayOf<Any>(input), mapOf(scoreOut to out))
        budget.record(SystemClock.elapsedRealtime() - start)
        if (budget.overBudget) {
            Log.w(TAG, "phone detection disabled avg=${budget.avgMs}")
            close()
            return false
        }
        out.rewind()
        val f = out.asFloatBuffer()
        return (0 until anchors).any { f.get(it * classes + CELL_PHONE) >= MIN_SCORE }
    }

    @Synchronized
    override fun close() {
        interpreter?.close()
        interpreter = null
    }
}
