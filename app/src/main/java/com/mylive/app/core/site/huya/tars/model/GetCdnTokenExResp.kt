package com.mylive.app.core.site.huya.tars.model

import com.mylive.app.core.site.huya.tars.TarsInputStream
import com.mylive.app.core.site.huya.tars.TarsOutputStream
import com.mylive.app.core.site.huya.tars.TarsStruct

/**
 * Extended CDN token response (getCdnTokenInfoEx).
 */
class GetCdnTokenExResp : TarsStruct() {
    var sFlvToken: String = ""   // tag 0
    var iExpireTime: Int = 0     // tag 1

    override fun readFrom(`is`: TarsInputStream) {
        sFlvToken = `is`.readString(0, false)
        iExpireTime = `is`.readInt(1, false).toInt()
    }

    override fun writeTo(os: TarsOutputStream) {
        os.write(sFlvToken, 0)
        os.write(iExpireTime, 1)
    }

    override fun deepCopy(): TarsStruct {
        return GetCdnTokenExResp().also {
            it.sFlvToken = sFlvToken
            it.iExpireTime = iExpireTime
        }
    }
}
