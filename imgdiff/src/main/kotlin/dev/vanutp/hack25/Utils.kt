package dev.vanutp.hack25

import org.bytedeco.opencv.global.opencv_core.CV_8U
import org.bytedeco.opencv.global.opencv_imgcodecs.imdecode
import org.bytedeco.opencv.opencv_core.Mat
import org.openqa.selenium.Rectangle

data class KPoint2d(
    val x: Int,
    val y: Int,
)

data class KPoint2f(
    val x: Float,
    val y: Float,
)
typealias KMatch = Pair<KPoint2f, KPoint2f>

data class KRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

fun Rectangle.toKRect() = KRect(x, y, width, height)

fun imload(bytes: ByteArray, flags: Int): Mat {
    val buf = Mat(1, bytes.size, CV_8U)
    buf.data().put(bytes, 0, bytes.size)
    return imdecode(buf, flags)
}
