package com.rouddy.twophonesupporter.bluetooth

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.algorigo.library.rx.Rx2ServiceBindingFactory
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.rouddy.twophonesupporter.BleAdvertiser
import com.rouddy.twophonesupporter.BleGattServiceGenerator
import com.rouddy.twophonesupporter.MainActivity
import com.rouddy.twophonesupporter.R
import com.rouddy.twophonesupporter.bluetooth.peripheral.MyGattDelegate
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import java.io.ByteArrayOutputStream

class BluetoothService : Service(), MyGattDelegate.Delegate {

    inner class ServiceBinder : Binder() {
        fun getService(): BluetoothService {
            return this@BluetoothService
        }
    }

    private val binder = ServiceBinder()

    private val gattDelegate = MyGattDelegate(this)

    private var peripheralDisposable: Disposable? = null

    override fun onCreate() {
        Log.e("$$$", "BluetoothService::onCreate")
        super.onCreate()

        if (getActAsPeripheralStarted() && peripheralDisposable == null) {
            startBluetooth()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // If the notification supports a direct reply action, use
            // PendingIntent.FLAG_MUTABLE instead.
            val pendingIntent: PendingIntent =
                Intent(this, MainActivity::class.java).let { notificationIntent ->
                    PendingIntent.getActivity(this, 0, notificationIntent,
                        PendingIntent.FLAG_IMMUTABLE)
                }

            val notificationManager = getSystemService(NotificationManager::class.java) as NotificationManager
            notificationManager.createNotificationChannel(NotificationChannel(
                NOTIFICATION_CHANNEL_ID_BLUETOOTH_SERVICE,
                "Bluetooth Service",
                NotificationManager.IMPORTANCE_HIGH
            ))

            val notification: Notification = Notification.Builder(this, NOTIFICATION_CHANNEL_ID_BLUETOOTH_SERVICE)
                .setContentTitle(getText(R.string.bluetooth_service_notification_title))
//                .setContentText(getText(R.string.notification_message))
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setContentIntent(pendingIntent)
//                .setTicker(getText(R.string.ticker_text))
                .build()

            // Notification ID cannot be 0.
            startForeground(NOTIFICATION_ID_BLUETOOTH_SERVICE, notification)
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder
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

        peripheralDisposable = BleGattServiceGenerator
            .startServer(this, gattDelegate)
            .flatMap {
                BleAdvertiser.startAdvertising(this)
            }
            .doFinally {
                peripheralDisposable = null
            }
            .doOnNext {
                if (subject?.hasComplete() == false) {
                    subject.onComplete()
                }
            }
            .doOnError {
                if (subject?.hasComplete() == false) {
                    subject.onError(it)
                }
            }
            .subscribe({
            }, {
                Log.e("$$$", "start bluetooth error", it)
            })
    }

    fun stopPeripheral(): Completable {
        return Completable.fromCallable {
            peripheralDisposable?.dispose()
            setActAsPeripheralStarted(false)
        }
    }

    fun clearPeripheral() {
        getSharedPreferences(SHARED_PREFERENCE_NAME, Context.MODE_PRIVATE)
            .edit()
            .apply {
                remove(KEY_STORED_CENTRAL_DEVICE)
            }
            .apply()
        gattDelegate.disconnectDevice()
    }

    private fun setActAsPeripheralStarted(started: Boolean) {
        clearPeripheral()
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

    fun notificationPosted(key: String, title: String?, text: String?, subText: String?, icon: Int?): Completable {
        return Single.fromCallable {
            Log.e("$$$", "notificationPosted:$key, $title, $text, $subText, $icon")
            val json = JsonObject().apply {
                addProperty("key", key)
                addProperty("title", title)
                addProperty("text", text)
                addProperty("sub", subText)
                icon?.let { ContextCompat.getDrawable(this@BluetoothService, it) }
                    ?.let {
                        val outputStream = ByteArrayOutputStream()
                        it.toBitmap().compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
                        Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
                    }
                    ?.also {
                        addProperty("icon", it)
                    }
            }
            Gson().toJson(json)
        }
            .map {
                Log.e("$$$", "notificationPosted:$it")
                Packet(Packet.PacketType.Notification, it.toByteArray().toList())
            }
            .subscribeOn(Schedulers.computation())
            .flatMapCompletable { gattDelegate.sendPacket(it) }
    }

    override fun checkDeviceUuid(uuid: String): Boolean {
        return getSharedPreferences(SHARED_PREFERENCE_NAME, Context.MODE_PRIVATE)
            .run {
                val stored = getString(KEY_STORED_CENTRAL_DEVICE, null)
                if (stored != null) {
                    stored.compareTo(uuid) == 0
                } else {
                    edit().apply {
                        putString(KEY_STORED_CENTRAL_DEVICE, uuid)
                    }.apply()
                    true
                }
            }
    }

    override fun clearDeviceUuid() {
        getSharedPreferences(SHARED_PREFERENCE_NAME, Context.MODE_PRIVATE)
            .edit()
            .apply {
                remove(KEY_STORED_CENTRAL_DEVICE)
            }
            .apply()
    }

    companion object {
        fun bindService(context: Context): Observable<BluetoothService> {
            return Rx2ServiceBindingFactory
                .bind<ServiceBinder>(
                    context,
                    Intent(context, BluetoothService::class.java)
                )
                .map { it.getService() }
        }

        private const val SHARED_PREFERENCE_NAME = "BluetoothServiceSP"
        private const val KEY_ACT_AS_PERIPHERAL_STARTED = "KeyActAsPeripheralStarted"
        private const val KEY_STORED_CENTRAL_DEVICE = "KeyStoredCentralDevice"

        private const val NOTIFICATION_CHANNEL_ID_BLUETOOTH_SERVICE = "NotificationChannelIdBluetoothService"
        private const val NOTIFICATION_ID_BLUETOOTH_SERVICE = 0x01410
    }
}