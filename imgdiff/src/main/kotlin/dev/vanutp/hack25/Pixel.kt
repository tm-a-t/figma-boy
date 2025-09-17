package dev.vanutp.hack25

import org.bytedeco.opencv.global.opencv_core.*
import org.bytedeco.opencv.global.opencv_imgproc.*
import org.bytedeco.opencv.opencv_core.*

data class PixelDiffResult(
    val diffPixels: List<KPoint2d>,
    val finalMask: Mat,
    val canvas1: Mat,
    val canvas2: Mat
)

fun pixelDiff(img1: Mat, img2: Mat): PixelDiffResult {
    val w1 = img1.cols()
    val h1 = img1.rows()
    val w2 = img2.cols()
    val h2 = img2.rows()

    val maxW = maxOf(w1, w2)
    val maxH = maxOf(h1, h2)

    // 1) Place both images on full-size canvases (padding with zeros)
    val c1 = Mat(maxH, maxW, img1.type(), Scalar(0.0))
    val c2 = Mat(maxH, maxW, img2.type(), Scalar(0.0))
    img1.copyTo(c1.apply(Rect(0, 0, w1, h1)))
    img2.copyTo(c2.apply(Rect(0, 0, w2, h2)))

    // 2) Build presence masks (255 where image has data, 0 elsewhere)
    val m1 = Mat(maxH, maxW, img1.type(), Scalar(0.0))
    val m2 = Mat(maxH, maxW, img2.type(), Scalar(0.0))
    rectangle(m1, Rect(0, 0, w1, h1), Scalar(255.0), -1, 8, 0)
    rectangle(m2, Rect(0, 0, w2, h2), Scalar(255.0), -1, 8, 0)

    // 3) Compute absolute difference over full canvases, then binarize
    val diff = Mat()
    absdiff(c1, c2, diff)
    val bin = Mat()
    threshold(diff, bin, 0.0, 255.0, THRESH_BINARY)

    // 4) Regions present in only one image (non-overlapping) via XOR of presence masks
    val xorMask = Mat()
    bitwise_xor(m1, m2, xorMask)

    // 5) Final mask: any non-zero pixel in bin OR xorMask is a difference
    val discreteMask = Mat()
    bitwise_or(bin, xorMask, discreteMask)

    // 5.1) Keep a pixel as different only if at least 6 of its 8 neighbors are also different
    // Compute 3x3 sum (center + 8 neighbors) over the binary mask, then require sum >= 7*255
    val kernel = Mat(3, 3, CV_32F, Scalar(1.0))
    val sum3x3 = Mat()
    filter2D(discreteMask, sum3x3, CV_32F, kernel)
    val neighbor7 = Mat()
    threshold(sum3x3, neighbor7, 7.0 * 255.0 - 1e-3, 255.0, THRESH_BINARY)
    val neighbor7u8 = Mat()
    neighbor7.convertTo(neighbor7u8, CV_8U)
    val filteredMask = Mat()
    bitwise_and(discreteMask, neighbor7u8, filteredMask)

    // 6) Extract coordinates of non-zero pixels using OpenCV (no manual pixel loops)
    val binaryMask = Mat()
    findNonZero(filteredMask, binaryMask)

    val out = mutableListOf<KPoint2d>()
    for (i in 0 until binaryMask.rows()) {
        val p = Point(binaryMask.ptr(i))
        out.add(KPoint2d(p.x(), p.y()))
    }

    return PixelDiffResult(out, filteredMask, c1, c2)
}

fun renderPixelDiffResult(result: PixelDiffResult): Pair<Mat, Mat> {
    // Convert canvases to color for highlighting
    val c1c = Mat()
    val c2c = Mat()
    cvtColor(result.canvas1, c1c, COLOR_GRAY2BGR)
    cvtColor(result.canvas2, c2c, COLOR_GRAY2BGR)

    // Create red overlay where differences are present using mask directly
    val redFull1 = Mat(c1c.size(), c1c.type(), Scalar(0.0, 0.0, 255.0, 0.0))
    val redFull2 = Mat(c2c.size(), c2c.type(), Scalar(0.0, 0.0, 255.0, 0.0))
    val red1 = Mat(c1c.size(), c1c.type(), Scalar(0.0, 0.0, 0.0, 0.0))
    val red2 = Mat(c2c.size(), c2c.type(), Scalar(0.0, 0.0, 0.0, 0.0))
    redFull1.copyTo(red1, result.finalMask)
    redFull2.copyTo(red2, result.finalMask)

    val out1 = Mat()
    val out2 = Mat()
    addWeighted(c1c, 1.0, red1, 0.5, 0.0, out1)
    addWeighted(c2c, 1.0, red2, 0.5, 0.0, out2)

    return Pair(out1, out2)

    // Combine side-by-side
//    val combined = Mat()
//    val mv = MatVector(2L)
//    mv.put(0L, out1)
//    mv.put(1L, out2)
//    hconcat(mv, combined)

//    val outputPath = "pixel_diff.png"
//    imwrite(outputPath, combined)
//    println("Wrote result to $outputPath")

//    val converter = OpenCVFrameConverter.ToMat()
//    val frame = CanvasFrame("Pixel Diff", 1.0)
//    frame.defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
//    frame.showImage(converter.convert(combined))
//    while (frame.isVisible) {
//        Thread.sleep(50)
//    }
}
