package me.tmat.figmaboy.mcp

import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.set

/**
 * Manages a single-plugin session (for now)
 */
class PluginHub {
    private var session: DefaultWebSocketServerSession? = null
    private val mutex = Mutex()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<FigmaPluginResponse>>()

    suspend fun setSession(sess: DefaultWebSocketServerSession?) {
        mutex.withLock { session = sess }
    }

    suspend fun isConnected(): Boolean = mutex.withLock { session != null }

    suspend fun callToolThroughPlugin(name: String, args: JsonObject, timeoutMs: Long = 20_000): FigmaPluginResponse {
        val reqId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<FigmaPluginResponse>()
        pending[reqId] = deferred

        val s = mutex.withLock { session } ?: run {
            pending.remove(reqId)
            throw IllegalStateException("Plugin is not connected")
        }

        val payload = json.encodeToString(FigmaPluginCommand(requestId = reqId, name = name, args = args))
        s.send(Frame.Text(payload))

        return try {
            withTimeout(timeoutMs) { deferred.await() }
        } finally {
            pending.remove(reqId)
        }
    }

    fun complete(requestId: String, response: FigmaPluginResponse) {
        pending.remove(requestId)?.complete(response)
    }
}
