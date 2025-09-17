package me.tmat.figmaboy.mcp

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import io.ktor.server.cio.CIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.CoroutineScope
import java.net.ServerSocket

class McpServerService(
    private val coroutineScope: CoroutineScope
) : Disposable {

    private val log = logger<McpServerService>()

    private var engine: ApplicationEngine? = null
    @Volatile
    var port: Int = 4114
        private set

    init {
        coroutineScope.launch(Dispatchers.IO) {
            startServer()
        }
    }

    //  private fun freePort(): Int = ServerSocket(0).use { it.localPort }
    private fun usePort(): Int = ServerSocket(4114).use { it.localPort }

    private fun startServer() {
        if (engine != null) return

        port = usePort()
        val localHost = "127.0.0.1"

        val server = embeddedServer(CIO, host = localHost, port = port) {
            module(object : ILogger {
                override fun info(message: String) = println(message)
                override fun warn(message: String) = println(message)
                override fun error(message: String) = println(message)
            })
        }

        server.start(wait = false)
        engine = server.engine
        println("MCP server started on http://$localHost:$port")
    }

    override fun dispose() {
        // Called on dynamic plugin unload/IDE shutdown
        engine?.stop(gracePeriodMillis = 1000, timeoutMillis = 5000)
        engine = null
        println("MCP server stopped")
    }
}
