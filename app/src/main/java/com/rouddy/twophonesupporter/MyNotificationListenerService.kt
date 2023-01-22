package com.rouddy.twophonesupporter

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.rouddy.twophonesupporter.bluetooth.BluetoothService

class MyNotificationListenerService : NotificationListenerService() {

    override fun onDestroy() {
        Log.e("$$$", "MyNotificationListenerService::onDestroy")
        super.onDestroy()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.e("$$$", "NotificationListenerService::onListenerConnected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.e("$$$", "NotificationListenerService::onListenerDisconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        Log.e("$$$", "NotificationListenerService::onNotificationPosted:$sbn")
        sbn?.run {
            val json = JsonObject()
            json.addProperty("key", key)
            json.addProperty("title", notification.extras.getString(Notification.EXTRA_TITLE).toString())
            json.addProperty("text", notification.extras.getString(Notification.EXTRA_TEXT).toString())
            json.addProperty("sub", notification.extras.getString(Notification.EXTRA_SUB_TEXT).toString())
//            notification.extras.getInt(Notification.EXTRA_LARGE_ICON_BIG).also {
//                Log.e("$$$", "NotificationListenerService:${it}")
//                if (it != 0) {
//                    Log.e("$$$", "NotificationListenerService:${ContextCompat.getDrawable(this@MyNotificationListenerService, it)}")
//                }
//            }
            Gson().toJson(json)
        }?.also { json ->
            BluetoothService
                .bindService(this)
                .flatMapFirstCompletable {
                    it.notificationPosted(json)
                }
                .subscribe({
                    Log.e("$$$", "NotificationListenerService::sendData complete")
                }, {
                    Log.e("$$$", "NotificationListenerService::sendData error", it)
                })
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        Log.e("$$$", "NotificationListenerService::onNotificationRemoved:$sbn")
    }
}