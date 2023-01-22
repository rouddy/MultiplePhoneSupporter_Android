package com.rouddy.twophonesupporter.bluetooth

import com.algorigo.library.toByteArray
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class Packet(val type: PacketType, val data: List<Byte>) {

    enum class PacketType(val rawValue: Short) {
        CheckVersion(0x00),
        CheckDevice(0x01),
        Notification(0x10),
        ;

        companion object {
            fun valueFor(rawValue: Short): PacketType? {
                return values().firstOrNull { it.rawValue == rawValue }
            }
        }
    }

    val size: Int
        get() = data.size + 6

    fun toByteArray(): ByteArray {
        return size.toByteArray(byteOrder = ByteOrder.LITTLE_ENDIAN) + type.rawValue.toByteArray() + data.toByteArray()
    }

    companion object {
        fun initWithData(byteArray: ByteArray): Packet? {
            return byteArray
                .toList()
                .let { bytes ->
                    if (bytes.size < 4) {
                        return@let null
                    }

                    val size = bytes
                        .subList(0, 4)
                        .toByteArray()
                        .toInt()
                    if (bytes.size >= size) {
                        val packetTypeShort = bytes
                            .subList(4, 6)
                            .toByteArray()
                            .toShort()
                        val data = bytes.subList(6, size)
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

fun ByteArray.toInt(): Int {
    if (size != 4) {
        throw Exception("wrong len")
    }
    reverse()
    return ByteBuffer.wrap(this).int
}

fun ByteArray.toShort(): Short {
    if (size != 2) {
        throw Exception("wrong len")
    }
    reverse()
    return ByteBuffer.wrap(this).short
}

fun Short.toByteArray(): ByteArray {
    return byteArrayOf((toInt() and 0xff).toByte(), ((toInt() shr 8) and 0xff).toByte())
}
