package com.rouddy.twophonesupporter.bluetooth.peripheral

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.os.Build
import android.util.Log
import com.jakewharton.rxrelay3.BehaviorRelay
import com.jakewharton.rxrelay3.PublishRelay
import com.rouddy.twophonesupporter.BleGattServiceGenerator
import com.rouddy.twophonesupporter.bluetooth.Packet
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.*

@SuppressLint("MissingPermission")
class MyGattDelegate : BleGattServiceGenerator.GattDelegate {

    private var notificationDisposable: Disposable? = null
    private var nextNotificationRelay: BehaviorRelay<Any>? = null
    private val compositeDisposable = CompositeDisposable()
    private lateinit var receivedDataRelay: BehaviorRelay<ByteArray>
    private val receivedPacketRelay = PublishRelay.create<Packet>()
    private val notificationRelay = PublishRelay.create<ByteArray>()

    override fun onConnected() {
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
                        receivedPacketRelay.accept(packet)
                        byteArray = byteArray.sliceArray(packet.size until packet.size)
                    }
                    byteArray
                }
            }
            .subscribe({
                Log.e("$$$", "received data relay $it")
            }, {
                Log.e("$$$", "received data relay error", it)
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

    override fun startNotification(notificationType: BleGattServiceGenerator.NotificationType, device: BluetoothDevice, characteristic: BluetoothGattCharacteristic, gattServer: BluetoothGattServer) {
        val relay = BehaviorRelay
            .create<Any>()
            .apply { accept(1) }
        nextNotificationRelay = relay
        notificationDisposable = notificationRelay
            .zipWith(relay) { byteArray, _ ->
                byteArray
            }
            .subscribeOn(Schedulers.computation())
            .doFinally {
                notificationDisposable = null
                nextNotificationRelay = null
            }
            .subscribe({
                if (Build.VERSION.SDK_INT >= 33) {
                    gattServer.notifyCharacteristicChanged(device, characteristic, false, it)
                } else {
                    characteristic.value = it
                    gattServer.notifyCharacteristicChanged(device, characteristic, false)
                }
            }, {
                Log.e("$$$", "notification error", it)
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
        when (packet.type) {
            Packet.PacketType.CheckDevice -> processCheckDevice(packet.data)
            else -> {

            }
        }
    }

    private fun processCheckDevice(data: List<Byte>) {
        val centralUuid = String(data.toByteArray())
        val certificated = if (true) {
            0x01.toByte()
        } else {
            0x00.toByte()
        }
        val returnPacket = Packet(Packet.PacketType.CheckDevice, listOf(certificated))
        notificationRelay.accept(returnPacket.toByteArray())
    }

    fun sendPacket(packet: Packet): Completable {
        return Completable.fromCallable {
            notificationRelay.accept(packet.toByteArray())
        }
    }

    companion object {
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
