package com.rouddy.twophonesupporter

import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.algorigo.library.rx.Rx2ServiceBindingFactory
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import java.util.*

class MyNotificationListenerService : NotificationListenerService() {

    inner class ServiceBinder : Binder() {
        fun getService(): MyNotificationListenerService {
            return this@MyNotificationListenerService
        }
    }

    private val binder = ServiceBinder()
    private var disposable: Disposable? = null

    private val gattDelegate = object : BleGattServiceGenerator.GattDelegate {
        override fun getReadResponse(uuid: UUID): ByteArray {
            return byteArrayOf(0x01, 0x02)
        }

        override fun getWriteResponse(uuid: UUID, data: ByteArray): ByteArray {
            return data
        }
    }

    override fun onCreate() {
        Log.e("!!!", "MyNotificationListenerService::onCreate")
        super.onCreate()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    override fun onDestroy() {
        Log.e("!!!", "MyNotificationListenerService::onDestroy")
        super.onDestroy()
    }

    fun start(): Completable {
        val subject = PublishSubject.create<Any>()
        return subject.ignoreElements()
            .doOnSubscribe {
                startBluetooth(subject)
            }
    }

    private fun startBluetooth(subject: Subject<Any>) {
        if (disposable != null) {
            subject.onComplete()
            return
        }

        disposable = BleGattServiceGenerator
            .startServer(this, gattDelegate)
            .flatMap {
                BleAdvertiser
                    .startAdvertising(this)
            }
            .doFinally {
                disposable = null
            }
            .subscribe({
                if (!subject.hasComplete()) {
                    subject.onComplete()
                }
            }, {
                Log.e("!!!", "start bluetooth error", it)
                if (!subject.hasComplete()) {
                    subject.onError(it)
                }
            })
    }

    fun stop(): Completable {
        return Completable.fromCallable {
            disposable?.dispose()
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.e("!!!", "NotificationListenerService::onListenerConnected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.e("!!!", "NotificationListenerService::onListenerDisconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        Log.e("!!!", "NotificationListenerService::onNotificationPosted:$sbn")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        Log.e("!!!", "NotificationListenerService::onNotificationRemoved:$sbn")
    }

    companion object {
        internal val SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        internal val CHARACTERISTIC_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")

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

        fun bindService(context: Context): Observable<MyNotificationListenerService> {
            return Rx2ServiceBindingFactory
                .bind<MyNotificationListenerService.ServiceBinder>(
                    context,
                    Intent(context, MyNotificationListenerService::class.java)
                )
                .map { it.getService() }
        }
    }
}