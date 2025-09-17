package me.tmat.figmaboy.mcp

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import kotlinx.serialization.json.*

fun Application.module(log: ILogger) {
    val hub = PluginHub()

//    install(CallLogging) { logger = log }
    install(ContentNegotiation) { json() }
//    install(SSE)
    install(WebSockets)

    mcp {
        Server(
            serverInfo = Implementation(
                name = "figma-boy",
                version = "1.0.0"
            ),
            options = ServerOptions(
                capabilities = ServerCapabilities(
                    prompts = ServerCapabilities.Prompts(listChanged = true),
                    resources = ServerCapabilities.Resources(subscribe = true, listChanged = true),
                    tools = ServerCapabilities.Tools(listChanged = true),
                )
            )
        ).apply {
            addTools(registerTools(hub))
        }
    }

    routing {

        get("/") {
            call.respondText("MCP server running")
        }

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
    }
}
