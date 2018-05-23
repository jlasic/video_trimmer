package com.example.bro.videotrimer;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

/**
 * Created by lasic on 09.03.2017..
 */

public class TimelineProgressUpdater extends View {

    private final static String TAG = "TimelineProgressUpdater";

    private int cursorColor;
    private int cursorWidth;
    private float progress;
    private boolean isRunning;

    public TimelineProgressUpdater(Context context) {
        this(context, null);
    }

    public TimelineProgressUpdater(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TimelineProgressUpdater(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        // prevent render is in edit mode
        if(isInEditMode()) return;

        TypedArray array = context.obtainStyledAttributes(attrs, R.styleable.TimelineProgressUpdater);
        try{
            cursorColor = getCursorColor(array);
            cursorWidth = getCursorWidth(array);
        }
        finally {
            array.recycle();
        }
        isRunning = false;
        progress = 0;

        setWillNotDraw(false);
    }

    private int getCursorWidth(TypedArray array) {
        return array.getDimensionPixelSize(R.styleable.TimelineProgressUpdater_cursor_width, 6);
    }

    private int getCursorColor(TypedArray array) {
        return array.getColor(R.styleable.TimelineProgressUpdater_cursor_color, Color.YELLOW);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if(isInEditMode()) return;

        if (isRunning)
            drawProgress(canvas);
    }

    private void drawProgress(Canvas canvas) {
        Rect cursor = new Rect();
        cursor.left = (int) (progress * getWidth() - 0.5f * cursorWidth);
        cursor.right = (int) (cursor.left + 0.5f * cursorWidth);
        cursor.top = 0;
        cursor.bottom = getHeight();

        Paint paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(cursorColor);
        paint.setAntiAlias(true);

        canvas.drawRect(cursor, paint);
    }

    public void setProgress(float progress){
        isRunning = true;
        this.progress = progress;
        postInvalidate();
    }
    public void cancle(){
        isRunning = false;
        postInvalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return false;
    }
}
