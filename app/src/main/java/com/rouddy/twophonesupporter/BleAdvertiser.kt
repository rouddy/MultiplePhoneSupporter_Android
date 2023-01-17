package com.rouddy.twophonesupporter

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.ActivityCompat
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject

object BleAdvertiser {

    fun startAdvertising(context: Context): Observable<BluetoothLeAdvertiser> {
        val connectedSubject = BehaviorSubject.create<BluetoothLeAdvertiser>()
        var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null

        val advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                super.onStartSuccess(settingsInEffect)
                Log.e("$$$", "AdvertiseCallback::onStartSuccess")
                connectedSubject.onNext(bluetoothLeAdvertiser!!)
            }

            override fun onStartFailure(errorCode: Int) {
                super.onStartFailure(errorCode)
                Log.e("$$$", "AdvertiseCallback::onStartFailure:$errorCode")
                connectedSubject.onError(RuntimeException("Failure:$errorCode"))
            }
        }

        return Completable.fromCallable {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                throw RuntimeException("Permission")
            }

            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val bluetoothAdapter = bluetoothManager.adapter

//            val isNameChanged = bluetoothAdapter.setName("Test")
//            Log.e("$$$", "BleAdvertiseObservable::isNameChanged:$isNameChanged")

            bluetoothLeAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
            val settings = AdvertiseSettings
                .Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build()

            val data = AdvertiseData
                .Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(ParcelUuid(MyGattDelegate.SERVICE_UUID))
                .build()

            bluetoothLeAdvertiser!!.startAdvertising(settings, data, advertiseCallback)
            Log.e("$$$", "BleAdvertiseObservable startAdvertising")
        }
            .subscribeOn(Schedulers.io())
            .andThen(connectedSubject)
            .doFinally {
                bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
            }
    }

}