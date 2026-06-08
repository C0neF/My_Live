package com.mylive.app.core.site.huya

import android.util.Base64
import com.mylive.app.core.common.CoreLog
import com.mylive.app.core.common.WebSocketUtils
import com.mylive.app.core.model.DanmakuArgs
import com.mylive.app.core.model.LiveMessage
import com.mylive.app.core.model.LiveMessageColor
import com.mylive.app.core.model.LiveMessageType
import com.mylive.app.core.site.LiveDanmaku
import com.mylive.app.core.site.huya.tars.TarsInputStream
import com.mylive.app.core.site.huya.tars.TarsOutputStream
import com.mylive.app.core.site.huya.tars.model.HYBulletFormat
import com.mylive.app.core.site.huya.tars.model.HYMessage
import com.mylive.app.core.site.huya.tars.model.HYPushMessage
import com.mylive.app.core.site.huya.tars.model.HYSender

/**
 * Huya danmaku (live chat) implementation.
 *
 * Connects via WebSocket using a TARS binary protocol. The join room message
 * is TARS-encoded, and incoming messages are parsed as TARS push messages.
 *
 * Heartbeat is a fixed base64-encoded binary payload sent every 60 seconds.
 *
 * Ported from Dart HuyaDanmaku.
 */
class HuyaDanmaku(
    private val webSocketUtils: WebSocketUtils
) : LiveDanmaku {

    override var heartbeatTime: Int = 60 * 1000
    override var onMessage: ((LiveMessage) -> Unit)? = null
    override var onClose: ((String) -> Unit)? = null
    override var onReady: (() -> Unit)? = null

    private val serverUrl = "wss://cdnws.api.huya.com"

    /**
     * Fixed heartbeat payload. Decoded from base64 "ABQdAAwsNgBM".
     */
    private val heartbeatData = Base64.decode("ABQdAAwsNgBM", Base64.DEFAULT)

    private lateinit var danmakuArgs: DanmakuArgs.Huya

    override suspend fun start(args: DanmakuArgs) {
        danmakuArgs = args as DanmakuArgs.Huya
        webSocketUtils.connect(
            url = serverUrl,
            heartBeatTime = heartbeatTime.toLong(),
            onMessage = { e ->
                when (e) {
                    is ByteArray -> decodeMessage(e)
                }
            },
            onReady = {
                onReady?.invoke()
                joinRoom()
            },
            onHeartBeat = { heartbeat() },
            onReconnect = { onClose?.invoke("与服务器断开连接，正在尝试重连") },
            onClose = { e ->
                if (!e.contains("Socket closed")) {
                    onClose?.invoke("服务器连接失败$e")
                }
            }
        )
    }

    /**
     * Build and send the TARS-encoded join room message.
     */
    private fun joinRoom() {
        val joinData = getJoinData(danmakuArgs.ayyuid, danmakuArgs.topSid, danmakuArgs.topSid)
        webSocketUtils.sendMessage(joinData)
    }

    /**
     * Encode the join room message using TARS binary protocol.
     *
     * Structure: outer cmd stream has tag 0 = command type (1),
     * tag 1 = inner stream (join room params).
     * Inner stream: tag 0 = ayyuid, tag 1 = true, tag 2/3 = empty strings,
     * tag 4 = tid, tag 5 = sid, tag 6/7 = 0.
     */
    private fun getJoinData(ayyuid: Int, tid: Long, sid: Long): ByteArray {
        return try {
            val oos = TarsOutputStream()
            oos.write(ayyuid, 0)
            oos.write(true, 1)
            oos.write("", 2)
            oos.write("", 3)
            oos.write(tid, 4)
            oos.write(sid, 5)
            oos.write(0, 6)
            oos.write(0, 7)

            val wscmd = TarsOutputStream()
            wscmd.write(1, 0)
            wscmd.write(oos.toByteArray(), 1)
            wscmd.toByteArray()
        } catch (e: Exception) {
            CoreLog.error(e)
            ByteArray(0)
        }
    }

    override fun heartbeat() {
        webSocketUtils.sendMessage(heartbeatData)
    }

    override suspend fun stop() {
        onMessage = null
        onClose = null
        webSocketUtils.close()
    }

    /**
     * Decode incoming WebSocket binary messages.
     *
     * The outer stream has tag 0 = type. Type 7 indicates a push message.
     * The payload at tag 1 is parsed as an HYPushMessage.
     * URI 1400 = chat message, URI 8006 = online count.
     */
    private fun decodeMessage(data: ByteArray) {
        try {
            var stream = TarsInputStream(data)
            val type = stream.readInt(0, false).toInt()
            if (type == 7) {
                val innerBytes = stream.readBytes(1, false)
                stream = TarsInputStream(innerBytes)
                val wSPushMessage = HYPushMessage()
                wSPushMessage.readFrom(stream)

                when (wSPushMessage.uri) {
                    1400 -> {
                        // Chat message
                        val messageNotice = HYMessage()
                        messageNotice.readFrom(TarsInputStream(wSPushMessage.msg))
                        val uname = messageNotice.userInfo.nickName
                        val content = messageNotice.content
                        val color = messageNotice.bulletFormat.fontColor

                        onMessage?.invoke(
                            LiveMessage(
                                type = LiveMessageType.CHAT,
                                color = if (color <= 0) LiveMessageColor.WHITE
                                else LiveMessageColor.numberToColor(color),
                                message = content,
                                userName = uname
                            )
                        )
                    }
                    8006 -> {
                        // Online count
                        val s = TarsInputStream(wSPushMessage.msg)
                        val online = s.readInt(0, false).toInt()
                        onMessage?.invoke(
                            LiveMessage(
                                type = LiveMessageType.ONLINE,
                                message = "",
                                userName = "",
                                color = LiveMessageColor.WHITE,
                                onlineCount = online
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            CoreLog.error(e)
        }
    }
}
