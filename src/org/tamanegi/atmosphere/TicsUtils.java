package org.tamanegi.atmosphere;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;

import android.content.Context;
import android.hardware.SensorManager;

public class TicsUtils
{
    public enum TicsStep
    {
        HOUR, DAY, WEEK, MONTH, QDAY, HDAY, FIVE_DAYS, TEN_DAYS
    }

    private static final TicsStep[] TICS_STEPS = {
        TicsStep.HOUR, TicsStep.DAY, TicsStep.WEEK, TicsStep.MONTH,
        TicsStep.QDAY, TicsStep.HDAY, TicsStep.FIVE_DAYS, TicsStep.TEN_DAYS
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
                       day > 25 ? 25 :
                       day);
            }
            if(step == TicsStep.TEN_DAYS) {
                day = (day / 10) * 10;
                day = (day == 0 ? 1 :
                       day > 20 ? 20 :
                       day);
            }

            cal.clear();
            cal.set(year, month, day, hour, 0);
        }

        return cal;
    }

    public static void incrementCalendar(Calendar cal, TicsStep step)
    {
        if(step == TicsStep.FIVE_DAYS) {
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
        else if(step == TicsStep.TEN_DAYS) {
            int day = cal.get(Calendar.DAY_OF_MONTH);
            day = (day < 10 ? 10 : day + 10);
            if(day <= 20) {
                cal.set(Calendar.DAY_OF_MONTH, day);
            }
            else {
                cal.add(Calendar.MONTH, 1);
                cal.set(Calendar.DAY_OF_MONTH, 1);
            }
        }
        else {
            int field = (step == TicsStep.HOUR ? Calendar.HOUR :
                         step == TicsStep.QDAY ? Calendar.HOUR :
                         step == TicsStep.HDAY ? Calendar.HOUR :
                         step == TicsStep.DAY ? Calendar.DAY_OF_MONTH :
                         step == TicsStep.WEEK ? Calendar.WEEK_OF_MONTH :
                         Calendar.MONTH);
            int cnt = (step == TicsStep.QDAY ? 6 :
                       step == TicsStep.HDAY ? 12 : 1);
            cal.add(field, cnt);
        }
    }

    public static DateFormat getTicsDateFormat(Context context, TicsStep step)
    {
        if(step == TicsStep.HOUR ||
           step == TicsStep.QDAY || step == TicsStep.HDAY) {
            return android.text.format.DateFormat.getTimeFormat(context);
        }
        else {
            return android.text.format.DateFormat.getDateFormat(context);
        }
    }

    public interface PressureUnitConverter
    {
        public float convert(float value);
    }

    // hPa -> hPa
    private static PressureUnitConverter converter_hPa =
        new PressureUnitConverter() {
            @Override public float convert(float value) {
                return value;
            }
        };

    // hPa -> mmHg
    private static PressureUnitConverter converter_mmHg =
        new PressureUnitConverter() {
            @Override public float convert(float value) {
                return value * 0.7500616f;
            }
        };

    // hPa -> inHg
    private static PressureUnitConverter converter_inHg =
        new PressureUnitConverter() {
            @Override public float convert(float value) {
                return value * 0.02952998f;
            }
        };

    // hPa -> atm
    private static PressureUnitConverter converter_atm =
        new PressureUnitConverter() {
            @Override public float convert(float value) {
                return value * 0.0009869233f;
            }
        };

    // hPa -> m
    private static PressureUnitConverter converter_m =
        new PressureUnitConverter() {
            @Override public float convert(float value) {
                return SensorManager.getAltitude(
                    SensorManager.PRESSURE_STANDARD_ATMOSPHERE, value);
            }
        };

    // hPa -> ft
    private static PressureUnitConverter converter_ft
        = new PressureUnitConverter() {
                @Override public float convert(float value) {
                    return 3.2808f * SensorManager.getAltitude(
                        SensorManager.PRESSURE_STANDARD_ATMOSPHERE, value);
                }
            };

    private static PressureUnitConverter[] converters = {
        converter_hPa,
        converter_mmHg,
        converter_inHg,
        converter_atm,
        converter_m,
        converter_ft,
    };

    public static PressureUnitConverter getPressureUnitConverter(int unit)
    {
        return converters[unit];
    }
}
