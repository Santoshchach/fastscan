package com.fastscan.app

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.util.ArrayList
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

object ScanProcessor {

    /**
     * Main entry point for the "Magic Scan" effect.
     * Replicates the Open Note Scanner processing pipeline.
     */
    fun processDocument(bitmap: Bitmap): Bitmap {
        val src = Mat()
        Utils.bitmapToMat(bitmap, src)

        // 1. Perspective Correction (Auto Crop) - If no document detected, it returns original
        val cropped = autoCrop(src)

        // 2. Grayscale Conversion
        val gray = Mat()
        Imgproc.cvtColor(cropped, gray, Imgproc.COLOR_BGR2GRAY)

        // 3. Noise Reduction (Bilateral Filter - preserves edges like ONS)
        val denoised = Mat()
        Imgproc.bilateralFilter(gray, denoised, 9, 75.0, 75.0)

        // 4. Contrast Enhancement (CLAHE - for uniform lighting)
        val enhanced = Mat()
        val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
        clahe.apply(denoised, enhanced)

        // 5. Sharpening (Unsharp Mask style to keep text sharp)
        val sharpened = Mat()
        val kernel = Mat(3, 3, CvType.CV_32F)
        kernel.put(0, 0, 0.0, -1.0, 0.0, -1.0, 5.0, -1.0, 0.0, -1.0, 0.0)
        Imgproc.filter2D(enhanced, sharpened, -1, kernel)

        // 6. Adaptive Thresholding (The ONS "Magic" filter core)
        val finalScan = Mat()
        Imgproc.adaptiveThreshold(
            sharpened,
            finalScan,
            255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY,
            21, // Block size (ONS uses 11-21)
            10.0 // Constant C (ONS uses 2-10)
        )

        // Convert back to Bitmap
        val resultBitmap = Bitmap.createBitmap(finalScan.cols(), finalScan.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(finalScan, resultBitmap)

        // Cleanup
        src.release()
        cropped.release()
        gray.release()
        denoised.release()
        enhanced.release()
        sharpened.release()
        finalScan.release()
        kernel.release()

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
            if (area > (src.rows() * src.cols() * 0.1)) { // Only consider contours > 10% of image
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

        val result = if (maxContour != null) {
            val warped = warpPerspective(src, maxContour)
            maxContour.release()
            warped
        } else {
            src.clone()
        }

        gray.release()
        blurred.release()
        edges.release()
        hierarchy.release()
        contours.forEach { it.release() }

        return result
    }

    private fun warpPerspective(src: Mat, points: MatOfPoint2f): Mat {
        val ptsArray = points.toArray()
        val sortedPoints = sortPoints(ptsArray)
        
        val widthA = sqrt((sortedPoints[2].x - sortedPoints[3].x).pow(2.0) + (sortedPoints[2].y - sortedPoints[3].y).pow(2.0))
        val widthB = sqrt((sortedPoints[1].x - sortedPoints[0].x).pow(2.0) + (sortedPoints[1].y - sortedPoints[0].y).pow(2.0))
        val maxWidth = max(widthA.toInt(), widthB.toInt())

        val heightA = sqrt((sortedPoints[1].x - sortedPoints[2].x).pow(2.0) + (sortedPoints[1].y - sortedPoints[2].y).pow(2.0))
        val heightB = sqrt((sortedPoints[0].x - sortedPoints[3].x).pow(2.0) + (sortedPoints[0].y - sortedPoints[3].y).pow(2.0))
        val maxHeight = max(heightA.toInt(), heightB.toInt())

        val dst = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(maxWidth.toDouble() - 1, 0.0),
            Point(maxWidth.toDouble() - 1, maxHeight.toDouble() - 1),
            Point(0.0, maxHeight.toDouble() - 1)
        )

        val srcPointsMat = MatOfPoint2f(*sortedPoints)
        val transform = Imgproc.getPerspectiveTransform(srcPointsMat, dst)
        val warped = Mat()
        Imgproc.warpPerspective(src, warped, transform, Size(maxWidth.toDouble(), maxHeight.toDouble()))
        
        transform.release()
        dst.release()
        srcPointsMat.release()
        return warped
    }

    private fun sortPoints(pts: Array<Point>): Array<Point> {
        val sorted = Array(4) { Point() }
        
        // 0: top-left, 1: top-right, 2: bottom-right, 3: bottom-left
        val sum = pts.map { it.x + it.y }
        sorted[0] = pts[sum.indexOf(sum.minOrNull()!!)]
        sorted[2] = pts[sum.indexOf(sum.maxOrNull()!!)]

        val diff = pts.map { it.x - it.y }
        sorted[1] = pts[diff.indexOf(diff.maxOrNull()!!)]
        sorted[3] = pts[diff.indexOf(diff.minOrNull()!!)]
        
        return sorted
    }
}
