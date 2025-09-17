package me.tmat.figmaboy.mcp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// json rpc 2.0

@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: String? = null,
    val method: String,
    val params: JsonElement? = null
)

@Serializable
data class JsonRpcError(val code: Int, val message: String, val data: JsonElement? = null)

@Serializable
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: String? = null,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null
)
