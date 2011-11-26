package org.tamanegi.atmosphere;

import java.util.Calendar;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

public class PlotView extends View
{
    public interface OnSelectionChangeListener
    {
        public void onSelectionChanged(long start, long end);
    }

    private LogData.LogRecord[] records = null;
    private int record_cnt = 0;

    private Paint paint;
    private Drawable selection_drawable;
    private OnSelectionChangeListener selection_listener = null;

    private long normal_interval = 1000 * 60 * 5;
    private long range_start = 0;
    private long range_end = 0;
    private long selection_start = -1;
    private long selection_end = -1;

    private float value_min = 980;
    private float value_max = 1020;

    private int primary_color = 0xff808080;
    private int secondary_color = 0xff808080;
    private int border_color = 0xff606060;

    private TicsUtils.TicsStep main_tics = TicsUtils.TicsStep.HOUR;
    private int main_tics_color = 0xff202020;

    private int value_main_step = 100;
    private int value_sub_step = 10;

    private TicsUtils.TicsStep sub_tics = TicsUtils.TicsStep.HOUR;
    private int sub_tics_color = 0xff404040;

    public PlotView(Context context)
    {
        super(context);
        init(context);
    }

    public PlotView(Context context, AttributeSet attrs)
    {
        this(context, attrs, 0);
    }

    public PlotView(Context context, AttributeSet attrs, int defstyle)
    {
        super(context, attrs, defstyle);
        init(context);

        TypedArray vals = context.obtainStyledAttributes(
            attrs, R.styleable.PlotView, defstyle, 0);

        primary_color =
            vals.getColor(R.styleable.PlotView_primaryColor, primary_color);
        secondary_color =
            vals.getColor(R.styleable.PlotView_secondaryColor, secondary_color);
        border_color =
            vals.getColor(R.styleable.PlotView_borderColor, border_color);
        main_tics_color =
            vals.getColor(R.styleable.PlotView_mainTicsColor, main_tics_color);
        sub_tics_color =
            vals.getColor(R.styleable.PlotView_subTicsColor, sub_tics_color);

        main_tics = TicsUtils.getTicsStep(
            vals.getInt(R.styleable.PlotView_mainTicsStep, 0));
        sub_tics = TicsUtils.getTicsStep(
            vals.getInt(R.styleable.PlotView_subTicsStep, 0));

        value_main_step = vals.getInteger(
            R.styleable.PlotView_mainValueTicsStep, value_main_step);
        value_sub_step = vals.getInteger(
            R.styleable.PlotView_subValueTicsStep, value_sub_step);

        vals.recycle();
    }

    private void init(Context context)
    {
        paint = new Paint();
        selection_drawable =
            context.getResources().getDrawable(R.drawable.plotter_selection);
    }

    @Override
    public void onDraw(Canvas canvas)
    {
        int w = getWidth();
        int h = getHeight();

        double range = (double)(range_end - range_start);
        float value_range = value_max - value_min;

        paint.setAlpha(0xff);

        // tics: x-axis
        paint.setStrokeWidth(1);
        {
            paint.setColor(sub_tics_color);
            Calendar sub_time =
                TicsUtils.roundTimeByTicsStep(range_start, sub_tics);
            while(sub_time.getTimeInMillis() < range_end) {
                long t = sub_time.getTimeInMillis();
                int x = (int)(((t - range_start) / range) * w);
                canvas.drawLine(x, 0, x, h, paint);

                TicsUtils.incrementCalendar(sub_time, sub_tics);
            }

            paint.setColor(main_tics_color);
            Calendar main_time =
                TicsUtils.roundTimeByTicsStep(range_start, main_tics);
            while(main_time.getTimeInMillis() < range_end) {
                long t = main_time.getTimeInMillis();
                int x = (int)(((t - range_start) / range) * w);
                canvas.drawLine(x, 0, x, h, paint);

                TicsUtils.incrementCalendar(main_time, main_tics);
            }
        }

        // tics: y-axis
        paint.setStrokeWidth(1);
        if(value_sub_step > 0) {
            paint.setColor(sub_tics_color);
            int sub_min =
                (int)((value_min + value_sub_step - 1) / value_sub_step) *
                value_sub_step;
            for(int v = 0; v < value_range; v += value_sub_step) {
                int y = h -
                    (int)(((sub_min + v - value_min) / value_range) * h);
                canvas.drawLine(0, y, w, y, paint);
            }
        }
        if(value_main_step > 0) {
            paint.setColor(main_tics_color);
            int main_min =
                (int)((value_min + value_main_step - 1) / value_main_step) *
                value_main_step;
            for(int v = 0; v < value_range; v += value_main_step) {
                int y = h -
                    (int)(((main_min + v - value_min) / value_range) * h);
                canvas.drawLine(0, y, w, y, paint);
            }
        }

        // border
        paint.setColor(border_color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        canvas.drawRect(0, 0, w, h, paint);

        // values
        paint.setColor(primary_color);
        paint.setStrokeWidth(2);

        boolean is_first = true;
        int prev_x = 0;
        int prev_y = 0;
        for(int i = 0; i < record_cnt; i++) {
            if(records[i].time < range_start ||
               records[i].time > range_end) {
                continue;
            }

            int x = (int)(((records[i].time - range_start) / range) * w);
            int y = h -
                (int)(((records[i].value - value_min) / value_range) * h);

            if(! is_first) {
                int alpha = (int)
                    (((float)normal_interval /
                      (records[i].time - records[i - 1].time)) * 0xff);
                if(alpha < 0x80) {
                    paint.setAlpha(alpha);
                }

                canvas.drawLine(prev_x, prev_y, x, y, paint);

                if(alpha < 0x80) {
                    paint.setAlpha(0xff);
                }
            }
            is_first = false;
            prev_x = x;
            prev_y = y;
        }

        // selection
        if(selection_start >= 0 && selection_end >= 0) {
            int xs = (int)(((selection_start - range_start) / range) * w);
            int xe = (int)(((selection_end - range_start) / range) * w);
            selection_drawable.setBounds(xs, 0, xe, h);
            selection_drawable.draw(canvas);
        }
    }

    public void setData(LogData.LogRecord[] records, int cnt)
    {
        this.records = records;
        this.record_cnt = cnt;
        invalidate();
    }

    public void setOnSelectionChangeListener(OnSelectionChangeListener listener)
    {
        selection_listener = listener;
    }

    private void selectionChanged()
    {
        if(selection_listener == null) {
            return;
        }

        selection_listener.onSelectionChanged(selection_start, selection_end);
    }

    public void setNormalInterval(long interval)
    {
        normal_interval = interval;
        invalidate();
    }

    public void setTimeRange(long start, long end)
    {
        range_start = start;
        range_end = end;

        long org_start = selection_start;
        long org_end = selection_end;
        adjustSelectionRange();

        if(org_start != selection_start || org_end != selection_end) {
            selectionChanged();
        }
        invalidate();
    }

    public long getTimeRangeStart()
    {
        return range_start;
    }

    public long getTimeRangeEnd()
    {
        return range_end;
    }

    public void setSelectionRange(long start, long end)
    {
        if(selection_start == start && selection_end == end) {
            return;
        }

        long org_start = selection_start;
        long org_end = selection_end;
        selection_start = start;
        selection_end = end;
        adjustSelectionRange();

        if(org_start != selection_start || org_end != selection_end) {
            selectionChanged();
            invalidate();
        }
    }

    public long getSelectionRangeStart()
    {
        return selection_start;
    }

    public long getSelectionRangeEnd()
    {
        return selection_end;
    }

    private void adjustSelectionRange()
    {
        if(selection_start < 0 || selection_end < 0) {
            return;
        }

        long org_start = selection_start;
        long org_end = selection_end;
        long org_range = org_end - org_start;

        selection_start =
            Math.max(Math.min(selection_start, range_end), range_start);
        selection_end =
            Math.max(Math.min(selection_end, range_end), range_start);

        if(range_end - range_start >= org_end - org_start) {
            if(selection_start + org_range <= range_end) {
                selection_end = selection_start + org_range;
            }
            else {
                selection_start = selection_end - org_range;
            }
        }
    }

    public void setValueRange(float min, float max)
    {
        value_min = min;
        value_max = max;
        invalidate();
    }

    public void setValueRangeMin(float min)
    {
        value_min = min;
        invalidate();
    }

    public void setValueRangeMax(float max)
    {
        value_max = max;
        invalidate();
    }

    public float getValueRangeMin()
    {
        return value_min;
    }

    public float getValueRangeMax()
    {
        return value_max;
    }
}
