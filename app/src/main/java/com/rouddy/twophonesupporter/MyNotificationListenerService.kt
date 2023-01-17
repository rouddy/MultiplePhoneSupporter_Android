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
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject

class MyNotificationListenerService : NotificationListenerService() {

    inner class ServiceBinder : Binder() {
        fun getService(): MyNotificationListenerService {
            return this@MyNotificationListenerService
        }
    }

    private val binder = ServiceBinder()

    private val gattDelegate = MyGattDelegate()

    private var peripheralDisposable: Disposable? = null

    override fun onCreate() {
        Log.e("$$$", "MyNotificationListenerService::onCreate")
        super.onCreate()

        if (getActAsPeripheralStarted() && peripheralDisposable == null) {
            startBluetooth()
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    override fun onDestroy() {
        Log.e("$$$", "MyNotificationListenerService::onDestroy")
        super.onDestroy()
    }

    fun checkPeripheralStarted(): Single<Boolean> {
        return Single.fromCallable { peripheralDisposable != null }
    }

    fun startPeripheral(): Completable {
        val subject = PublishSubject.create<Any>()
        return subject.ignoreElements()
            .doOnSubscribe {
                startBluetooth(subject)
            }
            .doOnComplete {
                setActAsPeripheralStarted(true)
            }
    }

    private fun startBluetooth(subject: Subject<Any>? = null) {
        if (peripheralDisposable != null) {
            subject?.onComplete()
            return
        }

        Log.e("$$$", "startBluetooth")
        peripheralDisposable = BleGattServiceGenerator
            .startServer(this, gattDelegate)
            .flatMap {
                BleAdvertiser
                    .startAdvertising(this)
            }
            .doFinally {
                peripheralDisposable = null
                Log.e("$$$", "startBluetooth doFinally")
            }
            .subscribe({
                if (subject?.hasComplete() == false) {
                    subject.onComplete()
                }
            }, {
                Log.e("$$$", "start bluetooth error", it)
                if (subject?.hasComplete() == false) {
                    subject.onError(it)
                }
            })
    }

    fun stopPeripheral(): Completable {
        return Completable.fromCallable {
            peripheralDisposable?.dispose()
            setActAsPeripheralStarted(false)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.e("$$$", "NotificationListenerService::onListenerConnected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.e("$$$", "NotificationListenerService::onListenerDisconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        Log.e("$$$", "NotificationListenerService::onNotificationPosted:$sbn")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        Log.e("$$$", "NotificationListenerService::onNotificationRemoved:$sbn")
    }

    private fun setActAsPeripheralStarted(started: Boolean) {
        getSharedPreferences(SHARED_PREFERENCE_NAME, Context.MODE_PRIVATE)
            .edit()
            .apply {
                putBoolean(KEY_ACT_AS_PERIPHERAL_STARTED, started)
            }
            .apply()
    }

    private fun getActAsPeripheralStarted(): Boolean {
        return getSharedPreferences(SHARED_PREFERENCE_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ACT_AS_PERIPHERAL_STARTED, false)
    }

    companion object {
        fun bindService(context: Context): Observable<MyNotificationListenerService> {
            return Rx2ServiceBindingFactory
                .bind<MyNotificationListenerService.ServiceBinder>(
                    context,
                    Intent(context, MyNotificationListenerService::class.java)
                )
                .map { it.getService() }
        }

        private const val SHARED_PREFERENCE_NAME = "MyNotificationListenerServiceSP"
        private const val KEY_ACT_AS_PERIPHERAL_STARTED = "KeyActAsPeripheralStarted"
    }
}