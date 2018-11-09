package org.tamanegi.atmosphere

import android.annotation.TargetApi
import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Build
import android.os.Handler

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class LoggerJobService : JobService() {
    lateinit var handler: Handler;

    override fun onCreate() {
        super.onCreate()
        handler = Handler()
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        when(params!!.jobId) {
            JOB_ID_MEASURE -> {
                startForegroundLogger()
                measureAsync(this, handler) {
                    stopForegroundLogger()
                    jobFinished(params, false)
                }
            }
        }

        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        stopForegroundLogger()
        return false
    }
}
