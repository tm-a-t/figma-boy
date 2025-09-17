package me.tmat.figmaboyplugin.mcp

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service

class McpDebugAction : AnAction("Meow") {
    override fun actionPerformed(e: AnActionEvent) {
        val port = service<McpServerService>().port
        BrowserUtil.browse("http://127.0.0.1:$port/")
    }
}