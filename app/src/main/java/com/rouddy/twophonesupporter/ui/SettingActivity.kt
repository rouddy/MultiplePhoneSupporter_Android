package com.rouddy.twophonesupporter.ui

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import com.rouddy.twophonesupporter.R
import com.rouddy.twophonesupporter.databinding.ActivitySettingBinding

class SettingActivity : AppCompatActivity(), SettingAdapter.Listener {

    enum class Settings(val text: String) {
        Connection("Connection"),
        NotificationFilter("Notification Filter"),
        Credit("Credit"),
    }

    private lateinit var binding: ActivitySettingBinding
    private val adapter = SettingAdapter(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"

        binding = ActivitySettingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.settingRecycler.adapter = adapter
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSettingClick(settings: Settings) {
        when (settings) {
            Settings.Connection -> {
                Intent(this, InitialSettingActivity::class.java).also {
                    startActivity(it)
                }
            }
            Settings.NotificationFilter -> {
                Intent(this, NotificationFilterActivity::class.java).also {
                    startActivity(it)
                }
            }
            Settings.Credit -> {
                Intent(this, CreditActivity::class.java).also {
                    startActivity(it)
                }
            }
        }
    }
}