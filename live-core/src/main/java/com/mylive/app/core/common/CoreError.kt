package com.mylive.app.core.common

/**
 * Core error class for the application.
 * Wraps HTTP status codes and error messages into a unified exception.
 *
 * @param message The error message
 * @param statusCode The HTTP status code (0 if not applicable)
 */
class CoreError(
    override val message: String,
    val statusCode: Int = 0
) : Exception(message) {

    override fun toString(): String {
        return if (statusCode != 0) {
            statusCodeToString(statusCode)
        } else {
            message
        }
    }

    private fun statusCodeToString(statusCode: Int): String {
        return when (statusCode) {
            400 -> "错误的请求(400)"
            401 -> "无权限访问资源(401)"
            403 -> "无权限访问资源(403)"
            404 -> "服务器找不到请求的资源(404)"
            444 -> "抖音访问过于频繁或触发风控限制(444)，请稍后再试，避免连续刷新或重复进入直播间"
            500 -> "服务器出现错误(500)"
            502 -> "服务器出现错误(502)"
            503 -> "服务器出现错误(503)"
            else -> "连接服务器失败，请稍后再试($statusCode)"
        }
    }
}
