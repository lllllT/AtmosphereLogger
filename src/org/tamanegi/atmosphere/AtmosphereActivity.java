package org.tamanegi.atmosphere;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.AnimationUtils;

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

    private long plot_end = -1;

    private PlotView plotter_main;
    private PlotView plotter_sub;
    private VerticalTicsView vtics_main;
    private HorizontalTicsView htics_main_qday;
    private HorizontalTicsView htics_main_day;
    private HorizontalTicsView htics_sub;

    private SelectionHolder selection_holder;

    private Handler handler;
    private Runnable logdata_updater = new Runnable() {
            @Override public void run() {
                updateLogData();
            }
        };
    private Runnable vtics_updater = new Runnable() {
            @Override public void run() {
                updateValueTics(plotter_main,
                                plotter_sub.getSelectionRangeStart(),
                                plotter_sub.getSelectionRangeEnd(),
                                PLOT_VALUE_MAIN_TICS_STEP);
            }
        };

    private GestureDetector gesture_detector;

    private Map<Object, List<Animator>> animator_map;

    @Override
    public void onCreate(Bundle savedState)
    {
        super.onCreate(savedState);
        setContentView(R.layout.main);

        data = LogData.getInstance(this);
        records = new LogData.LogRecord[LogData.TOTAL_COUNT];
        record_cnt = 0;

        selection_holder = new SelectionHolder();

        handler = new Handler();
        gesture_detector = new GestureDetector(this, new GestureListener());
        animator_map = new HashMap<Object, List<Animator>>();

        plotter_main = (PlotView)findViewById(R.id.plotter_main);
        plotter_main.setOnTouchListener(new OnTouchMainPlotterListener());

        plotter_sub = (PlotView)findViewById(R.id.plotter_sub);
        plotter_sub.setOnTouchListener(new OnTouchSubPlotterListener());
        plotter_sub.setOnSelectionChangeListener(new SelectionListener());

        vtics_main = (VerticalTicsView)findViewById(R.id.tic_left);

        htics_main_qday =
            (HorizontalTicsView)findViewById(R.id.tic_bottom_qday);
        htics_main_day =
            (HorizontalTicsView)findViewById(R.id.tic_bottom_day);
        htics_sub =
            (HorizontalTicsView)findViewById(R.id.tic_sub_bottom);

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

        SensorManager manager = (SensorManager)getSystemService(SENSOR_SERVICE);
        Sensor sensor = manager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        if(sensor == null) {
            new AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setMessage(R.string.msg_no_sensor)
                .setPositiveButton(
                    android.R.string.ok,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog,
                                            int button) {
                            finish();
                        }
                    })
                .setOnCancelListener(
                    new DialogInterface.OnCancelListener() {
                        public void onCancel(DialogInterface dialog) {
                            finish();
                        }
                    })
                .show();
            return;
        }

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

        handler.removeCallbacks(logdata_updater);
        handler.removeCallbacks(vtics_updater);
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
        htics_sub.setTimeRange(cur_time - PLOT_SUB_RANGE, cur_time);
        updateValueTics(plotter_sub, cur_time - PLOT_SUB_RANGE, cur_time,
                        PLOT_VALUE_SUB_TICS_STEP);

        updateSelectionRange();

        handler.postDelayed(logdata_updater, LoggerService.LOG_INTERVAL);
    }

    private void updateSelectionRange()
    {
        long cur_time = System.currentTimeMillis();
        long selection_end = (plot_end < 0 ? cur_time : plot_end);
        plotter_sub.setSelectionRange(selection_end - PLOT_MAIN_RANGE,
                                      selection_end);
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

        int low = (int)(((min - step) / step) + 0.5f) * step;
        int high = (int)(((max + step) / step) + 0.5f) * step;
        float cur_low = plotter.getValueRangeMin();
        float cur_high = plotter.getValueRangeMax();

        if(low != cur_low || high != cur_high) {
            cancelAllAnimator(plotter);
            cancelAllAnimator(vtics_main);

            if(cur_low == cur_high) {
                plotter.setValueRange(low, high);
                vtics_main.setValueRange(low, high);
            }
            else {
                startValueAnimation(plotter, "valueRangeMin", cur_low, low);
                startValueAnimation(plotter, "valueRangeMax", cur_high, high);
                startValueAnimation(vtics_main, "valueRangeMin",
                                    cur_low, low);
                startValueAnimation(vtics_main, "valueRangeMax",
                                    cur_high, high);
            }
        }
    }

    private void startValueAnimation(Object target, String prop,
                                     float from, float to)
    {
        ObjectAnimator anim = ObjectAnimator.ofFloat(target, prop, from, to);
        anim.setDuration(ANIMATION_DURATION);
        anim.setInterpolator(AnimationUtils.loadInterpolator(
                                 this, R.anim.plotter_value_interpolator));
        anim.start();
        addAnimator(target, anim);
    }

    private void startSelectionAnimation(Object target, String prop,
                                         int from, int to)
    {
        ObjectAnimator anim = ObjectAnimator.ofInt(target, prop, from, to);
        anim.setDuration(ANIMATION_DURATION);
        anim.setInterpolator(AnimationUtils.loadInterpolator(
                                 this, R.anim.plotter_selection_interpolator));
        anim.start();
        addAnimator(target, anim);
    }

    private void startFlingAnimation(Object target, String prop,
                                     int from, int to, long duration)
    {
        ObjectAnimator anim = ObjectAnimator.ofInt(target, prop, from, to);
        anim.setDuration(duration);
        anim.setInterpolator(AnimationUtils.loadInterpolator(
                                 this, R.anim.plotter_fling_interpolator));
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
            htics_main_qday.setTimeRange(start, end);
            htics_main_day.setTimeRange(start, end);

            handler.removeCallbacks(vtics_updater);
            handler.post(vtics_updater);

            long cur_time = System.currentTimeMillis();
            plot_end = (cur_time - end <= LoggerService.LOG_INTERVAL * 2 ?
                        -1 : end);
        }
    }

    private class OnTouchMainPlotterListener implements View.OnTouchListener
    {
        public boolean onTouch(View v, MotionEvent ev)
        {
            return gesture_detector.onTouchEvent(ev);
        }
    }

    private class GestureListener
        extends GestureDetector.SimpleOnGestureListener
    {
        private float deceleration;

        private GestureListener()
        {
            float ppi = getResources().getDisplayMetrics().density * 160.0f;
            float inch_per_meter = 39.37f;
            deceleration =
                SensorManager.GRAVITY_EARTH * inch_per_meter * ppi *
                ViewConfiguration.getScrollFriction();
        }

        @Override
        public boolean onDown(MotionEvent ev)
        {
            cancelAllAnimator(selection_holder);
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent ev1, MotionEvent ev2,
                                float dx, float dy)
        {
            long cur_time = System.currentTimeMillis();
            long dt = (long)(dx * PLOT_MAIN_RANGE / plotter_main.getWidth());

            plot_end = (plot_end < 0 ? cur_time : plot_end) + dt;
            if(plot_end >= cur_time) {
                plot_end = -1;
            }
            updateSelectionRange();

            return true;
        }

        @Override
        public boolean onFling(MotionEvent ev1, MotionEvent ev2,
                               float vx, float vy)
        {
            float duration = Math.abs(vx / deceleration);
            float dx = -vx * duration / 2;
            long dt = (long)(dx * PLOT_MAIN_RANGE / plotter_main.getWidth());
            long cur_end = plotter_sub.getSelectionRangeEnd();

            cancelAllAnimator(selection_holder);
            selection_holder.end_from = cur_end;
            selection_holder.end_to = cur_end + dt;
            startFlingAnimation(selection_holder, "selectionRangeEnd",
                                0, SelectionHolder.DIVISION,
                                (long)(duration * 1000));
            return true;
        }
    }

    private class OnTouchSubPlotterListener implements View.OnTouchListener
    {
        private int touch_slop;
        private float last_x;

        private OnTouchSubPlotterListener()
        {
            ViewConfiguration conf =
                ViewConfiguration.get(AtmosphereActivity.this);
            touch_slop = conf.getScaledTouchSlop();
        }

        public boolean onTouch(View v, MotionEvent ev)
        {
            long range_start = plotter_sub.getTimeRangeStart();
            long range_end = plotter_sub.getTimeRangeEnd();
            long center =
                (long)((ev.getX() / plotter_sub.getWidth()) *
                       (range_end - range_start)) +
                range_start;
            long end = center + PLOT_MAIN_RANGE / 2;

            cancelAllAnimator(selection_holder);
            if(ev.getAction() == MotionEvent.ACTION_MOVE &&
               Math.abs(ev.getX() - last_x) > touch_slop) {
                plotter_sub.setSelectionRange(end - PLOT_MAIN_RANGE, end);
                last_x = ev.getX();
            }
            else {
                selection_holder.end_from = plotter_sub.getSelectionRangeEnd();
                selection_holder.end_to = end;
                startSelectionAnimation(selection_holder, "selectionRangeEnd",
                                        0, SelectionHolder.DIVISION);
                if(ev.getAction() != MotionEvent.ACTION_MOVE) {
                    last_x = ev.getX();
                }
            }

            return true;
        }
    }

    private class SelectionHolder
    {
        private static final int DIVISION = 1000;

        private long end_from;
        private long end_to;

        // invoked from animator
        @SuppressWarnings("unused")
        public void setSelectionRangeEnd(int fac)
        {
            long end = (end_from * (DIVISION - fac) + end_to * fac) / DIVISION;
            long start = end - PLOT_MAIN_RANGE;
            plotter_sub.setSelectionRange(start, end);
        }
    }
}
