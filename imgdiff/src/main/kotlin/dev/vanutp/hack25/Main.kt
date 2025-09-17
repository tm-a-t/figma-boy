package dev.vanutp.hack25

import org.bytedeco.opencv.global.opencv_imgcodecs.IMREAD_GRAYSCALE
import org.bytedeco.opencv.global.opencv_imgcodecs.imread
import org.openqa.selenium.Dimension
import org.openqa.selenium.OutputType
import org.openqa.selenium.WebElement
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.remote.RemoteWebDriver
import kotlin.io.path.Path

fun isIgnored(ignoreElements: Map<String, String>, element: WebElement): Boolean {
    for ((key, ignVal) in ignoreElements) {
        val attrVal = element.getAttribute(key) ?: continue
        if (key == "class" && attrVal.split(' ').contains(ignVal)) {
            return true
        }
        if (attrVal == ignVal) {
            return true
        }
    }
    return false
}

fun elementToString(driver: RemoteWebDriver, element: WebElement): String {
    val tag = element.tagName
    val attrs = driver.executeScript(
        """
            let attrs = {};
            for (const attr of arguments[0].attributes) {
                attrs[attr.name] = attr.value;
            }
            return attrs;
        """.trimIndent(),
        element
    ) as Map<*, *>
    val attrStr = attrs.entries.joinToString(" ") { "${it.key}=\"${it.value}\"" }
    return "<$tag $attrStr>"
}

fun resizeWindow(driver: RemoteWebDriver, width: Int, height: Int) {
    val isWayland = System.getenv("XDG_SESSION_TYPE") == "wayland"
    val scale = if (isWayland) {
        1.25f // костыль
    } else {
        1f
    }
    driver.manage().window().size = Dimension(
        ((width + 40) / scale).toInt(),
        ((height + 300) / scale).toInt(),
    )
}

fun compare(driver: RemoteWebDriver, referencePath: String, url: String, ignoreElements: Map<String, String>): List<Pair<String, KRect>> {
    val referenceImg = imread(referencePath, IMREAD_GRAYSCALE)
    resizeWindow(driver, referenceImg.cols(), referenceImg.rows())

    driver.get(url)
    Thread.sleep(2000)
    val screenshotBytes = driver.getScreenshotAs(OutputType.BYTES)
    Path("tests/screenshot.png").toFile().writeBytes(screenshotBytes)

    val screenshotImg = imload(screenshotBytes, IMREAD_GRAYSCALE)

    if (screenshotImg.cols() != referenceImg.cols()) {
        throw IllegalStateException("Width mismatch: reference ${referenceImg.cols()} vs screenshot ${screenshotImg.cols()}")
    }

    val diffResult = pixelDiff(referenceImg, screenshotImg)
    showPixelDiffResult(diffResult)
    val ignoreAreas = mutableListOf<KRect>()
    val diffElements = mutableListOf<Pair<String, KRect>>()
    diffResult.diffPixels.forEach { p ->


        if (
            (ignoreAreas + diffElements.map { it.second })
                .any { it.x <= p.x && p.x <= it.x + it.width && it.y <= p.y && p.y <= it.y + it.height }
        ) {
            return@forEach
        }

        val elementsRaw = driver.executeScript(
            """
                    let el = document.elementFromPoint(arguments[0], arguments[1]);
                    const res = [];
                    while (el) {
                        res.push(el);
                        el = el.parentElement;
                    }
                    return res;
                """.trimIndent(),
            p.x,
            p.y
        ) as List<*>
        val elements = elementsRaw.map { it as WebElement }
        if (elements.isEmpty()) {
            println("No element found at (${p.x}, ${p.y})")
            return@forEach
        }

        val ignoredElement = elements.firstOrNull { isIgnored(ignoreElements, it) }
        if (ignoredElement != null) {
            val r = ignoredElement.rect
            ignoreAreas.add(r.toKRect())
            println("Ignoring element ${ignoredElement.tagName} at ${r.toKRect()}")
            return@forEach
        }

        val element = elementToString(driver, elements.first())
        val rect = elements.first().rect.toKRect()
        diffElements.add(element to rect)
    }

    return diffElements
}

fun main(args: Array<String>) {
    val imgPath1 = "tests/reference.png"
    val chromeBinaryPath = System.getenv("CHROME_BINARY")
    val chromeOptions = ChromeOptions()
//    chromeOptions.addArguments("--headless")
    if (chromeBinaryPath != null) {
        chromeOptions.setBinary(chromeBinaryPath)
    }
    val driver = ChromeDriver(chromeOptions)
    try {
        println(compare(driver, imgPath1, "https://my.progtime.net", mapOf("class" to "IaFooter-module-scss-module__U0iMhG__version")))
    } finally {
        driver.quit()
    }

//    val imgPath2 = "tests/margin.png"
//    val img1 = imread(imgPath1)
//    val img2 = imread(imgPath2)
//
//    val diffResult = pixelDiff(img1, img2)
//    showPixelDiffResult(diffResult)

//    val flannResult = flannDiff(img1, img2)
//    flannResult.mismatches.first().run {
//        println("First unmatched keypoint: (${first.first}, ${first.second}) -> (${second.first}, ${second.second})")
//    }
//    showFlannDiffResult(img1, img2, flannResult)
}
