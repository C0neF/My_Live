package com.mylive.app.core.site.huya.tars

/**
 * TUP packet encoding/decoding.
 *
 * Wraps a [RequestPacket] and an [UniAttribute] (inherited) to produce
 * the final binary payload for TARS RPC calls.
 *
 * Ported from Dart tars_dart UniPacket.
 */
open class UniPacket : UniAttribute() {

    companion object {
        const val K_UNI_PACKET_HEAD_SIZE = 4
    }

    val packageData = RequestPacket()

    var servantName: String
        get() = packageData.sServantName
        set(value) { packageData.sServantName = value }

    var funcName: String
        get() = packageData.sFuncName
        set(value) { packageData.sFuncName = value }

    var requestId: Int
        get() = packageData.iRequestId
        set(value) { packageData.iRequestId = value }

    init {
        packageData.iVersion = PACKET_TYPE_TUP3
    }

    fun setPacketVersion(iVer: Int) {
        version = iVer
        packageData.iVersion = iVer
    }

    /**
     * Encode the packet: serialize newData into sBuffer, then serialize
     * the RequestPacket, and prefix with a 4-byte big-endian length.
     */
    override fun encode(): ByteArray {
        require(servantName.isNotEmpty()) { "servantName can not be null" }
        require(funcName.isNotEmpty()) { "funcName can not be null" }

        val os1 = TarsOutputStream()
        if (packageData.iVersion == PACKET_TYPE_TUP) {
            throw UnsupportedOperationException("TUP2 encoding not supported")
        } else {
            os1.write(newData, 0)
        }
        packageData.sBuffer = os1.toByteArray()

        val os2 = TarsOutputStream()
        writeTo(os2)
        val body = os2.toByteArray()
        val size = body.size

        val writeBuffer = WriteBuffer()
        writeBuffer.putInt32(size + K_UNI_PACKET_HEAD_SIZE, bigEndian = true)
        writeBuffer.putUint8List(body)
        return writeBuffer.done()
    }

    /**
     * Decode the packet from raw bytes. Reads the 4-byte length prefix,
     * then deserializes the RequestPacket, and finally the attribute data.
     */
    override fun decode(buffer: ByteArray, index: Int) {
        require(buffer.size >= K_UNI_PACKET_HEAD_SIZE) { "Decode namespace must include size head" }

        try {
            val `is` = TarsInputStream(buffer, K_UNI_PACKET_HEAD_SIZE + index)
            readFrom(`is`)

            // Set TUP version
            version = packageData.iVersion

            val bufferIs = TarsInputStream(packageData.sBuffer ?: ByteArray(0))
            if (packageData.iVersion == PACKET_TYPE_TUP) {
                oldData = bufferIs.readMapMap<String, String, ByteArray>(
                    "", "", byteArrayOf(0), 0, false
                ).toMutableMap()
            } else {
                newData = bufferIs.readMapOf<String, ByteArray>(
                    "", byteArrayOf(0), 0, false
                ).toMutableMap()
            }
        } catch (e: Exception) {
            throw e
        }
    }

    override fun writeTo(os: TarsOutputStream) {
        packageData.writeTo(os)
    }

    override fun readFrom(`is`: TarsInputStream) {
        packageData.readFrom(`is`)
    }
}

/**
 * RequestPacket - the outer envelope for TARS RPC requests.
 */
class RequestPacket : TarsStruct() {
    var iVersion: Int = 0
    var cPacketType: Int = 0
    var iMessageType: Int = 0
    var iRequestId: Int = 0
    var sServantName: String = ""
    var sFuncName: String = ""
    var sBuffer: ByteArray? = null
    var iTimeout: Int = 0
    var context: MutableMap<String, String>? = null
    var status: MutableMap<String, String>? = null

    override fun writeTo(os: TarsOutputStream) {
        os.write(iVersion, 1)
        os.write(cPacketType, 2)
        os.write(iMessageType, 3)
        os.write(iRequestId, 4)
        os.write(sServantName, 5)
        os.write(sFuncName, 6)
        sBuffer?.let { os.write(it, 7) }
        os.write(iTimeout, 8)
        context?.let { os.write(it, 9) }
        status?.let { os.write(it, 10) }
    }

    override fun readFrom(`is`: TarsInputStream) {
        iVersion = `is`.readInt(1, false).toInt()
        cPacketType = `is`.readInt(2, false).toInt()
        iMessageType = `is`.readInt(3, false).toInt()
        iRequestId = `is`.readInt(4, false).toInt()
        sServantName = `is`.readString(5, false)
        sFuncName = `is`.readString(6, false)
        sBuffer = `is`.readBytes(7, false)
        iTimeout = `is`.readInt(8, false).toInt()
        context = `is`.readMapOf<String, String>("", "", 9, false).toMutableMap()
        status = `is`.readMapOf<String, String>("", "", 10, false).toMutableMap()
    }

    override fun deepCopy(): TarsStruct {
        return RequestPacket().also {
            it.iVersion = iVersion
            it.cPacketType = cPacketType
            it.iMessageType = iMessageType
            it.iRequestId = iRequestId
            it.sServantName = sServantName
            it.sFuncName = sFuncName
            it.sBuffer = sBuffer?.copyOf()
            it.iTimeout = iTimeout
            it.context = context?.toMutableMap()
            it.status = status?.toMutableMap()
        }
    }
}
