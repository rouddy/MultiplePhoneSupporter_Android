package com.rouddy.twophonesupporter.ui

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.rouddy.twophonesupporter.bluetooth.BluetoothService
import com.rouddy.twophonesupporter.databinding.ActivityNotificationFilterBinding
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.schedulers.Schedulers

class NotificationFilterActivity : AppCompatActivity(), NotificationFilterAdapter.Listener {

    private lateinit var binding: ActivityNotificationFilterBinding
    private lateinit var adapter: NotificationFilterAdapter
    private val compositeDisposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = NotificationFilterAdapter(this)

        binding = ActivityNotificationFilterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.applicationRecycler.adapter = adapter
        BluetoothService.bindService(this)
            .flatMap {
                it.getFilterObservable()
            }
            .subscribe({
                adapter.setFilters(it)
            }, {
                Log.e(LOG_TAG, "filter error", it)
            })
            .addTo(compositeDisposable)

        //get a list of installed apps.
        getInstalledApplications()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                adapter.setApplications(it)
            }, {
                Log.e(LOG_TAG, "load applications error", it)
            })
            .addTo(compositeDisposable)
    }

    private fun getInstalledApplications(): Single<List<NotificationFilterAdapter.Application>> {
        return Single.fromCallable {
            val systemApps = getInstalledApplicationInfos(true)
                .map {
                    NotificationFilterAdapter.Application(
                        it.packageName,
                        packageManager.getApplicationLabel(it) as String,
                        it.loadIcon(packageManager),
                        false,
                    )
                }

            val installed = getInstalledApplicationInfos()
                .filter { applicationInfo ->
                    systemApps.firstOrNull { it.packageName == applicationInfo.packageName } == null
                }
                .map {
                    NotificationFilterAdapter.Application(
                        it.packageName,
                        packageManager.getApplicationLabel(it) as String,
                        it.loadIcon(packageManager),
                        true,
                    )
                }
            (systemApps + installed)
                .filter { it.packageName != packageName }
        }
            .subscribeOn(Schedulers.io())
    }

    private fun getInstalledApplicationInfos(systemOnly: Boolean = false): List<ApplicationInfo> {
        return if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getInstalledApplications(
                PackageManager.ApplicationInfoFlags.of(
                    if (systemOnly) {
                        (PackageManager.GET_META_DATA or PackageManager.MATCH_SYSTEM_ONLY).toLong()
                    } else {
                        PackageManager.GET_META_DATA.toLong()
                    }
                )
            )
        } else {
            packageManager.getInstalledApplications(
                if (systemOnly) {
                    (PackageManager.GET_META_DATA or PackageManager.MATCH_SYSTEM_ONLY)
                } else {
                    PackageManager.GET_META_DATA
                }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        compositeDisposable.dispose()
    }

    override fun onChecked(packageName: String, enabled: Boolean) {
        BluetoothService.bindService(this)
            .doOnNext {
                if (enabled) {
                    it.removeFilter(packageName)
                } else {
                    it.addFilter(packageName)
                }
            }
            .firstOrError()
            .subscribe({
            }, {
                Log.e(LOG_TAG, "add or remove filter error", it)
            })
            .addTo(compositeDisposable)
    }

    companion object {
        private val LOG_TAG = NotificationFilterActivity::class.java.simpleName
    }
}