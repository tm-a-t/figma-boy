package me.tmat.figmaboy.mcp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject


@Serializable
data class McpTool(
    val name: String,
    val description: String,
    val inputSchema: JsonObject
)

@Serializable
data class ToolsListResult(
    val tools: List<McpTool>,
    val nextCursor: String? = null // omitted when not paging
)

@Serializable
data class ToolCallParams(
    val name: String,
    val arguments: JsonObject = buildJsonObject { }
)