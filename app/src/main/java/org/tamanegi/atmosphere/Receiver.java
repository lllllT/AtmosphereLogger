package org.tamanegi.atmosphere;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.ResultReceiver;

public class Receiver extends BroadcastReceiver
{
    @Override
    public void onReceive(Context context, Intent intent)
    {
        if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Intent logger_intent = new Intent(context, LoggerService.class)
                .setAction(LoggerService.ACTION_START_LOGGING);
            context.startService(logger_intent);
        }
        else if(LoggerService.ACTION_MEASURE.equals(intent.getAction())) {
            PowerManager pmgr = (PowerManager)context.getApplicationContext()
                .getSystemService(Context.POWER_SERVICE);
            PowerManager.WakeLock wakelock = pmgr.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "Receiver.Measure");

            Intent logger_intent = new Intent(context, LoggerService.class)
                .setAction(LoggerService.ACTION_MEASURE)
                .putExtra(LoggerService.EXTRA_RESULT_RECEIVER,
                          new WakeResultReceiver(wakelock));

            wakelock.acquire();
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
