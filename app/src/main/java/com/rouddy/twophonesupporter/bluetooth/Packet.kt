package com.rouddy.twophonesupporter.bluetooth

data class Packet(val type: PacketType, val data: List<Byte>) {

    enum class PacketType(val rawValue: UShort) {
        CheckVersion(0x00u),
        CheckVersionResponse(0x01u),
        CheckDevice(0x02u),
        CheckDeviceResponse(0x03u),
        Notification(0xf000u),
        ClearDevice(0xffffu),
        ;

        companion object {
            fun valueFor(rawValue: UShort): PacketType? {
                return values().firstOrNull { it.rawValue == rawValue }
            }
        }
    }

    val size: Int
        get() = SizeSize + TypeSize + data.size

    fun toByteArray(): ByteArray {
        return size.toUShort().toByteArray() + type.rawValue.toByteArray() + data.toByteArray()
    }

    companion object {
        internal const val SizeSize = 2
        internal const val TypeSize = 2

        fun initWithData(byteArray: ByteArray): Packet? {
            return byteArray
                .toList()
                .let { bytes ->
                    if (bytes.size < SizeSize) {
                        return@let null
                    }

                    val size = bytes
                        .subList(0, SizeSize)
                        .toByteArray()
                        .toUShort()
                    if (bytes.size >= size.toInt()) {
                        val packetTypeShort = bytes
                            .subList(SizeSize, SizeSize + TypeSize)
                            .toByteArray()
                            .toUShort()
                        val data = bytes.subList(SizeSize + TypeSize, size.toInt())
                        PacketType
                            .valueFor(packetTypeShort)
                            ?.let {
                                Packet(it, data)
                            }
                    } else {
                        null
                    }
                }
        }
    }
}

fun ByteArray.toUShort(): UShort {
    if (size != 2) {
        throw Exception("wrong len")
    }
    return (this[0].toUInt() or (this[1].toUInt() shl 8)).toUShort()
}

fun UShort.toByteArray(): ByteArray {
    return byteArrayOf((toInt() and 0xff).toByte(), ((toInt() shr 8) and 0xff).toByte())
}
