package org.tamanegi.atmosphere;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.os.ResultReceiver;

public class LoggerService extends IntentService
{
    public static final String ACTION_START_LOGGING =
        "org.tamanegi.atmosphere.action.START_LOGGING";
    public static final String ACTION_STOP_LOGGING =
        "org.tamanegi.atmosphere.action.STOP_LOGGING";
    public static final String ACTION_MEASURE =
        "org.tamanegi.atmosphere.action.MEASURE";

    public static final String EXTRA_RESULT_RECEIVER = "resultReceiver";

    static final long TIMEOUT_MSEC = 10 * 1000;
    static final long LOG_INTERVAL = 15 * 60 * 1000;
    static final long LOG_INTERVAL_FLEX = 5 * 60 * 1000;

    private Handler handler;

    public LoggerService()
    {
        super("LoggerService");
    }

    @Override
    public void onCreate()
    {
        super.onCreate();
        handler = new Handler();
    }

    @Override
    protected void onHandleIntent(Intent intent)
    {
        Bundle ret = null;

        if(ACTION_START_LOGGING.equals(intent.getAction())) {
            startLogging();
        }
        else if(ACTION_STOP_LOGGING.equals(intent.getAction())) {
            stopLogging();
        }
        else if(ACTION_MEASURE.equals(intent.getAction())) {
            measure();
        }

        Parcelable v = intent.getParcelableExtra(EXTRA_RESULT_RECEIVER);
        if(v != null && v instanceof ResultReceiver) {
            ((ResultReceiver)v).send(0, ret);
        }
    }

    private void startLogging()
    {
        LoggerAlarmTools.startLoggingAlarm(this);
    }

    private void stopLogging()
    {
        LoggerAlarmTools.stopLoggingAlarm(this);
    }

    private void measure()
    {
        LoggerServiceTools.startForegroundLogger(this);
        LoggerTools.measure(this, handler);
        LoggerServiceTools.stopForegroundLogger(this);
    }
}
