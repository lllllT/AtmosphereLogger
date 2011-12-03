package org.tamanegi.atmosphere;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

public class AtmosphereFragment extends Fragment
{
    private static final long PLOT_MAIN_RANGE = 1000L * 60 * 60 * 24 * 2;
    private static final long PLOT_SUB_RANGE = 1000L * 60 * 60 * 24 * 30;

    private static final int ANIMATION_DURATION = 500;

    private static final String KEY_PLOT_END =
        "org.tamanegi.atmosphere.PlotEnd";

    private static final String PREF_UNIT = "unit";

    private LogData data;
    private LogData.LogRecord[] records;
    private int record_cnt;

    private long plot_end = -1;

    private TextView unit_label;
    private PlotView plotter_main;
    private PlotView plotter_sub;
    private VerticalTicsView vtics_main;
    private HorizontalTicsView htics_main1;
    private HorizontalTicsView htics_main2;
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
                updateValueTics(plotter_main, vtics_main,
                                plotter_sub.getSelectionRangeStart(),
                                plotter_sub.getSelectionRangeEnd(),
                                unit_params.getMainPlotStride(),
                                true);
            }
        };

    private GestureDetector gesture_detector;

    private Map<Object, List<Animator>> animator_map;

    private int measure_unit = 0;
    private TicsUtils.UnitParameters unit_params;
    private SharedPreferences.OnSharedPreferenceChangeListener unit_listener =
        new SharedPreferences.OnSharedPreferenceChangeListener() {
            public void onSharedPreferenceChanged(SharedPreferences pref,
                                                  String key) {
                if(PREF_UNIT.equals(key)) {
                    onUnitChanged(pref.getInt(key, 0));
                }
            }
        };

    @Override
    public void onCreate(Bundle savedState)
    {
        super.onCreate(savedState);

        setHasOptionsMenu(true);
        measure_unit = PreferenceManager
            .getDefaultSharedPreferences(getActivity())
            .getInt(PREF_UNIT, 0);
        unit_params = TicsUtils.getUnitParameters(getActivity(), measure_unit);
        PreferenceManager.getDefaultSharedPreferences(getActivity())
            .registerOnSharedPreferenceChangeListener(unit_listener);

        if(savedState != null) {
            plot_end = savedState.getLong(KEY_PLOT_END, plot_end);
        }
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();

        PreferenceManager.getDefaultSharedPreferences(getActivity())
            .unregisterOnSharedPreferenceChangeListener(unit_listener);
    }

    @Override
    public void onSaveInstanceState(Bundle state)
    {
        super.onSaveInstanceState(state);

        state.putLong(KEY_PLOT_END, plot_end);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedState)
    {
        View v = inflater.inflate(R.layout.atmosphere, container, false);

        data = LogData.getInstance(getActivity());
        records = new LogData.LogRecord[LogData.TOTAL_COUNT];
        record_cnt = 0;

        selection_holder = new SelectionHolder();

        handler = new Handler();
        gesture_detector = new GestureDetector(getActivity(),
                                               new GestureListener());
        animator_map = new HashMap<Object, List<Animator>>();

        unit_label = (TextView)v.findViewById(R.id.text_unit);

        plotter_main = (PlotView)v.findViewById(R.id.plotter_main);
        plotter_main.setOnTouchListener(new OnTouchMainPlotterListener());

        plotter_sub = (PlotView)v.findViewById(R.id.plotter_sub);
        plotter_sub.setOnTouchListener(new OnTouchSubPlotterListener());
        plotter_sub.setOnSelectionChangeListener(new SelectionListener());

        vtics_main = (VerticalTicsView)v.findViewById(R.id.tic_left);

        htics_main1 = (HorizontalTicsView)v.findViewById(R.id.tic_bottom1);
        htics_main2 = (HorizontalTicsView)v.findViewById(R.id.tic_bottom2);
        htics_sub = (HorizontalTicsView)v.findViewById(R.id.tic_sub_bottom);

        updateViewParams();

        return v;
    }

    @Override
    public void onResume()
    {
        super.onResume();

        SensorManager manager = (SensorManager)
            getActivity().getSystemService(Context.SENSOR_SERVICE);
        Sensor sensor = manager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        if(sensor == null) {
            new NoSensorDialogFragment().show(getFragmentManager(), "NoSensor");
            return;
        }

        // start logger service, if not yet started
        Intent logger_intent = new Intent(getActivity(), LoggerService.class)
            .setAction(LoggerService.ACTION_START_LOGGING);
        getActivity().startService(logger_intent);

        updateLogData();
    }

    @Override
    public void onPause()
    {
        super.onPause();

        handler.removeCallbacks(logdata_updater);
        handler.removeCallbacks(vtics_updater);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
    {
        inflater.inflate(R.menu.options_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        if(item.getItemId() == R.id.menu_unit) {
            new UnitChooserDialogFragment().show(getFragmentManager(), "unit");
            return true;
        }

        return false;
    }

    private void onUnitChanged(int unit_idx)
    {
        measure_unit = unit_idx;
        unit_params = TicsUtils.getUnitParameters(getActivity(), measure_unit);

        updateViewParams();
        updateLogData();
    }

    private void updateViewParams()
    {
        unit_label.setText(unit_params.getLabel());

        vtics_main.setMaxDigits(unit_params.getVTicsDigits());
        vtics_main.setValueStep(unit_params.getVTicsStep());
        vtics_main.setFormat(unit_params.getVTicsFormat());

        plotter_main.setValueStep(unit_params.getMainPlotMajorStep(),
                                  unit_params.getMainPlotMinorStep());
        plotter_sub.setValueStep(unit_params.getSubPlotMajorStep(),
                                 unit_params.getSubPlotMinorStep());
    }

    private void updateLogData()
    {
        record_cnt = data.readRecords(records);
        long cur_time = System.currentTimeMillis();

        TicsUtils.PressureUnitConverter conv =
            TicsUtils.getPressureUnitConverter(measure_unit);
        for(int i = 0; i < record_cnt; i++) {
            records[i].value = conv.convert(records[i].value);
        }

        plotter_main.setData(records, record_cnt);
        plotter_main.setNormalInterval(LoggerService.LOG_INTERVAL);
        updateValueTics(plotter_main, vtics_main,
                        plotter_sub.getSelectionRangeStart(),
                        plotter_sub.getSelectionRangeEnd(),
                        unit_params.getMainPlotStride(),
                        false);

        plotter_sub.setData(records, record_cnt);
        plotter_sub.setNormalInterval(LoggerService.LOG_INTERVAL);
        plotter_sub.setTimeRange(cur_time - PLOT_SUB_RANGE, cur_time);
        htics_sub.setTimeRange(cur_time - PLOT_SUB_RANGE, cur_time);
        updateValueTics(plotter_sub, null,
                        cur_time - PLOT_SUB_RANGE, cur_time,
                        unit_params.getSubPlotStride(),
                        false);

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

    private void updateValueTics(PlotView plotter, VerticalTicsView vtics,
                                 long start, long end, float step,
                                 boolean do_animate)
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

        float low = Math.round((min - step) / step) * step;
        float high = Math.round((max + step) / step) * step;
        float cur_low = plotter.getValueRangeMin();
        float cur_high = plotter.getValueRangeMax();

        if(low != cur_low || high != cur_high) {
            cancelAllAnimator(plotter);
            if(vtics != null) {
                cancelAllAnimator(vtics);
            }

            if(! do_animate || cur_low == cur_high) {
                plotter.setValueRange(low, high);
                if(vtics != null) {
                    vtics.setValueRange(low, high);
                }
            }
            else {
                startValueAnimation(plotter, "valueRangeMin", cur_low, low);
                startValueAnimation(plotter, "valueRangeMax", cur_high, high);
                if(vtics != null) {
                    startValueAnimation(vtics, "valueRangeMin", cur_low, low);
                    startValueAnimation(vtics, "valueRangeMax", cur_high, high);
                }
            }
        }
    }

    private void startValueAnimation(Object target, String prop,
                                     float from, float to)
    {
        ObjectAnimator anim = ObjectAnimator.ofFloat(target, prop, from, to);
        anim.setDuration(ANIMATION_DURATION);
        anim.setInterpolator(AnimationUtils.loadInterpolator(
                                 getActivity(),
                                 R.anim.plotter_value_interpolator));
        anim.start();
        addAnimator(target, anim);
    }

    private void startSelectionAnimation(Object target, String prop,
                                         int from, int to)
    {
        ObjectAnimator anim = ObjectAnimator.ofInt(target, prop, from, to);
        anim.setDuration(ANIMATION_DURATION);
        anim.setInterpolator(AnimationUtils.loadInterpolator(
                                 getActivity(),
                                 R.anim.plotter_selection_interpolator));
        anim.start();
        addAnimator(target, anim);
    }

    private void startFlingAnimation(Object target, String prop,
                                     int from, int to, long duration)
    {
        ObjectAnimator anim = ObjectAnimator.ofInt(target, prop, from, to);
        anim.setDuration(duration);
        anim.setInterpolator(AnimationUtils.loadInterpolator(
                                 getActivity(),
                                 R.anim.plotter_fling_interpolator));
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
            htics_main1.setTimeRange(start, end);
            htics_main2.setTimeRange(start, end);

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
            ViewConfiguration conf = ViewConfiguration.get(getActivity());
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

    public static class NoSensorDialogFragment extends DialogFragment
    {
        @Override
        public Dialog onCreateDialog(Bundle savedState)
        {
            return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.app_name)
                .setMessage(R.string.msg_no_sensor)
                .setPositiveButton(
                    android.R.string.ok,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            getActivity().finish();
                        }
                    })
                .create();
        }

        @Override
        public void onCancel(DialogInterface dialog)
        {
            getActivity().finish();
        }
    }

    public static class UnitChooserDialogFragment extends DialogFragment
    {
        @Override
        public Dialog onCreateDialog(Bundle savedState)
        {
            int check_idx = getPreferences().getInt(PREF_UNIT, 0);

            return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.menu_unit)
                .setSingleChoiceItems(
                    R.array.unit_entries, check_idx,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            setUnitPreference(which);
                            dialog.dismiss();
                        }
                    })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        }

        private SharedPreferences getPreferences()
        {
            return PreferenceManager.getDefaultSharedPreferences(getActivity());
        }

        private void setUnitPreference(int idx)
        {
            SharedPreferences.Editor editor = getPreferences().edit();
            editor.putInt(PREF_UNIT, idx);
            editor.apply();
        }
    }
}
