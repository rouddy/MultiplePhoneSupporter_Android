package com.rouddy.twophonesupporter.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.rouddy.twophonesupporter.bluetooth.BluetoothService
import com.rouddy.twophonesupporter.databinding.ActivityInitialSettingBinding
import com.rouddy.twophonesupporter.flatMapFirstCompletable
import com.tbruyelle.rxpermissions3.RxPermissions
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo

class InitialSettingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInitialSettingBinding

    private val compositeDisposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityInitialSettingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        BluetoothService
            .bindService(this)
            .flatMap {
                it.getPeripheralStateObservable()
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                Log.e(LOG_TAG, "peripheral success $it")
                when (it) {
                    is BluetoothService.PeripheralState.Stop -> {
                        binding.actAsPeripheralBtn.isSelected = false
                        binding.finishBtn.isEnabled = false
                        binding.peripheralStateView.visibility = View.GONE
                    }
                    is BluetoothService.PeripheralState.Advertising -> {
                        binding.actAsPeripheralBtn.isSelected = true
                        binding.finishBtn.isEnabled = true
                        binding.peripheralStateView.visibility = View.GONE
                    }
                    is BluetoothService.PeripheralState.WaitForConnect -> {
                        binding.actAsPeripheralBtn.isSelected = true
                        binding.finishBtn.isEnabled = true
                        binding.peripheralStateView.visibility = View.VISIBLE
                        binding.peripheralStateView.text = "Wait For Connect Device ${it.macAddress}"
                    }
                    is BluetoothService.PeripheralState.Connected -> {
                        binding.actAsPeripheralBtn.isSelected = true
                        binding.finishBtn.isEnabled = true
                        binding.peripheralStateView.visibility = View.VISIBLE
                        binding.peripheralStateView.text = "Device ${it.macAddress} is Connected"
                    }
                }

            }, {
                Log.e(LOG_TAG, "peripheral check", it)
            })
            .addTo(compositeDisposable)

        binding.actAsPeripheralBtn.setOnClickListener {
            if (binding.actAsPeripheralBtn.isSelected) {
                AlertDialog.Builder(this)
                    .setTitle("Do you want to stop peripheral?")
                    .setPositiveButton("Stop") { _, _ ->
                        stopActAsPeripheral()
                    }
                    .setNegativeButton("Keep Alive") { _, _ ->

                    }
                    .create()
                    .show()
            } else {
                startActAsPeripheral()
            }
        }
        binding.actAsCentralBtn.setOnClickListener {
            Toast.makeText(this, "Act as Central is not supported yet", Toast.LENGTH_LONG).show()
        }
        binding.finishBtn.setOnClickListener {
            finishIfEnabled()
        }

        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
                finishIfEnabled()
            }
        } else {
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finishIfEnabled()
                }
            })
        }
    }

    private fun startActAsPeripheral() {
        checkPeripheralPermission()
            .andThen(BluetoothService.bindService(this))
            .flatMapFirstCompletable { service ->
                service.startPeripheral()
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                Log.e(LOG_TAG, "start peripheral complete")
                binding.finishBtn.isEnabled = true
            }, {
                Log.e(LOG_TAG, "peripheral error", it)
            })
            .addTo(compositeDisposable)
    }

    private fun stopActAsPeripheral() {
        checkPeripheralPermission()
            .andThen(BluetoothService.bindService(this))
            .flatMapFirstCompletable { service ->
                service.stopPeripheral()
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                Log.e(LOG_TAG, "start peripheral complete")
                binding.finishBtn.isEnabled = false
            }, {
                Log.e(LOG_TAG, "peripheral error", it)
            })
            .addTo(compositeDisposable)
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

    private fun finishIfEnabled() {
        if (binding.finishBtn.isEnabled) {
            finish()
        }
    }

    companion object {
        private val LOG_TAG = InitialSettingActivity::class.java.simpleName
    }
}