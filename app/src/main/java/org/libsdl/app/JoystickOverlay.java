package org.libsdl.app;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import org.ikemen_engine.ikemen_go.R;

public class JoystickOverlay extends View {
    private int deviceId = 0, axisX = 0, axisY = 0;
    private float centerX, centerY, stickX, stickY, radius;

    public JoystickOverlay(Context context) {
        super(context);
    }

    public JoystickOverlay(Context context, int deviceId, int axisX, int axisY) {
        super(context);
        this.deviceId = deviceId;
        this.axisX = axisX;
        this.axisY = axisY;
    }

    public JoystickOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        initFromAttrs(context, attrs);
    }

    public JoystickOverlay(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initFromAttrs(context, attrs);
    }

    private void initFromAttrs(Context context, AttributeSet attrs) {
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.JoystickOverlay);
            try {
                // The second parameter is the default value if the attribute isn't found
                axisX = a.getInt(R.styleable.JoystickOverlay_axisX, 0);
                axisY = a.getInt(R.styleable.JoystickOverlay_axisY, 1);
            } finally {
                a.recycle();
            }
        }
    }

    public void setAttrs(int deviceId, int axisX, int axisY) {
        this.deviceId = deviceId;
        this.axisX = axisX;
        this.axisY = axisY;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Provide a default size (200px) if none is specified
        int defSize = 200;
        int w = resolveSize(defSize, widthMeasureSpec);
        int h = resolveSize(defSize, heightMeasureSpec);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        centerX = w / 2f;
        centerY = h / 2f;
        radius = Math.min(w, h) / 3.0f;
        stickX = centerX;
        stickY = centerY;
    }

    public void updateVisualPos(float x, float y) {
        this.stickX = x;
        this.stickY = y;
        invalidate();
    }

    public void sendToSDL(float normX, float normY) {
        if (!isInEditMode()) {
            SDLControllerManager.onNativeJoy(deviceId, axisX, normX);
            SDLControllerManager.onNativeJoy(deviceId, axisY, normY);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // If we're in the editor, ensure we have values even if onSizeChanged didn't fire
        if (radius <= 0) {
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) return; // Still nothing to draw

            centerX = w / 2f;
            centerY = h / 2f;
            radius = Math.min(w, h) / 3.0f;
            stickX = centerX;
            stickY = centerY;
        }

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Draw Background
        p.setColor(0x44FFFFFF);
        canvas.drawCircle(centerX, centerY, radius, p);

        // Draw Stick Head
        p.setColor(0x88FFFFFF);
        canvas.drawCircle(stickX, stickY, radius / 2.0f, p);
    }
}
