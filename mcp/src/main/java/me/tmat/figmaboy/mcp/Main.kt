package me.tmat.figmaboy.mcp

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer

fun main() {
    embeddedServer(CIO, host = "127.0.0.1", port = 3001) {
        module(object : ILogger {
            override fun info(message: String) = println(message)
            override fun warn(message: String) = println(message)
            override fun error(message: String) = println(message)
        })
    }.start(wait = true)
}
