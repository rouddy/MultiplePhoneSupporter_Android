package com.rouddy.twophonesupporter

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.os.Build
import android.util.Log
import com.algorigo.library.toByteArray
import com.jakewharton.rxrelay3.BehaviorRelay
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.*
import java.util.concurrent.TimeUnit

@SuppressLint("MissingPermission")
class MyGattDelegate : BleGattServiceGenerator.GattDelegate {

    private var disposable: Disposable? = null
    private var notificationRelay = BehaviorRelay.create<Any>().apply { accept(1) }

    override fun getCharacteris(): List<BluetoothGattCharacteristic> {
        return listOf(
            BluetoothGattCharacteristic(
                CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ or
                        BluetoothGattCharacteristic.PROPERTY_WRITE or
                        BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ or
                        BluetoothGattCharacteristic.PERMISSION_WRITE
            ).apply {
                addDescriptor(BluetoothGattDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"), BluetoothGattCharacteristic.PERMISSION_WRITE))
            }
        )
    }

    override fun getReadResponse(uuid: UUID): ByteArray {
        return byteArrayOf(0x01, 0x02)
    }

    override fun getWriteResponse(uuid: UUID, data: ByteArray): ByteArray {
        return data
    }

    override fun startNotification(notificationType: BleGattServiceGenerator.NotificationType, device: BluetoothDevice, characteristic: BluetoothGattCharacteristic, gattServer: BluetoothGattServer) {
        disposable = Observable.interval(5, TimeUnit.SECONDS)
            .sample(notificationRelay)
            .subscribeOn(Schedulers.computation())
            .doFinally { disposable = null }
            .subscribe({
                if (Build.VERSION.SDK_INT >= 33) {
                    gattServer.notifyCharacteristicChanged(device, characteristic, false, it.toByteArray())
                } else {
                    characteristic.value = it.toByteArray()
                    gattServer.notifyCharacteristicChanged(device, characteristic, false)
                }
            }, {
                Log.e("$$$", "notification error", it)
            })
    }

    override fun stopNotification(uuid: UUID) {
        disposable?.dispose()
    }

    override fun getNotificationType(uuid: UUID): BleGattServiceGenerator.NotificationType? {
        return if (disposable != null) {
            BleGattServiceGenerator.NotificationType.Notification
        } else {
            null
        }
    }

    override fun nextNotification() {
        notificationRelay.accept(1)
    }

    companion object {
        internal val SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-0123456789AB")
        internal val CHARACTERISTIC_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-0123456789AB")

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
