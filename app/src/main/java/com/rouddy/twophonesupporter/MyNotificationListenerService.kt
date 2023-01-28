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
        Log.e("$$$", "NotificationListenerService::extras:${sbn?.notification?.extras?.keySet()?.joinToString(",")}")
        sbn?.run {
            BluetoothService
                .bindService(this@MyNotificationListenerService)
                .flatMapFirstCompletable {
                    it.notificationPosted(
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
                    )
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