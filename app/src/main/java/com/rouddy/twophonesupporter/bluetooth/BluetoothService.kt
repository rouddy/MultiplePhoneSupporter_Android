package com.rouddy.twophonesupporter.bluetooth

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Base64
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.algorigo.library.rx.Rx2ServiceBindingFactory
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.jakewharton.rxrelay3.BehaviorRelay
import com.rouddy.twophonesupporter.BleAdvertiser
import com.rouddy.twophonesupporter.BleGattServiceGenerator
import com.rouddy.twophonesupporter.ui.MainActivity
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
import kotlin.math.roundToInt

class BluetoothService : Service(), MyGattDelegate.Delegate {

    inner class ServiceBinder : Binder() {
        fun getService(): BluetoothService {
            return this@BluetoothService
        }
    }

    private val binder = ServiceBinder()
    private val gattDelegate = MyGattDelegate(this)
    private lateinit var peripheralName: String
    private var peripheralDisposable: Disposable? = null
    private val peripheralConnectedRelay = BehaviorRelay.create<Boolean>().apply {
        accept(false)
    }

    override fun onCreate() {
        Log.e(LOG_TAG, "BluetoothService::onCreate")
        super.onCreate()

        peripheralName = initPeripheralName()
        if (getActAsPeripheralStarted(this) && peripheralDisposable == null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return
            }
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

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
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

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun startBluetooth(subject: Subject<Any>? = null) {
        if (peripheralDisposable != null) {
            subject?.onComplete()
            return
        }

        peripheralDisposable = BleGattServiceGenerator
            .startServer(this, gattDelegate)
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
            .switchMap {
                when (it) {
                    is BleGattServiceGenerator.State.WaitForConnect -> {
                        peripheralConnectedRelay.accept(false)
                        BleAdvertiser.startAdvertising(this, peripheralName)
                    }
                    is BleGattServiceGenerator.State.Connected -> {
                        peripheralConnectedRelay.accept(true)
                        Observable.empty()
                    }
                    else -> {
                        Observable.error(IllegalStateException("state is wrong"))
                    }
                }
            }
            .doFinally {
                peripheralDisposable = null
            }
            .subscribe({
            }, {
                Log.e(LOG_TAG, "start bluetooth error", it)
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
    }

    fun getPeripheralConnectedObservable(): Observable<Boolean> {
        return peripheralConnectedRelay
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

    private fun initPeripheralName(): String {
        return getSharedPreferences(SHARED_PREFERENCE_NAME, MODE_PRIVATE).run {
            val name = getString(KEY_PERIPHERAL_NAME, null)
            if (name != null) {
                return@run name
            }
            val generated = (Math.random() * 16777215).roundToInt()
            val byteArray = byteArrayOf(generated.toByte(), (generated shr 1).toByte(), (generated shr 2).toByte())
            return Base64.encodeToString(byteArray, Base64.NO_PADDING or Base64.NO_WRAP).also {
                edit().apply {
                    putString(KEY_PERIPHERAL_NAME, it)
                }.apply()
            }
        }
    }

    fun notificationPosted(key: String, title: String?, text: String?, subText: String?, icon: Int?): Completable {
        return Single.fromCallable {
            Log.e(LOG_TAG, "notificationPosted:$key, $title, $text, $subText, $icon")
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
        private val LOG_TAG = BluetoothService::class.java.simpleName

        fun bindService(context: Context): Observable<BluetoothService> {
            return Rx2ServiceBindingFactory
                .bind<ServiceBinder>(
                    context,
                    Intent(context, BluetoothService::class.java)
                )
                .map { it.getService() }
        }

        fun getActAsPeripheralStarted(context: Context): Boolean {
            return context.getSharedPreferences(SHARED_PREFERENCE_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ACT_AS_PERIPHERAL_STARTED, false)
        }

        private const val SHARED_PREFERENCE_NAME = "BluetoothServiceSP"
        private const val KEY_ACT_AS_PERIPHERAL_STARTED = "KeyActAsPeripheralStarted"
        private const val KEY_STORED_CENTRAL_DEVICE = "KeyStoredCentralDevice"
        private const val KEY_PERIPHERAL_NAME = "KeyPeripheralName"

        private const val NOTIFICATION_CHANNEL_ID_BLUETOOTH_SERVICE = "NotificationChannelIdBluetoothService"
        private const val NOTIFICATION_ID_BLUETOOTH_SERVICE = 0x01410
    }
}