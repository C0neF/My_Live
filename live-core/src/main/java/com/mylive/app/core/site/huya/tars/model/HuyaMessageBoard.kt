package com.mylive.app.core.site.huya.tars.model

import com.mylive.app.core.site.huya.tars.TarsInputStream
import com.mylive.app.core.site.huya.tars.TarsOutputStream
import com.mylive.app.core.site.huya.tars.TarsStruct

/**
 * Request for the headline/super-chat message board.
 */
class GetGameEventMessageBoardReq : TarsStruct() {
    var lPid: Long = 0                          // tag 0
    var sOffset: String = ""                    // tag 1
    var tId: HuyaUserId = HuyaUserId()          // tag 2
    var iMessageBoardScope: Int = 0             // tag 3
    var iPageSize: Int = 10                     // tag 4

    override fun readFrom(`is`: TarsInputStream) {
        lPid = `is`.readInt(0, false)
        sOffset = `is`.readString(1, false)
        tId = `is`.readTarsStruct(tId, 2, false) as HuyaUserId
        iMessageBoardScope = `is`.readInt(3, false).toInt()
        iPageSize = `is`.readInt(4, false).toInt()
    }

    override fun writeTo(os: TarsOutputStream) {
        os.write(lPid, 0)
        os.write(sOffset, 1)
        os.write(tId, 2)
        os.write(iMessageBoardScope, 3)
        os.write(iPageSize, 4)
    }

    override fun deepCopy(): TarsStruct {
        return GetGameEventMessageBoardReq().also {
            it.lPid = lPid
            it.sOffset = sOffset
            it.tId = tId.deepCopy() as HuyaUserId
            it.iMessageBoardScope = iMessageBoardScope
            it.iPageSize = iPageSize
        }
    }
}

/**
 * Response for the headline/super-chat message board.
 */
class GetGameEventMessageBoardRsp : TarsStruct() {
    var tMessageBoardPanel: GameEventMessageBoardPanel = GameEventMessageBoardPanel() // tag 1

    override fun readFrom(`is`: TarsInputStream) {
        tMessageBoardPanel = `is`.readTarsStruct(tMessageBoardPanel, 1, false) as GameEventMessageBoardPanel
    }

    override fun writeTo(os: TarsOutputStream) {
        os.write(tMessageBoardPanel, 1)
    }

    override fun deepCopy(): TarsStruct {
        return GetGameEventMessageBoardRsp().also {
            it.tMessageBoardPanel = tMessageBoardPanel.deepCopy() as GameEventMessageBoardPanel
        }
    }
}

/**
 * Panel containing a list of message board info items.
 */
class GameEventMessageBoardPanel : TarsStruct() {
    var vGameEventMessageBoardInfo: List<GameEventMessageBoardInfo> =
        listOf(GameEventMessageBoardInfo()) // tag 1

    override fun readFrom(`is`: TarsInputStream) {
        vGameEventMessageBoardInfo = `is`.readList<GameEventMessageBoardInfo>(
            GameEventMessageBoardInfo(), 1, false
        )
    }

    override fun writeTo(os: TarsOutputStream) {
        os.write(vGameEventMessageBoardInfo, 1)
    }

    override fun deepCopy(): TarsStruct {
        return GameEventMessageBoardPanel().also {
            it.vGameEventMessageBoardInfo = vGameEventMessageBoardInfo.map { e ->
                e.deepCopy() as GameEventMessageBoardInfo
            }
        }
    }
}

/**
 * Individual message board info item.
 */
class GameEventMessageBoardInfo : TarsStruct() {
    var tMessageUser: MessageUser = MessageUser()  // tag 0
    var sContent: String = ""                       // tag 1
    var iCost: Int = 0                              // tag 2
    var iTotalSec: Int = 0                          // tag 4
    var iCountDown: Int = 0                         // tag 5
    var lMessageId: Long = 0                        // tag 9
    var iCostPay: Int = 0                           // tag 12

    override fun readFrom(`is`: TarsInputStream) {
        tMessageUser = `is`.readTarsStruct(tMessageUser, 0, false) as MessageUser
        sContent = `is`.readString(1, false)
        iCost = `is`.readInt(2, false).toInt()
        iTotalSec = `is`.readInt(4, false).toInt()
        iCountDown = `is`.readInt(5, false).toInt()
        lMessageId = `is`.readInt(9, false)
        iCostPay = `is`.readInt(12, false).toInt()
    }

    override fun writeTo(os: TarsOutputStream) {
        os.write(tMessageUser, 0)
        os.write(sContent, 1)
        os.write(iCost, 2)
        os.write(iTotalSec, 4)
        os.write(iCountDown, 5)
        os.write(lMessageId, 9)
        os.write(iCostPay, 12)
    }

    override fun deepCopy(): TarsStruct {
        return GameEventMessageBoardInfo().also {
            it.tMessageUser = tMessageUser.deepCopy() as MessageUser
            it.sContent = sContent
            it.iCost = iCost
            it.iTotalSec = iTotalSec
            it.iCountDown = iCountDown
            it.lMessageId = lMessageId
            it.iCostPay = iCostPay
        }
    }
}

/**
 * User info for message board entries.
 */
class MessageUser : TarsStruct() {
    var sNick: String = ""     // tag 1
    var sAvatar: String = ""   // tag 2

    override fun readFrom(`is`: TarsInputStream) {
        sNick = `is`.readString(1, false)
        sAvatar = `is`.readString(2, false)
    }

    override fun writeTo(os: TarsOutputStream) {
        os.write(sNick, 1)
        os.write(sAvatar, 2)
    }

    override fun deepCopy(): TarsStruct {
        return MessageUser().also {
            it.sNick = sNick
            it.sAvatar = sAvatar
        }
    }
}
