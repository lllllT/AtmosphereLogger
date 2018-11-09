@file:JvmName("LoggerTools")

package org.tamanegi.atmosphere

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler


val JOB_ID_MEASURE = 1

fun startLogging(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        val alreadyStarted = jobScheduler.allPendingJobs.any { it.id == JOB_ID_MEASURE }
        if(alreadyStarted) {
            return
        }

        val builder = JobInfo.Builder(JOB_ID_MEASURE, ComponentName(context, LoggerJobService::class.java))
                .setPersisted(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.setPeriodic(LoggerService.LOG_INTERVAL, LoggerService.LOG_INTERVAL_FLEX)
        }
        else {
            builder.setPeriodic(LoggerService.LOG_INTERVAL)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setRequiresBatteryNotLow(true)
        }

        val job = builder.build()

        jobScheduler.schedule(job)
    } else {
        val loggerIntent = Intent(context, LoggerService::class.java)
                .setAction(LoggerService.ACTION_START_LOGGING)
        context.startService(loggerIntent)
    }
}

fun measureAsync(context: Context, handler: Handler, completeHandler: ()->Unit) {
    MeasureRunner(context, handler, completeHandler).start()
}

fun measure(context: Context, handler: Handler) {
    val lock = Object()
    synchronized(lock) {
        measureAsync(context, handler) {
            synchronized(lock) {
                lock.notify()
            }
        }

        try {
            lock.wait(LoggerService.TIMEOUT_MSEC)
        }
        catch (e:Exception) {
            // ignore
        }
    }
}

private class MeasureRunner(val context: Context, val handler: Handler, val completeHandler: () -> Unit) : SensorEventListener {
    val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    var measured = false

    fun start() {
        val sensor = manager.getDefaultSensor(Sensor.TYPE_PRESSURE)
        if(sensor == null) {
            handler.post {
                completeHandler()
            }
            return;
        }

        if(! manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL, handler)) {
            handler.post {
                completeHandler()
            }
            return
        }

        handler.postDelayed(
                {
                    if(!measured) {
                        manager.unregisterListener(this)
                        completeHandler()
                    }
                },
                LoggerService.TIMEOUT_MSEC)
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // do nothing
    }

    override fun onSensorChanged(event: SensorEvent) {
        measured = true
        manager.unregisterListener(this)

        val value = event.values[0]
        val time = System.currentTimeMillis()

        val log = LogData.getInstance(context)
        log.writeRecord(LogData.LogRecord(time, value))

        completeHandler()
    }
}
