package com.rouddy.twophonesupporter

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.rouddy.twophonesupporter.bluetooth.BluetoothService

data class NotificationData(val key: String, val title: String?, val text: String?, val subText: String?, val icon: Int?) {
    val packageName: String
        get() = key.split("|")[1]
}

class MyNotificationListenerService : NotificationListenerService() {

    override fun onDestroy() {
        Log.e(LOG_TAG, "MyNotificationListenerService::onDestroy")
        super.onDestroy()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.e(LOG_TAG, "onListenerConnected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.e(LOG_TAG, "onListenerDisconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        Log.e(LOG_TAG, "onNotificationPosted:$sbn")
        Log.e(LOG_TAG, "extras:${sbn?.notification?.extras?.keySet()?.joinToString(",")}")
        sbn?.run {
            BluetoothService
                .bindService(this@MyNotificationListenerService)
                .flatMapFirstCompletable {
                    it.notificationPosted(NotificationData(
                        key,
                        notification.extras.getString(Notification.EXTRA_TITLE),
                        notification.extras.getString(Notification.EXTRA_TEXT),
                        notification.extras.getString(Notification.EXTRA_SUB_TEXT),
                        notification.extras.getInt(Notification.EXTRA_LARGE_ICON_BIG).let {
                            if (it != 0) {
                                it
                            } else {
                                null
                            }
                        }
                    ))
                }
                .subscribe({
                    Log.e(LOG_TAG, "sendData complete")
                }, {
                    Log.e(LOG_TAG, "sendData error", it)
                })
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        Log.e(LOG_TAG, "onNotificationRemoved:$sbn")
    }

    companion object {
        private val LOG_TAG = MyNotificationListenerService::class.java.simpleName
    }
}