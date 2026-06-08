package com.mylive.app.core.site.huya.tars.model

import com.mylive.app.core.site.huya.tars.TarsInputStream
import com.mylive.app.core.site.huya.tars.TarsOutputStream
import com.mylive.app.core.site.huya.tars.TarsStruct

/**
 * Huya user identity struct used in TARS RPC requests.
 */
class HuyaUserId : TarsStruct() {
    var lUid: Long = 0          // tag 0
    var sGuid: String = ""      // tag 1
    var sToken: String = ""     // tag 2
    var sHuYaUA: String = ""    // tag 3
    var sCookie: String = ""    // tag 4
    var iTokenType: Int = 0     // tag 5
    var sDeviceInfo: String = "" // tag 6
    var sQIMEI: String = ""     // tag 7

    override fun readFrom(`is`: TarsInputStream) {
        lUid = `is`.readInt(0, false)
        sGuid = `is`.readString(1, false)
        sToken = `is`.readString(2, false)
        sHuYaUA = `is`.readString(3, false)
        sCookie = `is`.readString(4, false)
        iTokenType = `is`.readInt(5, false).toInt()
        sDeviceInfo = `is`.readString(6, false)
        sQIMEI = `is`.readString(7, false)
    }

    override fun writeTo(os: TarsOutputStream) {
        os.write(lUid, 0)
        os.write(sGuid, 1)
        os.write(sToken, 2)
        os.write(sHuYaUA, 3)
        os.write(sCookie, 4)
        os.write(iTokenType, 5)
        os.write(sDeviceInfo, 6)
        os.write(sQIMEI, 7)
    }

    override fun deepCopy(): TarsStruct {
        return HuyaUserId().also {
            it.lUid = lUid
            it.sGuid = sGuid
            it.sToken = sToken
            it.sHuYaUA = sHuYaUA
            it.sCookie = sCookie
            it.iTokenType = iTokenType
            it.sDeviceInfo = sDeviceInfo
            it.sQIMEI = sQIMEI
        }
    }
}
