package dev.vanutp.hack25

import kotlin.test.Test
import kotlin.test.assertTrue
import org.bytedeco.opencv.global.opencv_imgcodecs.imread
import org.bytedeco.opencv.global.opencv_imgcodecs.imencode
import org.bytedeco.opencv.global.opencv_imgcodecs.IMREAD_GRAYSCALE
import org.bytedeco.javacpp.BytePointer

class ImencodeTest {
    @Test
    fun encodesNonEmptyPng() {
        val img = imread("tests/reference.png", IMREAD_GRAYSCALE)
        assertTrue(!img.empty(), "reference image failed to load")
        val buf = BytePointer()
        val ok = imencode(".png", img, buf)
        assertTrue(ok, "imencode returned false")
        val length = buf.limit().toInt()
        assertTrue(length > 0, "Encoded buffer is empty")
    }
}

