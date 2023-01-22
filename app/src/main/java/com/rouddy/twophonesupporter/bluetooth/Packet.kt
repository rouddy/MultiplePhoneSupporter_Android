package com.rouddy.twophonesupporter.bluetooth

import com.algorigo.library.toByteArray
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class Packet(val type: PacketType, val data: List<Byte>) {

    enum class PacketType(val rawValue: Short) {
        CheckVersion(0x00),
        CheckDevice(0x01),
        CheckOS(0x02),
        Notification(0x10),
        ;

        companion object {
            fun valueFor(rawValue: Short): PacketType? {
                return values().firstOrNull { it.rawValue == rawValue }
            }
        }
    }

    val size: Int
        get() = SizeSize + TypeSize + data.size

    fun toByteArray(): ByteArray {
        return size.toShort().toByteArray() + type.rawValue.toByteArray() + data.toByteArray()
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
                        .toShort()
                    if (bytes.size >= size) {
                        val packetTypeShort = bytes
                            .subList(SizeSize, SizeSize + TypeSize)
                            .toByteArray()
                            .toShort()
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
