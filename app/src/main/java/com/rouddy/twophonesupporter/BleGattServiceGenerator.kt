package com.rouddy.twophonesupporter

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import com.jakewharton.rxrelay3.BehaviorRelay
import com.rouddy.twophonesupporter.bluetooth.peripheral.MyGattDelegate
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import java.util.*

object BleGattServiceGenerator {

    enum class NotificationType {
        Notification,
        Indication,
    }

    @SuppressLint("MissingPermission")
    abstract class GattDelegate {
        private var connectedDevice: BluetoothDevice? = null
        private val gattServerRelay = BehaviorRelay.create<BluetoothGattServer>()
        private val serviceAddedRelay = BehaviorRelay.create<Any>()
        private val gattServer: BluetoothGattServer
            get() = gattServerRelay.firstOrError().blockingGet()
        protected lateinit var address: String
            private set

        val gattCallback = object : BluetoothGattServerCallback() {
            override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
                super.onServiceAdded(status, service)
                if (status == 0) {
                    serviceAddedRelay.accept(1)
                }
            }

            override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
                super.onConnectionStateChange(device, status, newState)
                if (newState == BluetoothGattServer.STATE_CONNECTED) {
                    gattServer.connect(device!!, false)
                    onConnected(device!!)
                } else if (newState == BluetoothGattServer.STATE_DISCONNECTED
                    || newState == BluetoothGattServer.STATE_DISCONNECTING) {
                    Log.e("$$$", "DISCONNECTED : $newState")
                    onDisconnected()
                }
            }

            override fun onCharacteristicReadRequest(device: BluetoothDevice?, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic?) {
                super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
                val response = getReadResponse(characteristic!!.uuid)
                gattServer.sendResponse(device!!, requestId, BluetoothGatt.GATT_SUCCESS, offset, response)
            }

            override fun onCharacteristicWriteRequest(device: BluetoothDevice?, requestId: Int, characteristic: BluetoothGattCharacteristic?, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
                super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)
                val response = getWriteResponse(characteristic!!.uuid, value!!)
                if (responseNeeded) {
                    gattServer.sendResponse(device!!, requestId, BluetoothGatt.GATT_SUCCESS, offset, response)
                }
            }

            override fun onDescriptorReadRequest(device: BluetoothDevice?, requestId: Int, offset: Int, descriptor: BluetoothGattDescriptor?) {
                super.onDescriptorReadRequest(device, requestId, offset, descriptor)
                when (getNotificationType(descriptor!!.characteristic.uuid)) {
                    NotificationType.Notification -> {
                        gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    }
                    NotificationType.Indication -> {
                        gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                    }
                    null -> {
                        gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
                    }
                }
            }

            override fun onDescriptorWriteRequest(device: BluetoothDevice?, requestId: Int, descriptor: BluetoothGattDescriptor?, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
                super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value)

                val notifyData: (ByteArray) -> Unit = {
                    if (Build.VERSION.SDK_INT >= 33) {
                        gattServer.notifyCharacteristicChanged(device!!, descriptor!!.characteristic, false, it)
                    } else {
                        descriptor!!.characteristic.value = it
                        gattServer.notifyCharacteristicChanged(device!!, descriptor.characteristic, false)
                    }
                }

                if (value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                    startNotification(NotificationType.Notification, device!!, notifyData)
                } else if (value.contentEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)) {
                    startNotification(NotificationType.Indication, device!!, notifyData)
                } else if (value.contentEquals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
                    stopNotification(descriptor!!.characteristic.uuid)
                }
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }

            override fun onExecuteWrite(device: BluetoothDevice?, requestId: Int, execute: Boolean) {
                super.onExecuteWrite(device, requestId, execute)
            }

            override fun onNotificationSent(device: BluetoothDevice?, status: Int) {
                super.onNotificationSent(device, status)
                nextNotification()
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        internal fun initialize(context: Context): Observable<BluetoothGattServer> {
            return Completable.fromCallable {
                val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                address = bluetoothManager.adapter.address
                gattServerRelay.accept(bluetoothManager.openGattServer(context, gattCallback))
                val gattService = BluetoothGattService(MyGattDelegate.SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
                getCharacteris()
                    .forEach {
                        gattService.addCharacteristic(it)
                    }

                gattServer.addService(gattService)
            }
                .andThen(gattServerRelay.zipWith(serviceAddedRelay) { gattServer, _ ->
                    gattServer
                })
                .doFinally {
                    disconnectDevice()
                    gattServer.close()
                }
        }

        fun onConnected(bluetoothDevice: BluetoothDevice) {
            connectedDevice = bluetoothDevice
            onConnected()
        }

        fun onDisconnected() {
            connectedDevice = null
        }

        fun disconnectDevice() {
            connectedDevice?.let {
                gattServer.cancelConnection(it)
            }
            connectedDevice = null
        }

        abstract fun onConnected()
        abstract fun getCharacteris(): List<BluetoothGattCharacteristic>
        abstract fun getReadResponse(uuid: UUID): ByteArray
        abstract fun getWriteResponse(uuid: UUID, data: ByteArray): ByteArray
        abstract fun startNotification(notificationType: NotificationType, device: BluetoothDevice, callback: (ByteArray) -> Unit)
        abstract fun stopNotification(uuid: UUID)
        abstract fun getNotificationType(uuid: UUID): NotificationType?
        abstract fun nextNotification()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun startServer(context: Context, gattDelegate: GattDelegate): Observable<BluetoothGattServer> {
        return gattDelegate.initialize(context)
            .subscribeOn(Schedulers.io())
    }
}