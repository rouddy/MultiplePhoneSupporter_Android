package com.rouddy.twophonesupporter

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.util.Log
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import java.util.*

object BleGattServiceGenerator {

    interface GattDelegate {
        fun getReadResponse(uuid: UUID): ByteArray
        fun getWriteResponse(uuid: UUID, data: ByteArray): ByteArray
    }

    @SuppressLint("MissingPermission")
    fun startServer(context: Context, gattDelegate: GattDelegate): Observable<BluetoothGattServer> {
        val gattServerSubject = PublishSubject.create<BluetoothGattServer>()
        var gattServer: BluetoothGattServer? = null

        val gattCallback = object : BluetoothGattServerCallback() {
            override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
                super.onServiceAdded(status, service)
                Log.e("!!!", "BluetoothGattServerCallback::onServiceAdded:$status, $service")
                if (status == 0 && !gattServerSubject.hasComplete()) {
                    gattServerSubject.onNext(gattServer!!)
                }
            }

            override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
                super.onConnectionStateChange(device, status, newState)
                Log.e("!!!", "BluetoothGattServerCallback::onConnectionStateChange:$device, $status, $newState")
            }

            override fun onCharacteristicReadRequest(device: BluetoothDevice?, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic?) {
                super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
                Log.e("!!!", "BluetoothGattServerCallback::onCharacteristicReadRequest:$device, $requestId")
                val response = gattDelegate.getReadResponse(characteristic!!.uuid)
                gattServer!!.sendResponse(device!!, requestId, BluetoothGatt.GATT_SUCCESS, offset, response)
            }

            override fun onCharacteristicWriteRequest(device: BluetoothDevice?, requestId: Int, characteristic: BluetoothGattCharacteristic?, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
                super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)
                Log.e("!!!", "BluetoothGattServerCallback::onCharacteristicWriteRequest:$device, $requestId")
                val response = gattDelegate.getWriteResponse(characteristic!!.uuid, value!!)
                if (responseNeeded) {
                    gattServer!!.sendResponse(device!!, requestId, BluetoothGatt.GATT_SUCCESS, offset, response)
                }
            }

            override fun onDescriptorReadRequest(device: BluetoothDevice?, requestId: Int, offset: Int, descriptor: BluetoothGattDescriptor?) {
                super.onDescriptorReadRequest(device, requestId, offset, descriptor)
                Log.e("!!!", "BluetoothGattServerCallback::onDescriptorReadRequest:$device, $requestId")
            }

            override fun onDescriptorWriteRequest(device: BluetoothDevice?, requestId: Int, descriptor: BluetoothGattDescriptor?, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
                super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value)
                Log.e("!!!", "BluetoothGattServerCallback::onDescriptorWriteRequest:$device, $requestId")
                if (value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {

                } else if (value.contentEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)) {

                } else if (value.contentEquals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {

                }
            }

            override fun onExecuteWrite(device: BluetoothDevice?, requestId: Int, execute: Boolean) {
                super.onExecuteWrite(device, requestId, execute)
                Log.e("!!!", "BluetoothGattServerCallback::onExecuteWrite:$device, $requestId")
            }

            override fun onNotificationSent(device: BluetoothDevice?, status: Int) {
                super.onNotificationSent(device, status)
                Log.e("!!!", "BluetoothGattServerCallback::onNotificationSent:$device, $status")
            }
        }

        return Completable.fromCallable {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            gattServer = bluetoothManager.openGattServer(context, gattCallback)
            Log.e("!!!", "BleGattServiceObservable::openGattServer:$gattServer")
            val gattService = BluetoothGattService(MyNotificationListenerService.SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
            val characteristic = BluetoothGattCharacteristic(
                MyNotificationListenerService.CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE or
                        BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
            )
            characteristic.addDescriptor(BluetoothGattDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"), BluetoothGattCharacteristic.PERMISSION_WRITE))
            gattService.addCharacteristic(characteristic)

            gattServer!!.addService(gattService)
            Log.e("!!!", "BleGattServiceObservable addService")
        }
            .subscribeOn(Schedulers.io())
            .andThen(gattServerSubject)
            .doFinally {
                gattServer!!.close()
            }
    }
}