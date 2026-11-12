package com.wjthinkbig.meetstudy.platform

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.hypot

/** Finds the largest document-like quadrilateral: gray, blur, Canny, contours, 4-vertex polygon approximation. */
object QuadDetector {
    private const val WORK_SIDE = 640
    private val ready: Boolean by lazy { OpenCVLoader.initLocal() }

    /** 8 coords (x,y) in [bitmap] pixel space ordered TL,TR,BR,BL, or null. */
    fun find(bitmap: Bitmap): FloatArray? {
        if (!ready) return null
        val scale = minOf(1f, WORK_SIDE.toFloat() / maxOf(bitmap.width, bitmap.height))
        val mats = ArrayList<Mat>()
        fun <T : Mat> T.track(): T = also { mats += it }
        try {
            val rgba = Mat().track()
            Utils.bitmapToMat(bitmap, rgba)
            val small = if (scale < 1f) Mat().track().also { Imgproc.resize(rgba, it, Size(), scale.toDouble(), scale.toDouble(), Imgproc.INTER_AREA) } else rgba
            val gray = Mat().track()
            Imgproc.cvtColor(small, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)
            val edges = Mat().track()
            Imgproc.Canny(gray, edges, 50.0, 150.0)
            // Close small gaps so a page border forms one contour.
            Imgproc.dilate(edges, edges, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0)).track())
            val contours = ArrayList<MatOfPoint>()
            Imgproc.findContours(edges, contours, Mat().track(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
            var best: FloatArray? = null
            var bestArea = 0f
            for (c in contours) {
                val c2f = MatOfPoint2f().track()
                c.convertTo(c2f, org.opencv.core.CvType.CV_32F)
                c.release()
                val approx = MatOfPoint2f().track()
                Imgproc.approxPolyDP(c2f, approx, 0.02 * Imgproc.arcLength(c2f, true), true)
                if (approx.rows() != 4) continue
                val pts = approx.toArray()
                val q = orderCorners(FloatArray(8) { i -> (if (i % 2 == 0) pts[i / 2].x else pts[i / 2].y).toFloat() })
                if (!isDocumentQuad(q, small.cols(), small.rows())) continue
                val a = quadArea(q)
                if (a > bestArea) { bestArea = a; best = q }
            }
            return best?.let { q -> FloatArray(8) { q[it] / scale } }
        } finally {
            mats.forEach { it.release() }
        }
    }

    /** Perspective-corrects [quad] (TL,TR,BR,BL) to an upright rectangle, long side <= [maxSide]. Caller recycles [src] and the result. */
    fun warp(src: Bitmap, quad: FloatArray, maxSide: Int = 1600): Bitmap {
        fun d(a: Int, b: Int) = hypot(quad[a * 2] - quad[b * 2], quad[a * 2 + 1] - quad[b * 2 + 1])
        var w = maxOf(d(0, 1), d(3, 2)); var h = maxOf(d(0, 3), d(1, 2))
        val k = minOf(1f, maxSide / maxOf(w, h))
        w *= k; h *= k
        val mats = ArrayList<Mat>()
        fun <T : Mat> T.track(): T = also { mats += it }
        try {
            val srcMat = Mat().track().also { Utils.bitmapToMat(src, it) }
            val from = MatOfPoint2f(*Array(4) { Point(quad[it * 2].toDouble(), quad[it * 2 + 1].toDouble()) }).track()
            val to = MatOfPoint2f(Point(0.0, 0.0), Point(w.toDouble(), 0.0), Point(w.toDouble(), h.toDouble()), Point(0.0, h.toDouble())).track()
            val m = Imgproc.getPerspectiveTransform(from, to).track()
            val out = Mat().track()
            Imgproc.warpPerspective(srcMat, out, m, Size(w.toDouble(), h.toDouble()))
            return Bitmap.createBitmap(out.cols(), out.rows(), Bitmap.Config.ARGB_8888).also { Utils.matToBitmap(out, it) }
        } finally {
            mats.forEach { it.release() }
        }
    }
}
