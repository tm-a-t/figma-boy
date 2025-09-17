package dev.vanutp.hack25

import org.bytedeco.javacv.CanvasFrame
import org.bytedeco.javacv.OpenCVFrameConverter
import org.bytedeco.opencv.global.opencv_core.*
import org.bytedeco.opencv.global.opencv_features2d.drawMatches
import org.bytedeco.opencv.global.opencv_imgcodecs.*
import org.bytedeco.opencv.global.opencv_imgproc.*
import org.bytedeco.opencv.opencv_core.*
import org.bytedeco.opencv.opencv_features2d.FlannBasedMatcher
import org.bytedeco.opencv.opencv_features2d.SIFT
import org.bytedeco.opencv.opencv_flann.KDTreeIndexParams
import org.bytedeco.opencv.opencv_flann.SearchParams
import javax.swing.WindowConstants

// https://docs.opencv.org/5.x/dc/dc3/tutorial_py_matcher.html

data class FlannResult(
    val kp1: KeyPointVector,
    val kp2: KeyPointVector,
    val mismatches: List<KMatch>,
    val cvMismatches: DMatchVector
)

fun flannDiff(img1: Mat, img2: Mat): FlannResult {
    val sift = SIFT.create()

    val kp1 = KeyPointVector()
    val des1 = Mat()
    sift.detectAndCompute(img1, Mat(), kp1, des1)

    val kp2 = KeyPointVector()
    val des2 = Mat()
    sift.detectAndCompute(img2, Mat(), kp2, des2)

    val indexParams = KDTreeIndexParams(5)
    val searchParams = SearchParams(50)
    val flann = FlannBasedMatcher(indexParams, searchParams)

    val matchesKnn = DMatchVectorVector()
    flann.knnMatch(des1, des2, matchesKnn, 2)

    // ratio test as per Lowe's paper
    val goodMatches = mutableListOf<DMatch>()
    for (i in 0 until matchesKnn.size().toInt()) {
        val pair = matchesKnn.get(i.toLong())
        val m = pair.get(0L)
        val n = pair.get(1L)
        if (m.distance() < 0.7f * n.distance()) {
            goodMatches.add(m)
        }
    }

    val mismatches = mutableListOf<KMatch>()
    val cvMismatches = DMatchVector()
    for (match in goodMatches) {
        val kp1 = kp1[match.queryIdx().toLong()].pt().let {
            KPoint2f(it.x(), it.y())
        }
        val kp2 = kp2[match.trainIdx().toLong()].pt().let {
            KPoint2f(it.x(), it.y())
        }
        if (kp1.x - kp2.x > 1 || kp1.y - kp2.y > 1) {
            mismatches.add(kp1 to kp2)
            cvMismatches.push_back(match)
        }
    }
    mismatches.sortWith(compareBy({ it.first.y }, { it.first.x }))

    return FlannResult(kp1, kp2, mismatches, cvMismatches)
}

fun showFlannDiffResult(img1: Mat, img2: Mat, result: FlannResult) {
    val out = Mat()
    drawMatches(img1, result.kp1, img2, result.kp2, result.cvMismatches, out)

    val outputPath = "sift_flann_matches.png"
    imwrite(outputPath, out)
    println("Wrote result to $outputPath")

    val converter = OpenCVFrameConverter.ToMat()
    val frame = CanvasFrame("SIFT + FLANN Matches", 1.0)
    frame.defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
    frame.showImage(converter.convert(out))
    while (frame.isVisible) {
        Thread.sleep(50)
    }
}
