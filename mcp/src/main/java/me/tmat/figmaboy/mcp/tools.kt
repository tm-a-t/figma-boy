package me.tmat.figmaboy.mcp

import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.RegisteredTool
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject


private val tools = listOf(
    Tool(
        name = "get_selection",
        title = "Get Selection",
        description = "Return an array of selected node IDs and minimal metadata",
        inputSchema = Tool.Input(),
        outputSchema = Tool.Output(),
        annotations = null,
    ),
    Tool(
        name = "replace_text",
        title = "Replace Text",
        description = "Replace text in a TEXT node",
        inputSchema = Tool.Input(
            buildJsonObject {
                put("nodeId", buildJsonObject { put("type", JsonPrimitive("string")) })
                put("text", buildJsonObject { put("type", JsonPrimitive("string")) })
            }
        ),
        outputSchema = Tool.Output(),
        annotations = null,
    ),
)

fun registerTools(hub: PluginHub) =
    tools.map { tool ->
        RegisteredTool(tool) { request ->

            assert(hub.isConnected())

            val figmaPluginResponse = hub.callToolThroughPlugin(tool.name, request.arguments)
            if (figmaPluginResponse.ok) {
                CallToolResult(
                    listOf(),  // todo short text response
                    buildJsonObject {
                        put("result", figmaPluginResponse.result ?: JsonNull)
                    }
                )
            } else {
                CallToolResult(
                    listOf(),
                    isError = true,
                )
            }
        }
    }
