package com.rouddy.twophonesupporter

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import com.rouddy.twophonesupporter.databinding.ActivityMainBinding
import com.tbruyelle.rxpermissions3.RxPermissions
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val compositeDisposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Intent(this, BluetoothService::class.java).also {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(it)
            } else {
                startService(it)
            }
        }

        BluetoothService.bindService(this)
            .flatMapSingle {
                it.checkPeripheralStarted()
            }
            .firstOrError()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                Log.e("$$$", "peripheral success $it")
                binding.actAsPeripheralBtn.isSelected = it
            }, {
                Log.e("$$$", "peripheral check", it)
            })
            .addTo(compositeDisposable)

        binding.actAsPeripheralBtn.setOnClickListener {
            if (!checkNotificationPermissionGrantred()) {
                startNotificationSettingActivity()
                return@setOnClickListener
            }

            checkPeripheralPermission()
                .andThen(BluetoothService.bindService(this))
                .flatMapSingle { service ->
                    service.checkPeripheralStarted()
                        .flatMap {
                            if (it) {
                                service.stopPeripheral()
                            } else {
                                service.startPeripheral()
                            }
                                .toSingleDefault(!it)
                        }
                }
                .firstOrError()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    Log.e("$$$", "${if (it) "start" else "stop"} peripheral complete")
                    binding.actAsPeripheralBtn.isSelected = it
                }, {
                    Log.e("$$$", "peripheral error", it)
                })
                .addTo(compositeDisposable)
        }
        binding.actAsCentralBtn.setOnClickListener {
            if (!checkNotificationPermissionGrantred()) {
                startNotificationSettingActivity()
                return@setOnClickListener
            }

        }
    }

    private fun checkNotificationPermissionGrantred(): Boolean {
        val sets = NotificationManagerCompat.getEnabledListenerPackages(this)
        Log.e("$$$", "enabled:${sets.joinToString(",")}")
        return sets.contains(packageName)
    }

    private fun startNotificationSettingActivity() {
        val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        startActivity(intent)
    }

    private fun checkPeripheralPermission() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        RxPermissions(this)
            .request(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT)
            .doOnNext {
                if (!it) {
                    throw IllegalStateException("Permissions Error")
                }
            }
            .firstOrError()
            .ignoreElement()
    } else {
        Completable.complete()
    }
}
