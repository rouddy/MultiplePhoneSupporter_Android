package com.rouddy.twophonesupporter.ui

import android.animation.Animator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import com.rouddy.twophonesupporter.R
import com.rouddy.twophonesupporter.bluetooth.BluetoothService
import com.rouddy.twophonesupporter.databinding.ActivityMainBinding
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
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

        BluetoothService.bindService(this)
            .flatMap {
                it.getPeripheralConnectedObservable()
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                if (it) {
                    binding.statusView
                        .animate()
                        .scaleY(1f)
                        .alpha(1f)
                        .setDuration(1000L)
                        .setListener(object : Animator.AnimatorListener {
                            override fun onAnimationEnd(p0: Animator) {
                                binding.statusView.visibility = View.VISIBLE
                            }
                            override fun onAnimationStart(p0: Animator) {}
                            override fun onAnimationCancel(p0: Animator) {}
                            override fun onAnimationRepeat(p0: Animator) {}
                        })
                        .start()
                    binding.statusView.text = "1 Devices Connected"
                } else {
                    binding.statusView
                        .animate()
                        .scaleY(0f)
                        .alpha(0f)
                        .setDuration(1000L)
                        .setListener(object : Animator.AnimatorListener {
                            override fun onAnimationEnd(p0: Animator) {
                                binding.statusView.visibility = View.GONE
                            }
                            override fun onAnimationStart(p0: Animator) {}
                            override fun onAnimationCancel(p0: Animator) {}
                            override fun onAnimationRepeat(p0: Animator) {}
                        })
                        .start()
                }
            }, {
                Log.e(LOG_TAG, "", it)
            })
            .addTo(compositeDisposable)
    }

    override fun onResume() {
        super.onResume()
        binding.notificationRelayStartBtn.isEnabled = !checkNotificationPermissionGrantred()
    }

    override fun onDestroy() {
        super.onDestroy()
        compositeDisposable.dispose()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                Intent(this, SettingActivity::class.java).also {
                    startActivity(it)
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun checkNotificationPermissionGrantred(): Boolean {
        val sets = NotificationManagerCompat.getEnabledListenerPackages(this)
        return sets.contains(packageName)
    }

    private fun startNotificationSettingActivity() {
        val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        startActivity(intent)
    }

    companion object {
        private val LOG_TAG = MainActivity::class.java.simpleName
    }
}
