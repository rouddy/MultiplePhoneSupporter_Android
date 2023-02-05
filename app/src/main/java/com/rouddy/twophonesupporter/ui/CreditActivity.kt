package com.rouddy.twophonesupporter.ui

import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.window.OnBackInvokedDispatcher
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.rouddy.twophonesupporter.databinding.ActivityCreditBinding
import java.io.BufferedReader
import java.io.InputStream

class CreditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreditBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Credits"

        binding = ActivityCreditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val am = resources.assets
        var inputStream: InputStream? = null
        var reader: BufferedReader? = null
        try {
            inputStream = am.open("credit.html")
            reader = BufferedReader(inputStream.reader())
            val content = StringBuilder()
            var line = reader.readLine()
            while (line != null) {
                content.append(line)
                line = reader.readLine()
            }
            binding.webView.loadData(content.toString(), "text/html", null)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            reader?.close()
            inputStream?.close()
        }

//        binding.webView.loadUrl("file://android_asset/credit.html")
        
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
                onBack()
            }
        } else {
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    onBack()
                }
            })
        }
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
    
    private fun onBack() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            finish()
        }
    }
}