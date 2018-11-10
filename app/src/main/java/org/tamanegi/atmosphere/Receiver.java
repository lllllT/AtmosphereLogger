package org.tamanegi.atmosphere;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.ResultReceiver;

public class Receiver extends BroadcastReceiver
{
    @Override
    public void onReceive(Context context, Intent intent)
    {
        if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) ||
                Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            LoggerTools.startLogging(context);
        }
        else if(LoggerService.ACTION_MEASURE.equals(intent.getAction()) &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)) {
            PowerManager pmgr = (PowerManager)context.getApplicationContext()
                .getSystemService(Context.POWER_SERVICE);
            PowerManager.WakeLock wakelock = pmgr.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "AtmosphereLogger:Receiver.Measure");

            Intent logger_intent = new Intent(context, LoggerService.class)
                .setAction(LoggerService.ACTION_MEASURE)
                .putExtra(LoggerService.EXTRA_RESULT_RECEIVER,
                          new WakeResultReceiver(wakelock));

            wakelock.acquire(LoggerService.TIMEOUT_MSEC);
            context.startService(logger_intent);
        }
    }

    private static class WakeResultReceiver extends ResultReceiver
    {
        private PowerManager.WakeLock wakelock;

        private WakeResultReceiver(PowerManager.WakeLock wakelock)
        {
            super(null);
            this.wakelock = wakelock;
        }

        protected void onReceiveResult(int code, Bundle data)
        {
            wakelock.release();
        }
    }
}
