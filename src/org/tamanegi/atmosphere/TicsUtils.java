package org.tamanegi.atmosphere;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;

import android.content.Context;

public class TicsUtils
{
    public enum TicsStep
    {
        HOUR, DAY, WEEK, MONTH, QDAY, FIVE_DAYS
    }

    private static final TicsStep[] TICS_STEPS = {
        TicsStep.HOUR, TicsStep.DAY, TicsStep.WEEK, TicsStep.MONTH,
        TicsStep.QDAY, TicsStep.FIVE_DAYS
    };

    public static TicsStep getTicsStep(int idx)
    {
        return TICS_STEPS[idx];
    }

    public static Calendar roundTimeByTicsStep(long time, TicsStep step)
    {
        Calendar cal = new GregorianCalendar();
        cal.setTimeInMillis(time);

        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH);
        if(step == TicsStep.WEEK) {
            int week = cal.get(Calendar.WEEK_OF_MONTH);
            cal.clear();
            cal.set(Calendar.YEAR, year);
            cal.set(Calendar.MONTH, month);
            cal.set(Calendar.WEEK_OF_MONTH, week);
            cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
        }
        else {
            int day = (step == TicsStep.MONTH ?
                       1 : cal.get(Calendar.DAY_OF_MONTH));
            int hour = (step == TicsStep.HOUR ?
                        cal.get(Calendar.HOUR) : 0);

            if(step == TicsStep.FIVE_DAYS) {
                day = (day / 5) * 5;
                day = (day == 0 ? 1 :
                       day >= 30 ? 25 :
                       day);
            }

            cal.clear();
            cal.set(year, month, day, hour, 0);
        }

        return cal;
    }

    public static void incrementCalendar(Calendar cal, TicsStep step)
    {
        if(step != TicsStep.FIVE_DAYS) {
            int field = (step == TicsStep.HOUR ? Calendar.HOUR :
                         step == TicsStep.QDAY ? Calendar.HOUR :
                         step == TicsStep.DAY ? Calendar.DAY_OF_MONTH :
                         step == TicsStep.WEEK ? Calendar.WEEK_OF_MONTH :
                         Calendar.MONTH);
            int cnt = (step == TicsStep.QDAY ? 6 : 1);
            cal.add(field, cnt);
        }
        else {
            int day = cal.get(Calendar.DAY_OF_MONTH);
            day = (day < 5 ? 5 : day + 5);
            if(day <= 25) {
                cal.set(Calendar.DAY_OF_MONTH, day);
            }
            else {
                cal.add(Calendar.MONTH, 1);
                cal.set(Calendar.DAY_OF_MONTH, 1);
            }
        }
    }

    public static DateFormat getTicsDateFormat(Context context, TicsStep step)
    {
        if(step == TicsStep.HOUR || step == TicsStep.QDAY) {
            return android.text.format.DateFormat.getTimeFormat(context);
        }
        else {
            return android.text.format.DateFormat.getDateFormat(context);
        }
    }
}
