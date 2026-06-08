package com.mylive.app.core.site.huya.tars.model

import com.mylive.app.core.site.huya.tars.TarsInputStream
import com.mylive.app.core.site.huya.tars.TarsOutputStream
import com.mylive.app.core.site.huya.tars.TarsStruct

/**
 * CDN token response (getCdnTokenInfo).
 */
class GetCdnTokenResp : TarsStruct() {
    var url: String = ""            // tag 0
    var cdnType: String = ""        // tag 1
    var streamName: String = ""     // tag 2
    var presenterUid: Int = 0       // tag 3
    var antiCode: String = ""       // tag 4
    var sTime: String = ""          // tag 5
    var flvAntiCode: String = ""    // tag 6
    var hlsAntiCode: String = ""    // tag 7

    override fun readFrom(`is`: TarsInputStream) {
        url = `is`.readString(0, false)
        cdnType = `is`.readString(1, false)
        streamName = `is`.readString(2, false)
        presenterUid = `is`.readInt(3, false).toInt()
        antiCode = `is`.readString(4, false)
        sTime = `is`.readString(5, false)
        flvAntiCode = `is`.readString(6, false)
        hlsAntiCode = `is`.readString(7, false)
    }

    override fun writeTo(os: TarsOutputStream) {
        os.write(url, 0)
        os.write(cdnType, 1)
        os.write(streamName, 2)
        os.write(presenterUid, 3)
        os.write(antiCode, 4)
        os.write(sTime, 5)
        os.write(flvAntiCode, 6)
        os.write(hlsAntiCode, 7)
    }

    override fun deepCopy(): TarsStruct {
        return GetCdnTokenResp().also {
            it.url = url
            it.cdnType = cdnType
            it.streamName = streamName
            it.presenterUid = presenterUid
            it.antiCode = antiCode
            it.sTime = sTime
            it.flvAntiCode = flvAntiCode
            it.hlsAntiCode = hlsAntiCode
        }
    }
}
