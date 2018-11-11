@file:JvmName("LoggerAlarmTools")

package org.tamanegi.atmosphere

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Context.ALARM_SERVICE
import android.content.Intent
import org.tamanegi.atmosphere.LoggerService.ACTION_MEASURE
import org.tamanegi.atmosphere.LoggerService.LOG_INTERVAL


fun startLoggingAlarm(context: Context) {
    val log = LogData.getInstance(context)
    val lasttime = log.lastTimestamp
    val nexttime = if (lasttime > 0)
        lasttime + LOG_INTERVAL
    else
        System.currentTimeMillis() + LOG_INTERVAL

    val amgr = context.getSystemService(ALARM_SERVICE) as AlarmManager
    amgr.setInexactRepeating(AlarmManager.RTC_WAKEUP, nexttime, LOG_INTERVAL, getMeasureIntent(context))
}

fun stopLoggingAlarm(context: Context) {
    val amgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    amgr.cancel(getMeasureIntent(context))
}

fun getMeasureIntent(context: Context): PendingIntent {
    val intent = Intent(context, Receiver::class.java)
            .setAction(ACTION_MEASURE)
    return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
}
