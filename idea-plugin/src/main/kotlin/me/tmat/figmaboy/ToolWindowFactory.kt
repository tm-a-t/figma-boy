package me.tmat.figmaboy

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.project.DumbAware
import com.intellij.ui.content.ContentFactory
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.openapi.actionSystem.*
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JPanel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField

class MyToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = SimpleToolWindowPanel(true, true)

        if (!JBCefApp.isSupported()) {
            // Fallback if JCEF isn’t available on this machine
            panel.setContent(JPanel(BorderLayout()).apply {
                add(JBLabel("JCEF is not supported on this system."), BorderLayout.CENTER)
                border = JBUI.Borders.empty(12)
            })
            addContent(toolWindow, panel)
            return
        }

        // Try to apply performance switches before JCEF initializes (best-effort)
        try {
            val cacheDir = com.intellij.openapi.application.PathManager.getSystemPath() + "/jcef-cache"
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
            addChromiumSwitchesCompatLocal(switches)
        } catch (_: Throwable) {
            // ignore
        }

        // Create the embedded Chromium browser
        val browser = JBCefBrowser("https://www.figma.com")  // or your app/URL
        Disposer.register(toolWindow.disposable, browser)   // dispose with tool window

        // Optional: simple toolbar (Back / Forward / Reload)
        val toolbar = ActionManager.getInstance()
            .createActionToolbar("MyToolWindowToolbar", DefaultActionGroup(
                BackAction(browser), ForwardAction(browser), ReloadAction(browser)
            ), true).apply { targetComponent = panel }

        // Build top control strip with toolbar + our custom controls
        val topPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 3))
        topPanel.add(toolbar.component)

        val idField = JBTextField().apply {
            columns = 16
            isEditable = false
            toolTipText = "Value of node-id parameter (text after node-id= and before &)."
        }
        val btn = JButton("Select").apply {
            toolTipText = "Show the value after node-id= and before & from the current Figma URL"
            addActionListener {
                val url = browser.cefBrowser.url ?: ""
                val match = Regex("node-id=([^&]+)").find(url)
                val value = match?.groupValues?.getOrNull(1) ?: ""
                idField.text = value
                if (value.isNotEmpty()) {
                    val base = project.basePath ?: System.getProperty("user.dir")
                    val dir = java.nio.file.Paths.get(base, "project_settings")
                    java.nio.file.Files.createDirectories(dir)
                    val file = dir.resolve("nodeId.txt")
                    java.nio.file.Files.write(file, value.toByteArray(Charsets.UTF_8))
                }
            }
        }

        topPanel.add(btn)
        topPanel.add(JBLabel(":"))
        topPanel.add(idField)

        panel.toolbar = topPanel
        panel.setContent(browser.component)


        addContent(toolWindow, panel)
    }

    private fun addContent(toolWindow: ToolWindow, panel: SimpleToolWindowPanel) {
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

private fun addChromiumSwitchesCompatLocal(flags: List<String>) {
    val cls = JBCefApp::class.java
    try {
        val m = cls.getMethod("addCommandLineSwitches", Array<String>::class.java)
        m.invoke(null, flags.toTypedArray())
        return
    } catch (_: Throwable) {
    }
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
            continue
        }
        if (singleArg != null) {
            try { singleArg.invoke(null, flag) } catch (_: Throwable) {}
        } else if (twoArg != null) {
            try { twoArg.invoke(null, trimmed, "") } catch (_: Throwable) {}
        }
    }
}

/* ---- Small toolbar actions ---- */
private class BackAction(private val b: JBCefBrowser) :
    AnAction("Back", "Go back", com.intellij.icons.AllIcons.Actions.Back) {
    override fun actionPerformed(e: AnActionEvent) { b.cefBrowser.goBack() }
    override fun update(e: AnActionEvent) { e.presentation.isEnabled = b.cefBrowser.canGoBack() }
}

private class ForwardAction(private val b: JBCefBrowser) :
    AnAction("Forward", "Go forward", com.intellij.icons.AllIcons.Actions.Forward) {
    override fun actionPerformed(e: AnActionEvent) { b.cefBrowser.goForward() }
    override fun update(e: AnActionEvent) { e.presentation.isEnabled = b.cefBrowser.canGoForward() }
}

private class ReloadAction(private val b: JBCefBrowser) :
    AnAction("Reload", "Reload page", com.intellij.icons.AllIcons.Actions.Refresh) {
    override fun actionPerformed(e: AnActionEvent) { b.cefBrowser.reload() }
}