package com.mylive.app.core.site.huya.tars.model

import com.mylive.app.core.site.huya.tars.TarsInputStream
import com.mylive.app.core.site.huya.tars.TarsOutputStream
import com.mylive.app.core.site.huya.tars.TarsStruct

/**
 * CDN token request (getCdnTokenInfo).
 */
class GetCdnTokenReq : TarsStruct() {
    var url: String = ""            // tag 0
    var cdnType: String = ""        // tag 1
    var streamName: String = ""     // tag 2
    var presenterUid: Int = 0       // tag 3

    override fun readFrom(`is`: TarsInputStream) {
        url = `is`.readString(0, false)
        cdnType = `is`.readString(1, false)
        streamName = `is`.readString(2, false)
        presenterUid = `is`.readInt(3, false).toInt()
    }

    override fun writeTo(os: TarsOutputStream) {
        os.write(url, 0)
        os.write(cdnType, 1)
        os.write(streamName, 2)
        os.write(presenterUid, 3)
    }

    override fun deepCopy(): TarsStruct {
        return GetCdnTokenReq().also {
            it.url = url
            it.cdnType = cdnType
            it.streamName = streamName
            it.presenterUid = presenterUid
        }
    }
}
