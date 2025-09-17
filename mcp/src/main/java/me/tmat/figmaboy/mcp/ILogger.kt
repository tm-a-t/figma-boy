package me.tmat.figmaboy.mcp

interface ILogger {
    fun info(message: String)
    fun warn(message: String)
    fun error(message: String)
}
