package com.fastscan.app

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.util.ArrayList

object ScanProcessor {

    fun processDocument(bitmap: Bitmap): Bitmap {
        val src = Mat()
        Utils.bitmapToMat(bitmap, src)

        // 1. Perspective Correction (Auto Crop)
        val cropped = autoCrop(src)

        // 2. Grayscale
        val gray = Mat()
        Imgproc.cvtColor(cropped, gray, Imgproc.COLOR_BGR2GRAY)

        // 3. Remove Noise (Bilateral Filter preserves edges)
        val denoised = Mat()
        Imgproc.bilateralFilter(gray, denoised, 9, 75.0, 75.0)

        // 4. Gaussian Blur
        val blurred = Mat()
        Imgproc.GaussianBlur(denoised, blurred, Size(3.0, 3.0), 0.0)

        // 5. Adaptive Threshold (Magic Scan Effect)
        val thresholded = Mat()
        Imgproc.adaptiveThreshold(
            blurred,
            thresholded,
            255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY,
            15,
            12.0
        )

        // Convert back to Bitmap
        val resultBitmap = Bitmap.createBitmap(thresholded.cols(), thresholded.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(thresholded, resultBitmap)

        // Cleanup
        src.release()
        cropped.release()
        gray.release()
        denoised.release()
        blurred.release()
        thresholded.release()

        return resultBitmap
    }

    private fun autoCrop(src: Mat): Mat {
        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)
        
        val blurred = Mat()
        Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
        
        val edges = Mat()
        Imgproc.Canny(blurred, edges, 75.0, 200.0)

        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        var maxArea = -1.0
        var maxContour: MatOfPoint2f? = null

        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area > 1000) {
                val contour2f = MatOfPoint2f(*contour.toArray())
                val peri = Imgproc.arcLength(contour2f, true)
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(contour2f, approx, 0.02 * peri, true)

                if (approx.total() == 4L && area > maxArea) {
                    maxArea = area
                    maxContour = approx
                }
            }
        }

        return if (maxContour != null) {
            warpPerspective(src, maxContour)
        } else {
            src.clone()
        }
    }

    private fun warpPerspective(src: Mat, points: MatOfPoint2f): Mat {
        val sortedPoints = sortPoints(points.toArray())
        val widthA = Math.sqrt(Math.pow(sortedPoints[2].x - sortedPoints[3].x, 2.0) + Math.pow(sortedPoints[2].y - sortedPoints[3].y, 2.0))
        val widthB = Math.sqrt(Math.pow(sortedPoints[1].x - sortedPoints[0].x, 2.0) + Math.pow(sortedPoints[1].y - sortedPoints[0].y, 2.0))
        val maxWidth = Math.max(widthA.toInt(), widthB.toInt())

        val heightA = Math.sqrt(Math.pow(sortedPoints[1].x - sortedPoints[2].x, 2.0) + Math.pow(sortedPoints[1].y - sortedPoints[2].y, 2.0))
        val heightB = Math.sqrt(Math.pow(sortedPoints[0].x - sortedPoints[3].x, 2.0) + Math.pow(sortedPoints[0].y - sortedPoints[3].y, 2.0))
        val maxHeight = Math.max(heightA.toInt(), heightB.toInt())

        val dst = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(maxWidth.toDouble() - 1, 0.0),
            Point(maxWidth.toDouble() - 1, maxHeight.toDouble() - 1),
            Point(0.0, maxHeight.toDouble() - 1)
        )

        val transform = Imgproc.getPerspectiveTransform(MatOfPoint2f(*sortedPoints), dst)
        val warped = Mat()
        Imgproc.warpPerspective(src, warped, transform, Size(maxWidth.toDouble(), maxHeight.toDouble()))
        
        return warped
    }

    private fun sortPoints(pts: Array<Point>): Array<Point> {
        val sorted = Array(4) { Point() }
        val sum = pts.map { it.x + it.y }
        sorted[0] = pts[sum.indexOf(sum.minOrNull()!!)]
        sorted[2] = pts[sum.indexOf(sum.maxOrNull()!!)]

        val diff = pts.map { it.x - it.y }
        sorted[1] = pts[diff.indexOf(diff.maxOrNull()!!)]
        sorted[3] = pts[diff.indexOf(diff.minOrNull()!!)]
        
        return sorted
    }
}
