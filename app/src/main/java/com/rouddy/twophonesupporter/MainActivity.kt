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
import io.reactivex.rxjava3.core.Completable

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.checkButton.setOnClickListener {
            if (!checkNotificationPermissionGrantred()) {
                val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                startActivity(intent)
            }
        }

        binding.startButton.setOnClickListener {
            checkPermission()
                .andThen(MyNotificationListenerService.bindService(this))
                .flatMapFirstCompletable {
                    it.start()
                }
                .subscribe({
                    Log.e("!!!", "start complete")
                }, {
                    Log.e("!!!", "start error", it)
                })
        }

        binding.stopButton.setOnClickListener {
            checkPermission()
                .andThen(MyNotificationListenerService.bindService(this))
                .flatMapFirstCompletable {
                    it.stop()
                }
                .subscribe({
                    Log.e("!!!", "stop complete")
                }, {
                    Log.e("!!!", "stop error", it)
                })
        }
    }

    private fun checkNotificationPermissionGrantred(): Boolean {
        val sets = NotificationManagerCompat.getEnabledListenerPackages(this)
        Log.e("!!!", "enabled:${sets.joinToString(",")}")
        return sets.contains(packageName)
    }

    private fun checkPermission() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
