package com.rouddy.twophonesupporter.ui

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.rouddy.twophonesupporter.R
import com.rouddy.twophonesupporter.bluetooth.BluetoothService
import com.rouddy.twophonesupporter.databinding.ActivityLogBinding
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import java.util.logging.Logger

class LogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogBinding
    private val adapter = LogAdapter()
    private val compositeDisposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.logRecyclerView.adapter = adapter

        loadLogs()
    }

    override fun onDestroy() {
        super.onDestroy()
        compositeDisposable.dispose()
    }

    private fun loadLogs() {
        BluetoothService
            .bindService(this)
            .flatMapSingle { it.getLogs() }
            .firstOrError()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                Log.e("!!!", it.joinToString(","))
                adapter.setLogs(it)
            }, {
                Log.e(LOG_TAG, "", it)
            })
            .addTo(compositeDisposable)
    }

    companion object {
        private val LOG_TAG = LogActivity::class.java.simpleName
    }
}