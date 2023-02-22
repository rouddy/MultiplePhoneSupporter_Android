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
import com.jakewharton.rxrelay3.ReplayRelay
import com.rouddy.twophonesupporter.BleAdvertiser
import com.rouddy.twophonesupporter.BleGattServiceGenerator
import com.rouddy.twophonesupporter.NotificationData
import com.rouddy.twophonesupporter.ui.MainActivity
import com.rouddy.twophonesupporter.R
import com.rouddy.twophonesupporter.bluetooth.peripheral.MyGattDelegate
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import kotlin.math.roundToInt

class BluetoothService : Service(), MyGattDelegate.Delegate {

    inner class ServiceBinder : Binder() {
        fun getService(): BluetoothService {
            return this@BluetoothService
        }
    }

    enum class PeripheralState {
        Stop,
        Advertising,
        WaitForConnect,
        Connected,
    }

    private class PackageNameFilter(val packageNames: Set<String>) {
        val filterRelay = BehaviorRelay.create<Set<String>>()
            .apply {
                accept(packageNames)
            }

        fun addFilter(packageName: String) {
            filterRelay
                .firstOrError()
                .blockingGet()
                .let {
                    it + setOf(packageName)
                }
                .also {
                    filterRelay.accept(it)
                }
        }

        fun removeFilter(packageName: String) {
            filterRelay
                .firstOrError()
                .blockingGet()
                .let {
                    it.toMutableSet().apply {
                        remove(packageName)
                    }
                }
                .also {
                    filterRelay.accept(it)
                }
        }
    }

    private val binder = ServiceBinder()
    private val gattDelegate = MyGattDelegate(this)
    private lateinit var peripheralName: String
    private var peripheralDisposable: Disposable? = null
    private val peripheralStateRelay = BehaviorRelay.create<PeripheralState>().apply {
        accept(PeripheralState.Stop)
    }
    private lateinit var packageNameFilter: PackageNameFilter
    private val compositeDisposable = CompositeDisposable()
    private val logger: Logger
    private val logRelay = ReplayRelay.createWithSize<String>(255)

    init {
        logger = Logger.getLogger("com.rouddy.twophonesupporter")
        logger.level = Level.ALL
        logger.addHandler(object : Handler() {
            override fun publish(p0: LogRecord?) {
                p0?.message?.also {
                    Log.e("!!!", "publish:$it\n")
                    logRelay.accept(it)
                }
            }

            override fun flush() {

            }

            override fun close() {

            }
        })
    }

    override fun onCreate() {
        Log.e(LOG_TAG, "BluetoothService::onCreate")
        super.onCreate()

        peripheralName = initPeripheralName()
        if (getActAsPeripheralStarted(this) && peripheralDisposable == null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return
            }
            val stored = getSharedPreferences(SHARED_PREFERENCE_NAME, Context.MODE_PRIVATE)
                .getString(KEY_STORED_CENTRAL_DEVICE, null)
            if (stored != null) {
                peripheralStateRelay.accept(PeripheralState.WaitForConnect)
            } else {
                peripheralStateRelay.accept(PeripheralState.Advertising)
            }
            startBluetooth()
        }

        packageNameFilter = PackageNameFilter(getPackageNamesFilter())
        packageNameFilter.filterRelay
            .subscribe({
                setPackageNamesFilter(it)
            })
            .addTo(compositeDisposable)
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
                        val stored = getSharedPreferences(SHARED_PREFERENCE_NAME, Context.MODE_PRIVATE)
                            .getString(KEY_STORED_CENTRAL_DEVICE, null)
                        if (stored != null) {
                            peripheralStateRelay.accept(PeripheralState.WaitForConnect)
                        } else {
                            peripheralStateRelay.accept(PeripheralState.Advertising)
                        }
                        BleAdvertiser.startAdvertising(this, peripheralName)
                    }
                    is BleGattServiceGenerator.State.Connected -> {
                        logger.log("Gatt Connected")
                        peripheralStateRelay.accept(PeripheralState.Connected)
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
        return gattDelegate.sendPacket(Packet(Packet.PacketType.ClearDevice, listOf()))
            .doOnComplete {
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

    fun getPeripheralStateObservable(): Observable<PeripheralState> {
        return peripheralStateRelay
    }

    fun notificationPosted(notificationData: NotificationData): Completable {
        return packageNameFilter.filterRelay
            .firstOrError()
            .flatMapCompletable {
                Log.e("!!!", "key: ${notificationData.packageName} vs package filter:${it.joinToString(",")}")
                if (it.contains(notificationData.packageName)) {
                    Single.fromCallable {
                        Log.e(LOG_TAG, "notificationPosted:${notificationData.key}, ${notificationData.title}, ${notificationData.text}, ${notificationData.subText}, ${notificationData.icon}")
                        val json = JsonObject().apply {
                            addProperty("key", notificationData.key)
                            addProperty("title", notificationData.title)
                            addProperty("text", notificationData.text)
                            addProperty("sub", notificationData.subText)
                            notificationData.icon?.let { ContextCompat.getDrawable(this@BluetoothService, it) }
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
                } else {
                    Completable.complete()
                }
            }
    }

    fun getFilterObservable(): Observable<Set<String>> {
        return packageNameFilter.filterRelay
    }

    fun addFilter(packageName: String) {
        packageNameFilter.addFilter(packageName)
    }

    fun removeFilter(packageName: String) {
        packageNameFilter.removeFilter(packageName)
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

    private fun getPackageNamesFilter(): MutableSet<String> {
        return getSharedPreferences(SHARED_PREFERENCE_NAME, Context.MODE_PRIVATE)
            .getStringSet(
                PACKAGE_NAMES_FILTER_KEY, setOf<String>()
            )
            ?.toMutableSet()
            ?: mutableSetOf()
    }

    private fun setPackageNamesFilter(set: Set<String>) {
        getSharedPreferences(SHARED_PREFERENCE_NAME, Context.MODE_PRIVATE)
            .edit()
            .apply {
                putStringSet(PACKAGE_NAMES_FILTER_KEY, set)
            }
            .apply()
    }

    fun getLogs(): Single<List<String>> {
        return logRelay
            .subscribeOn(Schedulers.io())
            .take(1, TimeUnit.SECONDS)
            .toList()
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
        private const val PACKAGE_NAMES_FILTER_KEY = "PackageNamesFilterKey"

        private const val NOTIFICATION_CHANNEL_ID_BLUETOOTH_SERVICE = "NotificationChannelIdBluetoothService"
        private const val NOTIFICATION_ID_BLUETOOTH_SERVICE = 0x01410
    }
}

fun Logger.log(message: String, exception: Exception = RuntimeException()) {
    exception.stackTrace[1].also {
        log(Level.ALL, "${it.fileName}(${it.lineNumber})\n$message")
    }
}

fun ByteArray.hex(): String {
    return "0x" + joinToString(separator = " ") { String.format("%02x", it) }
}
