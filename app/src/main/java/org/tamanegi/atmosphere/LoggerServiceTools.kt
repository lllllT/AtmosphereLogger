@file:JvmName("LoggerServiceTools")

package org.tamanegi.atmosphere

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.os.Build

val NOTIFICATION_ID_LOGGER_SERVICE = 1
val NOTIFICATION_CHANNEL_ID_LOGGER = "org.tamanegi.atmosphere.Logger"

fun Service.startForegroundLogger() {
    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID_LOGGER,
                getString(R.string.notification_channel_logger),
                NotificationManager.IMPORTANCE_LOW)
        channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC

        notificationManager.createNotificationChannel(channel)
    }

    val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Notification.Builder(this, NOTIFICATION_CHANNEL_ID_LOGGER)
    } else {
        Notification.Builder(this)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        builder.setPriority(Notification.PRIORITY_LOW)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        builder.setVisibility(Notification.VISIBILITY_PUBLIC)
    }

    builder.setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.measuring))
            .setSmallIcon(R.drawable.icon)

    val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
        builder.build()
    } else {
        builder.notification
    }
    startForeground(NOTIFICATION_ID_LOGGER_SERVICE, notification)
}

fun Service.stopForegroundLogger() {
    stopForeground(true)
}