package com.rouddy.twophonesupporter.bluetooth.peripheral

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.util.Log
import com.algorigo.library.toInt
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.jakewharton.rxrelay3.BehaviorRelay
import com.jakewharton.rxrelay3.PublishRelay
import com.rouddy.twophonesupporter.BleGattServiceGenerator
import com.rouddy.twophonesupporter.bluetooth.Packet
import com.rouddy.twophonesupporter.bluetooth.data.CheckDeviceReceivedData
import com.rouddy.twophonesupporter.bluetooth.hex
import com.rouddy.twophonesupporter.bluetooth.log
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.schedulers.Schedulers
import java.nio.ByteOrder
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

@SuppressLint("MissingPermission")
class MyGattDelegate(private val delegate: Delegate) : BleGattServiceGenerator.GattDelegate() {

    enum class OperatingSystem(val byte: Byte) {
        Android(0x00),
        iOS(0x01),
        ;

        companion object {
            fun valueFor(byte: Byte): OperatingSystem? {
                return values().firstOrNull { it.byte == byte }
            }
        }
    }

    interface Delegate {
        fun checkDeviceUuid(uuid: String): Boolean
        fun clearDeviceUuid()
    }

    private var notificationDisposable: Disposable? = null
    private var nextNotificationRelay: BehaviorRelay<Any>? = null
    private val compositeDisposable = CompositeDisposable()
    private lateinit var receivedDataRelay: BehaviorRelay<ByteArray>
    private val receivedPacketRelay = PublishRelay.create<Packet>()
    private val sendPacketRelay = PublishRelay.create<Packet>()
    private var operatingSystem: OperatingSystem? = null
    private val logger: Logger

    init {
        logger = Logger.getLogger("com.rouddy.twophonesupporter")
    }

    override fun onConnected(bluetoothDevice: BluetoothDevice) {
        super.onConnected(bluetoothDevice)
        receivedDataRelay = BehaviorRelay
            .create<ByteArray>()
            .apply {
                accept(byteArrayOf())
            }
        receivedDataRelay
            .scan { t1, t2 ->
                (t1 + t2).let { bytes ->
                    var byteArray = bytes
                    while (true) {
                        val packet = Packet.initWithData(byteArray)
                        if (packet == null) {
                            break
                        }
                        logger.log("Received Packet : ${packet.size} : ${packet.type.name} : ${byteArray.sliceArray(0 until packet.size).hex()}")
                        receivedPacketRelay.accept(packet)
                        byteArray = byteArray.sliceArray(packet.size until byteArray.size)
                    }
                    byteArray
                }
            }
            .subscribe({
                Log.e(LOG_TAG, "received data relay ${it.joinToString(separator = "") { String.format("%02x", it) }}")
                if (it.size > 0) {
                    logger.log("Received remnants : ${it.size} : ${it.hex()}")
                }
            }, {
                Log.e(LOG_TAG, "received data relay error", it)
            })
            .addTo(compositeDisposable)

        receivedPacketRelay
            .flatMapCompletable {
                Completable.fromCallable {
                    processPacket(it)
                }
            }
            .subscribe({
            }, {

            })
            .addTo(compositeDisposable)
    }

    override fun onDisconnected() {
        super.onDisconnected()
        compositeDisposable.clear()
        notificationDisposable?.dispose()
    }

    override fun getCharacteris(): List<BluetoothGattCharacteristic> {
        return listOf(
            BluetoothGattCharacteristic(
                WRITE_CHARACTERISTIC_UUID,
                        BluetoothGattCharacteristic.PROPERTY_WRITE,
                        BluetoothGattCharacteristic.PERMISSION_WRITE
            ),
            BluetoothGattCharacteristic(
                NOTIFY_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            ).apply {
                addDescriptor(BluetoothGattDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"), BluetoothGattCharacteristic.PERMISSION_WRITE))
            },
        )
    }

    override fun getReadResponse(uuid: UUID): ByteArray {
        return byteArrayOf(0x01, 0x02)
    }

    override fun getWriteResponse(uuid: UUID, data: ByteArray): ByteArray {
        receivedDataRelay.accept(data)
        return data
    }

    override fun startNotification(notificationType: BleGattServiceGenerator.NotificationType, device: BluetoothDevice, callback: (ByteArray) -> Unit) {
        if (notificationDisposable != null) {
            return
        }

        val relay = BehaviorRelay
            .create<Any>()
            .apply { accept(1) }
        nextNotificationRelay = relay
        notificationDisposable = sendPacketRelay
            .doOnNext {
                logger.log("Send Packet : ${it.size} : ${it.type.name} : ${it.toByteArray().hex()}")
            }
            .map { it.toByteArray() }
            .zipWith(relay) { byteArray, _ ->
                byteArray
            }
            .subscribeOn(Schedulers.computation())
            .doFinally {
                notificationDisposable = null
                nextNotificationRelay = null
            }
            .subscribe({
                callback.invoke(it)
            }, {
                Log.e(LOG_TAG, "notification error", it)
            })
    }

    override fun stopNotification(uuid: UUID) {
        notificationDisposable?.dispose()
    }

    override fun getNotificationType(uuid: UUID): BleGattServiceGenerator.NotificationType? {
        return if (notificationDisposable != null) {
            BleGattServiceGenerator.NotificationType.Notification
        } else {
            null
        }
    }

    override fun nextNotification() {
        nextNotificationRelay?.accept(1)
    }

    private fun processPacket(packet: Packet) {
        Log.e(LOG_TAG, "processPacket:${packet.type}:${packet.data.joinToString("") { String.format("%02x", it) }}")
        when (packet.type) {
            Packet.PacketType.CheckVersion -> processVersion(packet.data)
            Packet.PacketType.CheckDevice -> processCheckDevice(packet.data)
            Packet.PacketType.ClearDevice -> processClearDevice()
            else -> {

            }
        }
    }

    private fun processVersion(data: List<Byte>) {
        val centralVersion = data.toByteArray().toInt(byteOrder = ByteOrder.LITTLE_ENDIAN)
        Log.e(LOG_TAG, "centralVersion:$centralVersion")
        val json = JsonObject().apply {
            addProperty("versionOk", centralVersion == VERSION)
            addProperty("peripheralId", address)
        }
        val returnPacket = Packet(Packet.PacketType.CheckVersion, Gson().toJson(json).toByteArray().toList())
        sendPacketRelay.accept(returnPacket)
    }

    private fun processCheckDevice(data: List<Byte>) {
        val receivedString = String(data.toByteArray())
        val receivedJson = Gson().fromJson(receivedString, CheckDeviceReceivedData::class.java)
        val receivedUuid = receivedJson.uuid
        val certificated = delegate.checkDeviceUuid(receivedUuid)
        operatingSystem = OperatingSystem.valueFor(receivedJson.os)
        val json = JsonObject().apply {
            addProperty("vaildDevice", certificated)
            addProperty("os", OperatingSystem.Android.byte)
        }
        val returnPacket = Packet(Packet.PacketType.CheckDevice, Gson().toJson(json).toByteArray().toList())
        sendPacketRelay.accept(returnPacket)
        if (!certificated) {
            Completable.timer(1, TimeUnit.SECONDS)
                .subscribeOn(Schedulers.io())
                .subscribe({
                    disconnectDevice()
                }, {
                    Log.e(LOG_TAG, "timer error", it)
                })
        }
    }

    private fun processClearDevice() {
        val returnPacket = Packet(Packet.PacketType.ClearDevice, listOf())
        sendPacketRelay.accept(returnPacket)
        delegate.clearDeviceUuid()
        Completable.timer(1, TimeUnit.SECONDS)
            .subscribeOn(Schedulers.io())
            .subscribe({
                disconnectDevice()
            }, {
                Log.e(LOG_TAG, "timer error", it)
            })
    }

    fun sendPacket(packet: Packet): Completable {
        return Completable.fromCallable {
            sendPacketRelay.accept(packet)
        }
    }

    companion object {
        private val LOG_TAG = MyGattDelegate::class.java.simpleName

        internal val VERSION = 0x01

        internal val SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-0123456789AB")
        internal val WRITE_CHARACTERISTIC_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-0123456789AB")
        internal val NOTIFY_CHARACTERISTIC_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-0123456789AB")

        internal val UUID_DEVICE_INFO_SERVICE = UUID.fromString("0000180A-0000-1000-8000-00805F9B34FB")
        internal val UUID_MANUFACTURER_NAME = UUID.fromString("00002A29-0000-1000-8000-00805F9B34FB")
        internal val UUID_HARDWARE_REVISION = UUID.fromString("00002A27-0000-1000-8000-00805F9B34FB")
        internal val UUID_FIRMWARE_REVISION = UUID.fromString("00002A26-0000-1000-8000-00805F9B34FB")

        internal val UUID_GENERIC_ACCESS_SERVICE = UUID.fromString("00001800-0000-1000-8000-00805F9B34FB")
        internal val UUID_DEVICE_NAME = UUID.fromString("00002A00-0000-1000-8000-00805F9B34FB")
        internal val UUID_APPEARANCE = UUID.fromString("00002A01-0000-1000-8000-00805F9B34FB")
        internal val UUID_PERIPHERAL_PREFERRED = UUID.fromString("00002A04-0000-1000-8000-00805F9B34FB")
        internal val UUID_CENTRAL_ADDRESS_RESOL = UUID.fromString("00002AA6-0000-1000-8000-00805F9B34FB")

        internal val UUID_GENERIC_ATTRIBUTE_SERVICE = UUID.fromString("00001801-0000-1000-8000-00805F9B34FB")
        internal val UUID_SERVICE_CHANGED = UUID.fromString("00002A05-0000-1000-8000-00805F9B34FB")

        internal val UUID_BOND_MANAGEMENT_SERVICE = UUID.fromString("0000181E-0000-1000-8000-00805F9B34FB")
        internal val UUID_BOND_MANAGEMENT_FEATURE = UUID.fromString("00002AA5-0000-1000-8000-00805F9B34FB")
        internal val UUID_BOND_MANAGEMENT_CONTROL = UUID.fromString("00002AA4-0000-1000-8000-00805F9B34FB")

        internal val UUID_BATTERY_SERVICE = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")
        internal val UUID_BATTERY_LEVEL = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")
    }
}
