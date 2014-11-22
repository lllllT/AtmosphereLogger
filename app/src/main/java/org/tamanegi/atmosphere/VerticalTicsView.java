package org.tamanegi.atmosphere;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.FloatMath;
import android.view.View;

public class VerticalTicsView extends View
{
    private Paint paint;

    private float value_min = 980;
    private float value_max = 1020;

    private float value_step = 10;
    private float max_digits = 4;
    private String number_format = "%f";

    public VerticalTicsView(Context context)
    {
        super(context);
        init(context);
    }

    public VerticalTicsView(Context context, AttributeSet attrs)
    {
        this(context, attrs, 0);
    }

    public VerticalTicsView(Context context, AttributeSet attrs, int defstyle)
    {
        super(context, attrs, defstyle);
        init(context);

        TypedArray vals = context.obtainStyledAttributes(
            attrs, R.styleable.VerticalTicsView, defstyle, 0);

        int tsize = vals.getDimensionPixelSize(
            R.styleable.VerticalTicsView_textSize, 15);
        int tcolor = vals.getColor(
            R.styleable.VerticalTicsView_textColor, 0xff000000);
        paint.setTextSize(tsize);
        paint.setColor(tcolor);

        vals.recycle();
    }

    private void init(Context context)
    {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextAlign(Paint.Align.RIGHT);
    }

    @Override
    public void onDraw(Canvas canvas)
    {
        int w = getWidth();
        int h = getHeight();
        float value_range = value_max - value_min;

        float min = FloatMath.floor(value_min / value_step + 0.5f) * value_step;
        float max = FloatMath.ceil(value_max / value_step + 0.5f) * value_step;
        for(float v = 0; min + v < max; v += value_step) {
            float y = h - ((min + v - value_min) / value_range) * h;

            float th2 = -paint.ascent() / 2;
            float tx = w;
            float ty = (y - th2 < 0 ? th2 * 2 :
                        y + th2 > h ? h :
                        y + th2);

            String str = String.format(number_format, min + v);
            canvas.drawText(str, tx, ty, paint);
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
            width = (int)(paint.measureText("0123456789") * max_digits / 10);

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

    public void setValueStep(float step)
    {
        value_step = step;
        invalidate();
    }

    public void setMaxDigits(int digits)
    {
        max_digits = digits;
        requestLayout();
    }

    public void setFormat(String format)
    {
        number_format = format;
        invalidate();
    }
}
