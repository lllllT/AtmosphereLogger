package org.tamanegi.atmosphere;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class HorizontalTicsView extends View
{
    private long range_start = 0;
    private long range_end = 0;

    private TicsUtils.TicsStep tics;
    private DateFormat date_format;
    private Paint paint;

    public HorizontalTicsView(Context context)
    {
        super(context);
        init(context);
    }

    public HorizontalTicsView(Context context, AttributeSet attrs)
    {
        this(context, attrs, 0);
    }

    public HorizontalTicsView(Context context, AttributeSet attrs, int defstyle)
    {
        super(context, attrs, defstyle);
        init(context);

        TypedArray vals = context.obtainStyledAttributes(
            attrs, R.styleable.HorizontalTicsView, defstyle, 0);

        int tsize = vals.getDimensionPixelSize(
            R.styleable.HorizontalTicsView_textSize, 15);
        int tcolor = vals.getColor(
            R.styleable.HorizontalTicsView_textColor, 0xff000000);
        paint.setTextSize(tsize);
        paint.setColor(tcolor);

        tics = TicsUtils.getTicsStep(
            vals.getInt(R.styleable.HorizontalTicsView_ticsStep, 0));
        date_format = TicsUtils.getTicsDateFormat(context, tics);

        vals.recycle();
    }

    private void init(Context context)
    {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    @Override
    public void onDraw(Canvas canvas)
    {
        int w = getWidth();
        float ty = -paint.ascent();

        double range = (double)(range_end - range_start);

        Calendar time = TicsUtils.roundTimeByTicsStep(range_start, tics);
        while(time.getTimeInMillis() <= range_end) {
            long t = time.getTimeInMillis();
            int x = (int)(((t - range_start) / range) * w);

            String str = date_format.format(new Date(t));
            float tx = x - paint.measureText(str) / 2;
            canvas.drawText(str, tx, ty, paint);

            TicsUtils.incrementCalendar(time, tics);
        }
    }

    @Override
    public void onMeasure(int widthSpec, int heightSpec)
    {
        int wmode = MeasureSpec.getMode(widthSpec);
        int hmode = MeasureSpec.getMode(heightSpec);
        int wsize = MeasureSpec.getSize(widthSpec);
        int hsize = MeasureSpec.getSize(heightSpec);

        int width;
        int height;

        if(wmode == MeasureSpec.EXACTLY) {
            width = wsize;
        }
        else {
            width = (int)paint.measureText(date_format.format(new Date()));

            if(wmode == MeasureSpec.AT_MOST) {
                width = Math.min(width, wsize);
            }
        }

        if(hmode == MeasureSpec.EXACTLY) {
            height = hsize;
        }
        else {
            height = (int)(paint.descent() - paint.ascent());

            if(hmode == MeasureSpec.AT_MOST) {
                height = Math.min(height, hsize);
            }
        }

        setMeasuredDimension(width, height);
    }

    public void setTimeRange(long start, long end)
    {
        range_start = start;
        range_end = end;

        invalidate();
    }
}
