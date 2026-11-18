package com.mylive.app.core.site.huya.tars.model

import com.mylive.app.core.site.huya.tars.TarsInputStream
import com.mylive.app.core.site.huya.tars.TarsOutputStream
import com.mylive.app.core.site.huya.tars.TarsStruct

/**
 * TARS push message envelope.
 * Used to decode incoming WebSocket messages from Huya.
 */
class HYPushMessage : TarsStruct() {
    var pushType: Int = 0      // tag 0
    var uri: Int = 0           // tag 1
    var msg: ByteArray = ByteArray(0)  // tag 2
    var protocolType: Int = 0  // tag 3

    override fun readFrom(`is`: TarsInputStream) {
        pushType = `is`.readInt(0, false).toInt()
        uri = `is`.readInt(1, false).toInt()
        msg = `is`.readBytes(2, false)
        protocolType = `is`.readInt(3, false).toInt()
    }

    override fun writeTo(os: TarsOutputStream) {
        os.write(pushType, 0)
        os.write(uri, 1)
        os.write(msg, 2)
        os.write(protocolType, 3)
    }

    override fun deepCopy(): TarsStruct {
        return HYPushMessage().also {
            it.pushType = pushType
            it.uri = uri
            it.msg = msg.copyOf()
            it.protocolType = protocolType
        }
    }
}

/**
 * Chat message sender info.
 */
class HYSender : TarsStruct() {
    var uid: Int = 0           // tag 0
    var lMid: Int = 0          // tag 1
    var nickName: String = ""  // tag 2
    var gender: Int = 0        // tag 3

    override fun readFrom(`is`: TarsInputStream) {
        uid = `is`.readInt(0, false).toInt()
        lMid = `is`.readInt(1, false).toInt()
        nickName = `is`.readString(2, false)
        gender = `is`.readInt(3, false).toInt()
    }

    override fun writeTo(os: TarsOutputStream) {
        os.write(uid, 0)
        os.write(lMid, 1)
        os.write(nickName, 2)
        os.write(gender, 3)
    }

    override fun deepCopy(): TarsStruct {
        return HYSender().also {
            it.uid = uid
            it.lMid = lMid
            it.nickName = nickName
            it.gender = gender
        }
    }
}

/**
 * Chat message content wrapper.
 */
class HYMessage : TarsStruct() {
    var userInfo: HYSender = HYSender()           // tag 0
    var content: String = ""                       // tag 3
    var bulletFormat: HYBulletFormat = HYBulletFormat()  // tag 6

    override fun readFrom(`is`: TarsInputStream) {
        userInfo = `is`.readTarsStruct(userInfo, 0, false) as HYSender
        content = `is`.readString(3, false)
        bulletFormat = `is`.readTarsStruct(bulletFormat, 6, false) as HYBulletFormat
    }

    override fun writeTo(os: TarsOutputStream) {
        os.write(userInfo, 0)
        os.write(content, 3)
        os.write(bulletFormat, 6)
    }

    override fun deepCopy(): TarsStruct {
        return HYMessage().also {
            it.userInfo = userInfo.deepCopy() as HYSender
            it.content = content
            it.bulletFormat = bulletFormat.deepCopy() as HYBulletFormat
        }
    }
}

/**
 * Bullet screen (danmaku) formatting info.
 */
class HYBulletFormat : TarsStruct() {
    var fontColor: Int = 0       // tag 0
    var fontSize: Int = 4        // tag 1
    var textSpeed: Int = 0       // tag 2
    var transitionType: Int = 1  // tag 3

    override fun readFrom(`is`: TarsInputStream) {
        fontColor = `is`.readInt(0, false).toInt()
        fontSize = `is`.readInt(1, false).toInt()
        textSpeed = `is`.readInt(2, false).toInt()
        transitionType = `is`.readInt(3, false).toInt()
    }

    override fun writeTo(os: TarsOutputStream) {
        os.write(fontColor, 0)
        os.write(fontSize, 1)
        os.write(textSpeed, 2)
        os.write(transitionType, 3)
    }

    override fun deepCopy(): TarsStruct {
        return HYBulletFormat().also {
            it.fontColor = fontColor
            it.fontSize = fontSize
            it.textSpeed = textSpeed
            it.transitionType = transitionType
        }
    }
}
