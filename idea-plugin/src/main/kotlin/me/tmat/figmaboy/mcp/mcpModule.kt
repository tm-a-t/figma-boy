package me.tmat.figmaboy.mcp

import com.intellij.openapi.diagnostic.Logger
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.receiveText
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.server.websocket.*
import io.ktor.sse.ServerSentEvent
import io.ktor.websocket.*
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.serialization.json.*

private val json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    encodeDefaults = true
}

fun Application.mcpModule(log: Logger) {
    val hub = PluginHub()

//    install(CallLogging) {  }
    install(ContentNegotiation) { json() }
    install(SSE)
    install(WebSockets)

    routing {
        
        get("/") {
            call.respondText("MCP server running")
        }

        // A simple single-client SSE bridge for MCP responses
        // The POST /mcp endpoint accepts JSON-RPC requests from the client
        // The SSE /mcp endpoint streams JSON-RPC responses back to the client
        val mcpOutbound = kotlinx.coroutines.channels.Channel<String>(capacity = kotlinx.coroutines.channels.Channel.BUFFERED)

        // --- Figma Plugin WebSocket: connects here and receives commands
        webSocket("/plugin") {
            log.info("Plugin connected from ${call.request.origin.remoteHost}")
            hub.setSession(this)

            try {
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val text = frame.readText()
                            val asJson = runCatching { json.parseToJsonElement(text) }.getOrNull()
                            val type = asJson?.jsonObject?.get("type")?.jsonPrimitive?.content ?: ""

                            when (type) {
                                "hello" -> {
                                    val hello = json.decodeFromJsonElement<FigmaPluginHello>(asJson!!)
                                    log.info("Plugin hello: $hello")
                                }

                                "response" -> {
                                    val resp = json.decodeFromJsonElement<FigmaPluginResponse>(asJson!!)
                                    hub.complete(resp.requestId, resp)
                                }

                                else -> log.warn("Unknown plugin message: $text")
                            }
                        }

                        is Frame.Close -> log.info("Plugin closed: ${frame.readReason()}")
                        else -> Unit
                    }
                }
            } finally {
                hub.setSession(null)
                log.info("Plugin disconnected")
            }
        }

        // --- MCP Client SSE + POST: agent/LLM connects via SSE and sends requests via POST
        sse("/mcp") {
            println("MCP client connected via SSE")
            try {
                for (msg in mcpOutbound) {
                    send(ServerSentEvent(data = msg))
                }
            } finally {
                println("MCP SSE client disconnected")
            }
        }

        post("/mcp") {
            val text = call.receiveText()
            val req = runCatching { json.decodeFromString<JsonRpcRequest>(text) }.getOrNull()
            val resp = if (req == null) {
                JsonRpcResponse(
                    id = null,
                    error = JsonRpcError(-32700, "Parse error")
                )
            } else {
                answerRequest(req, hub)
            }
            val encoded = json.encodeToString(resp)
            mcpOutbound.trySend(encoded)
            call.respondText("ok")
        }

        // Health check
        get("/healthz") {
            val ok = hub.isConnected()
            call.respondText(if (ok) "ok (plugin connected)" else "ok (plugin NOT connected)")
        }
    }
}

suspend fun answerRequest(req: JsonRpcRequest, hub: PluginHub): JsonRpcResponse {
    when (req.method) {

        // MCP: initialize
        "initialize" -> {
            val result = buildJsonObject {
                put("protocolVersion", JsonPrimitive("2025-06-18")) // MCP spec revision string
                put("serverInfo", buildJsonObject {
                    put("name", JsonPrimitive("ktor-mcp-bridge"))
                    put("version", JsonPrimitive("0.1.0"))
                    put("title", JsonPrimitive("Ktor MCP Bridge"))
                })
                put("capabilities", buildJsonObject {
                    put("tools", buildJsonObject {
                        // You are not implementing listChanged notifications
                        put("listChanged", JsonPrimitive(false))
                    })
                })
                put(
                    "instructions",
                    JsonPrimitive("Bridge to a Figma plugin. Use tools/list then tools/call.")
                )
            }
            return JsonRpcResponse(id = req.id, result = result)
        }

        // MCP: ping
        "ping" -> {
            return JsonRpcResponse(id = req.id, result = buildJsonObject { })
        }

        // MCP: list tools (no pagination here; nextCursor omitted)
        "tools/list" -> {
            val tools = listOf(
                McpTool(
                    name = "get_selection",
                    description = "Return an array of selected node IDs and minimal metadata",
                    inputSchema = buildJsonObject {
                        put("type", JsonPrimitive("object"))
                        put("properties", buildJsonObject { })
                    }
                ),
                McpTool(
                    name = "replace_text",
                    description = "Replace text in a TEXT node",
                    inputSchema = buildJsonObject {
                        put("type", JsonPrimitive("object"))
                        put("required", JsonArray(listOf(JsonPrimitive("nodeId"), JsonPrimitive("text"))))
                        put("properties", buildJsonObject {
                            put("nodeId", buildJsonObject { put("type", JsonPrimitive("string")) })
                            put("text", buildJsonObject { put("type", JsonPrimitive("string")) })
                        })
                    }
                ),
            )
            val result = json.encodeToJsonElement(ToolsListResult(tools))
            return JsonRpcResponse(id = req.id, result = result)
        }

        // MCP: call a tool -> bridge to plugin
        "tools/call" -> {
            val params =
                req.params?.let { runCatching { json.decodeFromJsonElement<ToolCallParams>(it) }.getOrNull() }
            if (params == null) {
                return JsonRpcResponse(id = req.id, error = JsonRpcError(-32602, "Invalid params"))
            }

            if (!hub.isConnected()) {
                // Protocol-level issue -> JSON-RPC error
                return JsonRpcResponse(
                    id = req.id,
                    error = JsonRpcError(1001, "Figma plugin is not connected")
                )
            }

            try {
                val pluginResp = hub.callToolThroughPlugin(params.name, params.arguments)

                if (pluginResp.ok) {
                    // Proper CallToolResult
                    val result = buildJsonObject {
                        put("content", JsonArray(listOf(buildJsonObject {
                            put("type", JsonPrimitive("text"))
                            put("text", JsonPrimitive("ok"))
                        })))
                        // Also provide structured content for agents/UIs
                        pluginResp.result?.let { put("structuredContent", it) }
                    }
                    return JsonRpcResponse(id = req.id, result = result)
                } else {
                    // Tool-level failure should be a normal result with isError=true
                    val result = buildJsonObject {
                        put("isError", JsonPrimitive(true))
                        put("content", JsonArray(listOf(buildJsonObject {
                            put("type", JsonPrimitive("text"))
                            put("text", JsonPrimitive(pluginResp.error?.message ?: "Tool error"))
                        })))
                        pluginResp.error?.let { err ->
                            put("structuredContent", buildJsonObject {
                                put("code", JsonPrimitive(err.code))
                                err.data?.let { d -> put("data", d) }
                            })
                        }
                    }
                    return JsonRpcResponse(id = req.id, result = result)
                }
            } catch (t: TimeoutCancellationException) {
                return JsonRpcResponse(
                    id = req.id,
                    error = JsonRpcError(1003, "Timeout waiting for plugin response")
                )
            } catch (t: Throwable) {
                return JsonRpcResponse(
                    id = req.id,
                    error = JsonRpcError(1004, "Bridge error", JsonPrimitive(t.message ?: ""))
                )
            }
        }

        else -> {
            return JsonRpcResponse(
                id = req.id,
                error = JsonRpcError(-32601, "Method not found: ${req.method}")
            )
        }
    }
}
