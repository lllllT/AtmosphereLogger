package org.tamanegi.atmosphere;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.animation.AnimationUtils;
import android.view.animation.OvershootInterpolator;

public class AtmosphereActivity extends Activity
{
    private static final long PLOT_MAIN_RANGE = 1000L * 60 * 60 * 24 * 2;
    private static final long PLOT_SUB_RANGE = 1000L * 60 * 60 * 24 * 30;

    private static final int PLOT_VALUE_MAIN_TICS_STEP = 5;
    private static final int PLOT_VALUE_SUB_TICS_STEP = 10;

    private static final int ANIMATION_DURATION = 500;

    private static final String KEY_PLOT_END =
        "org.tamanegi.atmosphere.PlotEnd";

    private LogData data;
    private LogData.LogRecord[] records;
    private int record_cnt;

    private long plot_end;

    private PlotView plotter_main;
    private PlotView plotter_sub;

    private Handler handler;
    private Runnable updater = new Runnable() {
            @Override public void run() {
                updateLogData();
            }
        };

    private Map<Object, List<Animator>> animator_map;

    @Override
    public void onCreate(Bundle savedState)
    {
        super.onCreate(savedState);
        setContentView(R.layout.main);

        data = LogData.getInstance(this);
        records = new LogData.LogRecord[LogData.TOTAL_COUNT];
        record_cnt = 0;

        plotter_main = (PlotView)findViewById(R.id.plotter_main);
        plotter_sub = (PlotView)findViewById(R.id.plotter_sub);
        plotter_sub.setOnSelectionChangeListener(new SelectionListener());

        handler = new Handler();
        animator_map = new HashMap<Object, List<Animator>>();

        plot_end = -1;
        if(savedState != null) {
            plot_end = savedState.getLong(KEY_PLOT_END, plot_end);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state)
    {
        state.putLong(KEY_PLOT_END, plot_end);
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        // start logger service, if not yet started
        Intent logger_intent = new Intent(this, LoggerService.class)
            .setAction(LoggerService.ACTION_START_LOGGING);
        startService(logger_intent);

        updateLogData();
    }

    @Override
    protected void onPause()
    {
        super.onPause();

        handler.removeCallbacks(updater);
    }

    private void updateLogData()
    {
        record_cnt = data.readRecords(records);
        long cur_time = System.currentTimeMillis();

        plotter_main.setData(records, record_cnt);
        plotter_main.setNormalInterval(LoggerService.LOG_INTERVAL);

        plotter_sub.setData(records, record_cnt);
        plotter_sub.setNormalInterval(LoggerService.LOG_INTERVAL);
        plotter_sub.setTimeRange(cur_time - PLOT_SUB_RANGE, cur_time);
        updateValueTics(plotter_sub, cur_time - PLOT_SUB_RANGE, cur_time,
                        PLOT_VALUE_SUB_TICS_STEP);

        long selection_end = (plot_end < 0 ? cur_time : plot_end);
        plotter_sub.setSelectionRange(selection_end - PLOT_MAIN_RANGE,
                                      selection_end);

        handler.postDelayed(updater, LoggerService.LOG_INTERVAL);
    }

    private void updateValueTics(PlotView plotter,
                                 long start, long end, int step)
    {
        float min = Float.MAX_VALUE;
        float max = Float.MIN_VALUE;

        for(int i = 0; i < record_cnt; i++) {
            long t = records[i].time;
            float v = records[i].value;

            if(t < start || t > end) {
                continue;
            }

            if(v < min) {
                min = v;
            }
            if(v > max) {
                max = v;
            }
        }
        if(min > max) {
            return;
        }

        int low = (int)((min / step) + 0.5f) * step - step;
        int high = (int)((max / step) + 0.5f) * step + step;
        float cur_low = plotter.getValueRangeMin();
        float cur_high = plotter.getValueRangeMax();

        if(low != cur_low || high != cur_high) {
            cancelAllAnimator(plotter);

            if(cur_low == cur_high) {
                plotter.setValueRange(low, high);
            }
            else {
                startAnimation(plotter, "valueRangeMin", cur_low, low);
                startAnimation(plotter, "valueRangeMax", cur_high, high);
            }
        }
    }

    private void startAnimation(Object target, String prop,
                                float from, float to)
    {
        ObjectAnimator anim = ObjectAnimator.ofFloat(target, prop, from, to);
        anim.setDuration(ANIMATION_DURATION);
        anim.setInterpolator(AnimationUtils.loadInterpolator(
                                 this, R.anim.plotter_value_interpolator));
        anim.start();
        addAnimator(target, anim);
    }

    private void addAnimator(Object target, Animator animator)
    {
        List<Animator> list = animator_map.get(target);
        if(list == null) {
            list = new LinkedList<Animator>();
        }

        list.add(animator);
        animator_map.put(target, list);
    }

    private void cancelAllAnimator(Object target)
    {
        List<Animator> list = animator_map.get(target);
        if(list == null) {
            return;
        }

        for(Animator anim : list) {
            anim.cancel();
        }

        animator_map.remove(target);
    }

    private class SelectionListener
        implements PlotView.OnSelectionChangeListener
    {
        @Override
        public void onSelectionChanged(long start, long end)
        {
            plotter_main.setTimeRange(start, end);
            updateValueTics(plotter_main, start, end,
                            PLOT_VALUE_MAIN_TICS_STEP);

            long cur_time = System.currentTimeMillis();
            plot_end = (cur_time - end <= LoggerService.LOG_INTERVAL * 2 ?
                        -1 : end);
        }
    }
}
