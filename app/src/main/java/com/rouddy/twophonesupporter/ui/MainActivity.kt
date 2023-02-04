package com.rouddy.twophonesupporter.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import com.rouddy.twophonesupporter.bluetooth.BluetoothService
import com.rouddy.twophonesupporter.databinding.ActivityMainBinding
import io.reactivex.rxjava3.disposables.CompositeDisposable

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

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

        if (!BluetoothService.getActAsPeripheralStarted(this)) {
            Intent(this, InitialSettingActivity::class.java).also {
                startActivity(it)
            }
        }

        binding.notificationRelayStartBtn.setOnClickListener {
            if (!checkNotificationPermissionGrantred()) {
                startNotificationSettingActivity()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.notificationRelayStartBtn.isEnabled = !checkNotificationPermissionGrantred()
    }

    private fun checkNotificationPermissionGrantred(): Boolean {
        val sets = NotificationManagerCompat.getEnabledListenerPackages(this)
        return sets.contains(packageName)
    }

    private fun startNotificationSettingActivity() {
        val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        startActivity(intent)
    }
}
