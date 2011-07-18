package org.tamanegi.atmosphere;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
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

    static final int TIMEOUT_MSEC = 10 * 1000;
    static final int LOG_INTERVAL = 5 * 60 * 1000;

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
        LogData log = LogData.getInstance(this);
        long lasttime = log.getLastTimestamp();
        long nexttime = (lasttime > 0 ? lasttime + LOG_INTERVAL :
                         System.currentTimeMillis() + LOG_INTERVAL);

        AlarmManager amgr = (AlarmManager)getSystemService(ALARM_SERVICE);
        amgr.setInexactRepeating(AlarmManager.RTC_WAKEUP,
                                 nexttime, LOG_INTERVAL, getMeasureIntent());
    }

    private void stopLogging()
    {
        AlarmManager amgr = (AlarmManager)getSystemService(ALARM_SERVICE);
        amgr.cancel(getMeasureIntent());
    }

    private PendingIntent getMeasureIntent()
    {
        Intent intent = new Intent(this, Receiver.class)
            .setAction(ACTION_MEASURE);
        return PendingIntent.getBroadcast(this, 0, intent,
                                          PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void measure()
    {
        SensorManager manager = (SensorManager)getSystemService(SENSOR_SERVICE);
        Sensor sensor = manager.getDefaultSensor(Sensor.TYPE_PRESSURE);

        Listener listener = new Listener();
        synchronized(listener) {
            if(! manager.registerListener(
                   listener, sensor,
                   SensorManager.SENSOR_DELAY_NORMAL, handler)) {
                return;
            }

            try {
                listener.wait(TIMEOUT_MSEC);
            }
            catch(Exception e) {
                // ignore
            }
            manager.unregisterListener(listener);
        }

        LogData log = LogData.getInstance(this);
        log.writeRecord(new LogData.LogRecord(listener.time, listener.value));
    }

    private static class Listener implements SensorEventListener
    {
        private long time;
        private float value;

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy)
        {
            // do nothing
        }

        @Override
        public void onSensorChanged(SensorEvent event)
        {
            value = event.values[0];
            time = System.currentTimeMillis();

            synchronized(this) {
                notify();
            }
        }
    }
}
