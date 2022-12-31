package com.rouddy.twophonesupporter

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class MyNotificationListenerService : NotificationListenerService() {
    override fun onCreate() {
        Log.e("!!!", "MyNotificationListenerService::onCreate")
        super.onCreate()
    }

    override fun onDestroy() {
        Log.e("!!!", "MyNotificationListenerService::onDestroy")
        super.onDestroy()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.e("!!!", "onListenerConnected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.e("!!!", "onListenerDisconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        Log.e("!!!", "000 onNotificationPosted:$sbn")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        Log.e("!!!", "222 onNotificationRemoved:$sbn")
    }

}