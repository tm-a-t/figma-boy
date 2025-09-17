package me.tmat.figmaboy

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.jcef.JBCefApp

/**
 * Предзагружаемый сервис уровня Application.
 * На старте IDE добавляет Chromium/JCEF флаги ещё до инициализации JCEF.
 *
 * ВАЖНО: сервис зарегистрирован в plugin.xml с preload="true".
 * Это даёт максимально ранний момент выполнения среди поддерживаемых API.
 */
class JcefPerformanceService {

    private val log = Logger.getInstance(JcefPerformanceService::class.java)

    init {
        try {
            // Не триггерим явную инициализацию JCEF — просто добавляем флаги в командную строку.
            // Если JCEF уже инициализировали другие плагины — флаги будут проигнорированы (не страшно).
            val cacheDir = PathManager.getSystemPath() + "/jcef-cache"

            val switches = listOf(
                "--enable-gpu",
                "--ignore-gpu-blocklist",
                "--enable-zero-copy",
                "--enable-gpu-rasterization",
                "--enable-oop-rasterization",
                "--enable-native-gpu-memory-buffers",
                "--disable-background-timer-throttling",
                "--disable-renderer-backgrounding",
                "--enable-features=VaapiVideoDecoder,CanvasOopRasterization,WebRTC-H264WithOpenH264FFmpeg",
                "--disk-cache-dir=$cacheDir"
            )

            addChromiumSwitchesCompat(switches)
            log.info("JCEF performance switches applied (${switches.size}).")
        } catch (t: Throwable) {
            // Никаких падений IDE из-за оптимизаций — тихо логируем и продолжаем жить.
            log.warn("Failed to apply JCEF performance switches", t)
        }
    }
}

/**
 * Рефлексивная обёртка под разные версии платформы:
 * - addCommandLineSwitches(String...)
 * - addCommandLineSwitch(String)
 * - addCommandLineSwitch(String name, String value)
 */
private fun addChromiumSwitchesCompat(flags: List<String>) {
    val cls = JBCefApp::class.java

    // 1) bulk-метод
    try {
        val m = cls.getMethod("addCommandLineSwitches", Array<String>::class.java)
        m.invoke(null, flags.toTypedArray())
        return
    } catch (_: Throwable) { /* fall through */ }

    // 2) по одному флагу
    var singleArg: java.lang.reflect.Method? = null
    try { singleArg = cls.getMethod("addCommandLineSwitch", String::class.java) } catch (_: Throwable) {}

    var twoArg: java.lang.reflect.Method? = null
    try { twoArg = cls.getMethod("addCommandLineSwitch", String::class.java, String::class.java) } catch (_: Throwable) {}

    for (flag in flags) {
        val trimmed = flag.trim().removePrefix("--").removePrefix("-")
        val eq = trimmed.indexOf('=')
        if (eq > 0 && twoArg != null) {
            val name = trimmed.substring(0, eq)
            val value = trimmed.substring(eq + 1)
            try { twoArg.invoke(null, name, value) } catch (_: Throwable) {}
        } else if (singleArg != null) {
            try { singleArg.invoke(null, flag) } catch (_: Throwable) {}
        } else if (twoArg != null) {
            try { twoArg.invoke(null, trimmed, "") } catch (_: Throwable) {}
        }
    }
}