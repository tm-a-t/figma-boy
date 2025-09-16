package me.tmat.figmaboy

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.project.DumbAware
import com.intellij.ui.content.ContentFactory
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.JBColor
import com.intellij.openapi.actionSystem.*
import com.intellij.util.ui.JBUI
import javax.swing.JPanel
import java.awt.BorderLayout

class MyToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = SimpleToolWindowPanel(true, true)

        if (!JBCefApp.isSupported()) {
            // Fallback if JCEF isn’t available on this machine
            panel.setContent(JPanel(BorderLayout()).apply {
                add(com.intellij.ui.components.JBLabel("JCEF is not supported on this system."), BorderLayout.CENTER)
                border = JBUI.Borders.empty(12)
            })
            addContent(toolWindow, panel)
            return
        }

        // Create the embedded Chromium browser
        val browser = JBCefBrowser("https://www.figma.com")  // or your app/URL
        Disposer.register(toolWindow.disposable, browser)   // dispose with tool window

        // Optional: simple toolbar (Back / Forward / Reload)
        val toolbar = ActionManager.getInstance()
            .createActionToolbar("MyToolWindowToolbar", DefaultActionGroup(
                BackAction(browser), ForwardAction(browser), ReloadAction(browser)
            ), true).apply { targetComponent = panel }

        panel.toolbar = toolbar.component
        panel.setContent(browser.component)

        // Optional: JS ↔ IDE bridge (send strings from page to plugin)
        val jsQuery = com.intellij.ui.jcef.JBCefJSQuery.create(browser)
        jsQuery.addHandler { msgFromPage ->
            // TODO handle message (e.g., parse JSON, run IDE actions)
            null // returned string goes back to JS (or null)
        }
        browser.cefBrowser.executeJavaScript(
            """
            // Expose a function on the page that sends messages to the IDE:
            window.ideBridge = (s) => { ${jsQuery.inject("s")} };
            """.trimIndent(),
            browser.cefBrowser.url, 0
        )

        addContent(toolWindow, panel)
    }

    private fun addContent(toolWindow: ToolWindow, panel: SimpleToolWindowPanel) {
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
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