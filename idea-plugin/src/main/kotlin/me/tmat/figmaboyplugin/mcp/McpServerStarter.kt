package me.tmat.figmaboyplugin.mcp

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.components.service

class McpServerStarter : ProjectActivity {
  override suspend fun execute(project: Project) {
    // Touch the service so it initializes and starts the server.
    service<McpServerService>()
  }
}
