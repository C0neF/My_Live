package com.mylive.app.core.site.huya.tars.model

import com.mylive.app.core.site.huya.tars.TarsInputStream
import com.mylive.app.core.site.huya.tars.TarsOutputStream
import com.mylive.app.core.site.huya.tars.TarsStruct

/**
 * Extended CDN token request (getCdnTokenInfoEx).
 */
class GetCdnTokenExReq : TarsStruct() {
    var sFlvUrl: String = ""           // tag 0
    var sStreamName: String = ""       // tag 1
    var iLoopTime: Int = 0             // tag 2
    var tId: HuyaUserId = HuyaUserId() // tag 3
    var iAppId: Int = 66               // tag 4

    override fun readFrom(`is`: TarsInputStream) {
        sFlvUrl = `is`.readString(0, false)
        sStreamName = `is`.readString(1, false)
        iLoopTime = `is`.readInt(2, false).toInt()
        tId = `is`.readTarsStruct(tId, 3, false) as HuyaUserId
        iAppId = `is`.readInt(4, false).toInt()
    }

    override fun writeTo(os: TarsOutputStream) {
        os.write(sFlvUrl, 0)
        os.write(sStreamName, 1)
        os.write(iLoopTime, 2)
        os.write(tId, 3)
        os.write(iAppId, 4)
    }

    override fun deepCopy(): TarsStruct {
        return GetCdnTokenExReq().also {
            it.sFlvUrl = sFlvUrl
            it.sStreamName = sStreamName
            it.iLoopTime = iLoopTime
            it.tId = tId.deepCopy() as HuyaUserId
            it.iAppId = iAppId
        }
    }
}
