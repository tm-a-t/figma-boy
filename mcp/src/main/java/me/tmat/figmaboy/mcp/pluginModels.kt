package me.tmat.figmaboy.mcp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

val json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    encodeDefaults = true
}

@Serializable
sealed interface FigmaPluginMessage {
    val type: String
}

@Serializable
data class FigmaPluginHello(
    override val type: String = "hello",
    val fileKey: String? = null,
    val pluginVersion: String? = null,
    val capabilities: List<String> = emptyList()
) : FigmaPluginMessage

@Serializable
data class FigmaPluginCommand(
    override val type: String = "command",
    val requestId: String,
    val name: String,
    val args: JsonObject = buildJsonObject { }
) : FigmaPluginMessage

@Serializable
data class FigmaPluginResponse(
    override val type: String = "response",
    val requestId: String,
    val ok: Boolean,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null
) : FigmaPluginMessage
