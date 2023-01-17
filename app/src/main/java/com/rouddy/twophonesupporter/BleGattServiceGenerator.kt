package com.rouddy.twophonesupporter

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.util.Log
import com.jakewharton.rxrelay3.PublishRelay
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import java.util.*

object BleGattServiceGenerator {

    enum class NotificationType {
        Notification,
        Indication,
    }

    interface GattDelegate {
        fun getCharacteris(): List<BluetoothGattCharacteristic>
        fun getReadResponse(uuid: UUID): ByteArray
        fun getWriteResponse(uuid: UUID, data: ByteArray): ByteArray
        fun startNotification(notificationType: NotificationType, device: BluetoothDevice, characteristic: BluetoothGattCharacteristic, gattServer: BluetoothGattServer)
        fun stopNotification(uuid: UUID)
        fun getNotificationType(uuid: UUID): NotificationType?
        fun nextNotification()
    }

    @SuppressLint("MissingPermission")
    fun startServer(context: Context, gattDelegate: GattDelegate): Observable<BluetoothGattServer> {
        val gattServerSubject = PublishSubject.create<BluetoothGattServer>()
        var gattServer: BluetoothGattServer? = null

        val responseRely = PublishRelay.create<Any>()
        var disposable: Disposable? = null

        val gattCallback = object : BluetoothGattServerCallback() {
            override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
                super.onServiceAdded(status, service)
                Log.e("$$$", "BluetoothGattServerCallback::onServiceAdded:$status, $service")
                if (status == 0 && !gattServerSubject.hasComplete()) {
                    gattServerSubject.onNext(gattServer!!)
                }
            }

            override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
                super.onConnectionStateChange(device, status, newState)
                Log.e("$$$", "BluetoothGattServerCallback::onConnectionStateChange:$device, $status, $newState")
                if (newState == BluetoothGattServer.STATE_CONNECTED) {
                    if (disposable == null) {
                        disposable = responseRely
                            .concatMapSingle {
                                Single.fromCallable {

                                }
                            }
                            .doFinally {
                                disposable = null
                            }
                            .subscribe({
                                Log.e("$$$", "gatt response")
                            }, {
                                Log.e("$$$", "gatt response error")
                            })
                    }
                } else if (newState == BluetoothGattServer.STATE_DISCONNECTED
                    || newState == BluetoothGattServer.STATE_DISCONNECTING) {
                    disposable?.dispose()
                }
            }

            override fun onCharacteristicReadRequest(device: BluetoothDevice?, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic?) {
                super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
                Log.e("$$$", "BluetoothGattServerCallback::onCharacteristicReadRequest:$device, $requestId")
                val response = gattDelegate.getReadResponse(characteristic!!.uuid)
                gattServer!!.sendResponse(device!!, requestId, BluetoothGatt.GATT_SUCCESS, offset, response)
            }

            override fun onCharacteristicWriteRequest(device: BluetoothDevice?, requestId: Int, characteristic: BluetoothGattCharacteristic?, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
                super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)
                Log.e("$$$", "BluetoothGattServerCallback::onCharacteristicWriteRequest:$device, $requestId")
                val response = gattDelegate.getWriteResponse(characteristic!!.uuid, value!!)
                if (responseNeeded) {
                    gattServer!!.sendResponse(device!!, requestId, BluetoothGatt.GATT_SUCCESS, offset, response)
                }
            }

            override fun onDescriptorReadRequest(device: BluetoothDevice?, requestId: Int, offset: Int, descriptor: BluetoothGattDescriptor?) {
                super.onDescriptorReadRequest(device, requestId, offset, descriptor)
                Log.e("$$$", "BluetoothGattServerCallback::onDescriptorReadRequest:$device, $requestId")
                when (gattDelegate.getNotificationType(descriptor!!.characteristic.uuid)) {
                    NotificationType.Notification -> {
                        gattServer!!.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    }
                    NotificationType.Indication -> {
                        gattServer!!.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                    }
                    null -> {
                        gattServer!!.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
                    }
                }
            }

            override fun onDescriptorWriteRequest(device: BluetoothDevice?, requestId: Int, descriptor: BluetoothGattDescriptor?, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
                super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value)
                Log.e("$$$", "BluetoothGattServerCallback::onDescriptorWriteRequest:$device, $requestId")
                if (value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                    gattDelegate.startNotification(NotificationType.Notification, device!!, descriptor!!.characteristic, gattServer!!)
                } else if (value.contentEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)) {
                    gattDelegate.startNotification(NotificationType.Indication, device!!, descriptor!!.characteristic, gattServer!!)
                } else if (value.contentEquals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
                    gattDelegate.stopNotification(descriptor!!.characteristic.uuid)
                }
                gattServer!!.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }

            override fun onExecuteWrite(device: BluetoothDevice?, requestId: Int, execute: Boolean) {
                super.onExecuteWrite(device, requestId, execute)
                Log.e("$$$", "BluetoothGattServerCallback::onExecuteWrite:$device, $requestId")
            }

            override fun onNotificationSent(device: BluetoothDevice?, status: Int) {
                super.onNotificationSent(device, status)
                Log.e("$$$", "BluetoothGattServerCallback::onNotificationSent:$device, $status")
                gattDelegate.nextNotification()
            }
        }

        return Completable.fromCallable {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            gattServer = bluetoothManager.openGattServer(context, gattCallback)
            Log.e("$$$", "BleGattServiceObservable::openGattServer:$gattServer")
            val gattService = BluetoothGattService(MyGattDelegate.SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
            gattDelegate.getCharacteris()
                .forEach {
                    gattService.addCharacteristic(it)
                }

            gattServer!!.addService(gattService)
            Log.e("$$$", "BleGattServiceObservable addService")
        }
            .subscribeOn(Schedulers.io())
            .andThen(gattServerSubject)
            .doFinally {
                gattServer!!.close()
            }
    }
}