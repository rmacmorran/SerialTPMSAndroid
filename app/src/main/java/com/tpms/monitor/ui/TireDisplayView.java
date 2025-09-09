package com.tpms.monitor.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.tpms.monitor.model.TPMSSensor;

/**
 * Custom view to display tire with pressure and temperature indicators
 */
public class TireDisplayView extends View {
    private static final String TAG = "TireDisplayView";
    
    // Paint objects
    private Paint tirePaint;
    private Paint rimPaint;
    private Paint textPaint;
    private Paint pressureBarPaint;
    private Paint temperatureBarPaint;
    private Paint barBackgroundPaint;
    private Paint markerPaint;
    
    // Dimensions
    private float tireWidth;
    private float tireHeight;
    private float barWidth;
    private float barHeight;
    private float centerX;
    private float centerY;
    
    // Data
    private TPMSSensor.TirePosition tirePosition;
    private TPMSSensor sensorData;
    
    // Animation
    private ValueAnimator flashAnimator;
    private boolean isFlashing = false;
    private int flashAlpha = 255;
    
    // Colors
    private static final int COLOR_TIRE_NORMAL = Color.parseColor("#404040");
    private static final int COLOR_TIRE_ALARM = Color.parseColor("#FF4444");
    private static final int COLOR_RIM = Color.parseColor("#C0C0C0");
    private static final int COLOR_GREEN = Color.parseColor("#4CAF50");
    private static final int COLOR_YELLOW = Color.parseColor("#FFC107");
    private static final int COLOR_RED = Color.parseColor("#F44336");
    private static final int COLOR_BAR_BACKGROUND = Color.parseColor("#EEEEEE");
    private static final int COLOR_TEXT = Color.parseColor("#212121");
    
    public TireDisplayView(Context context) {
        super(context);
        init();
    }
    
    public TireDisplayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public TireDisplayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        // Initialize paint objects
        tirePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tirePaint.setColor(COLOR_TIRE_NORMAL);
        tirePaint.setStyle(Paint.Style.FILL);
        
        rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        rimPaint.setColor(COLOR_RIM);
        rimPaint.setStyle(Paint.Style.FILL);
        
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(COLOR_TEXT);
        textPaint.setTextSize(24);
        textPaint.setTextAlign(Paint.Align.CENTER);
        
        pressureBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        temperatureBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        
        barBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        barBackgroundPaint.setColor(COLOR_BAR_BACKGROUND);
        barBackgroundPaint.setStyle(Paint.Style.FILL);
        
        markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        markerPaint.setColor(Color.BLACK);
        markerPaint.setStyle(Paint.Style.FILL);
        markerPaint.setStrokeWidth(4);
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        
        centerX = w / 2f;
        centerY = h / 2f;
        
        // Calculate dimensions
        float minDimension = Math.min(w, h);
        tireWidth = minDimension * 0.6f;
        tireHeight = minDimension * 0.3f;
        
        barWidth = 20;
        barHeight = minDimension * 0.8f;
        
        // Adjust text size based on view size
        textPaint.setTextSize(Math.max(12, minDimension * 0.08f));
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (getWidth() == 0 || getHeight() == 0) {
            return;
        }
        
        // Draw tire position label
        drawTireLabel(canvas);
        
        // Draw tire shape
        drawTire(canvas);
        
        // Draw pressure and temperature bars
        drawPressureBar(canvas);
        drawTemperatureBar(canvas);
        
        // Draw sensor data text
        drawSensorData(canvas);
    }
    
    private void drawTireLabel(Canvas canvas) {
        if (tirePosition == null) return;
        
        String label = tirePosition.getShortName();
        float labelY = centerY - tireHeight/2 - 40;
        
        canvas.drawText(label, centerX, labelY, textPaint);
    }
    
    private void drawTire(Canvas canvas) {
        // Outer tire (rubber)
        RectF tireRect = new RectF(
            centerX - tireWidth/2,
            centerY - tireHeight/2,
            centerX + tireWidth/2,
            centerY + tireHeight/2
        );
        
        // Set tire color based on alarm status
        if (isFlashing) {
            tirePaint.setAlpha(flashAlpha);
            tirePaint.setColor(COLOR_TIRE_ALARM);
        } else {
            tirePaint.setAlpha(255);
            tirePaint.setColor(COLOR_TIRE_NORMAL);
        }
        
        canvas.drawRoundRect(tireRect, 20, 20, tirePaint);
        
        // Inner rim
        RectF rimRect = new RectF(
            centerX - tireWidth/3,
            centerY - tireHeight/3,
            centerX + tireWidth/3,
            centerY + tireHeight/3
        );
        
        canvas.drawRoundRect(rimRect, 10, 10, rimPaint);
    }
    
    private void drawPressureBar(Canvas canvas) {
        if (sensorData == null) return;
        
        // Bar position (left side)
        float barX = centerX - tireWidth/2 - 40;
        float barTop = centerY - barHeight/2;
        float barBottom = centerY + barHeight/2;
        
        // Background
        RectF barRect = new RectF(barX, barTop, barX + barWidth, barBottom);
        canvas.drawRoundRect(barRect, 5, 5, barBackgroundPaint);
        
        // Calculate pressure percentage and color
        float pressure = sensorData.getPressurePsi();
        float minPressure = sensorData.getPressureMinThreshold();
        float maxPressure = sensorData.getPressureMaxThreshold();
        float targetPressure = sensorData.getTargetPressure();
        
        // Determine bar color based on status
        int barColor = getStatusColor(pressure, minPressure, maxPressure, targetPressure);
        pressureBarPaint.setColor(barColor);
        
        // Calculate bar fill height (based on current pressure relative to range)
        float rangeMin = Math.max(0, targetPressure - (targetPressure * 0.5f)); // Show range from 50% to 150% of target
        float rangeMax = targetPressure + (targetPressure * 0.5f);
        float fillPercent = Math.max(0, Math.min(1, (pressure - rangeMin) / (rangeMax - rangeMin)));
        
        float fillHeight = barHeight * fillPercent;
        RectF fillRect = new RectF(barX + 2, barBottom - fillHeight, barX + barWidth - 2, barBottom - 2);
        canvas.drawRoundRect(fillRect, 3, 3, pressureBarPaint);
        
        // Draw target pressure marker
        float targetPercent = (targetPressure - rangeMin) / (rangeMax - rangeMin);
        float markerY = barBottom - (barHeight * targetPercent);
        canvas.drawLine(barX - 5, markerY, barX + barWidth + 5, markerY, markerPaint);
        
        // Label
        canvas.drawText("P", barX + barWidth/2, barTop - 10, textPaint);
        canvas.drawText(String.format("%.0f", pressure), barX + barWidth/2, barBottom + 25, textPaint);
    }
    
    private void drawTemperatureBar(Canvas canvas) {
        if (sensorData == null) return;
        
        // Bar position (right side)
        float barX = centerX + tireWidth/2 + 20;
        float barTop = centerY - barHeight/2;
        float barBottom = centerY + barHeight/2;
        
        // Background
        RectF barRect = new RectF(barX, barTop, barX + barWidth, barBottom);
        canvas.drawRoundRect(barRect, 5, 5, barBackgroundPaint);
        
        // Calculate temperature percentage and color
        float temperature = sensorData.getTemperatureF();
        float minTemp = sensorData.getTemperatureMinThreshold();
        float maxTemp = sensorData.getTemperatureMaxThreshold();
        float targetTemp = sensorData.getTargetTemperature();
        
        // Determine bar color based on status
        int barColor = getStatusColor(temperature, minTemp, maxTemp, targetTemp);
        temperatureBarPaint.setColor(barColor);
        
        // Calculate bar fill height (based on temperature range)
        float rangeMin = Math.max(32, targetTemp - 40); // Show reasonable temperature range
        float rangeMax = targetTemp + 40;
        float fillPercent = Math.max(0, Math.min(1, (temperature - rangeMin) / (rangeMax - rangeMin)));
        
        float fillHeight = barHeight * fillPercent;
        RectF fillRect = new RectF(barX + 2, barBottom - fillHeight, barX + barWidth - 2, barBottom - 2);
        canvas.drawRoundRect(fillRect, 3, 3, temperatureBarPaint);
        
        // Draw target temperature marker
        float targetPercent = (targetTemp - rangeMin) / (rangeMax - rangeMin);
        float markerY = barBottom - (barHeight * targetPercent);
        canvas.drawLine(barX - 5, markerY, barX + barWidth + 5, markerY, markerPaint);
        
        // Label
        canvas.drawText("T", barX + barWidth/2, barTop - 10, textPaint);
        canvas.drawText(String.format("%.0f°", temperature), barX + barWidth/2, barBottom + 25, textPaint);
    }
    
    private int getStatusColor(float value, float minThreshold, float maxThreshold, float target) {
        if (value < minThreshold || value > maxThreshold) {
            return COLOR_RED;
        }
        
        // Calculate deviation from target
        float deviation = Math.abs(value - target) / target;
        
        if (deviation < 0.05f) { // Within 5% of target
            return COLOR_GREEN;
        } else if (deviation < 0.15f) { // Within 15% of target
            return COLOR_YELLOW;
        } else {
            return COLOR_RED;
        }
    }
    
    private void drawSensorData(Canvas canvas) {
        if (sensorData == null) {
            // No sensor assigned
            canvas.drawText("No Sensor", centerX, centerY + tireHeight/2 + 60, textPaint);
            return;
        }
        
        // Sensor ID and status
        String sensorText = String.format("ID: %d", sensorData.getSensorId());
        canvas.drawText(sensorText, centerX, centerY + tireHeight/2 + 40, textPaint);
        
        String statusText = sensorData.getStatusDescription();
        canvas.drawText(statusText, centerX, centerY + tireHeight/2 + 65, textPaint);
    }
    
    public void setTirePosition(TPMSSensor.TirePosition position) {
        this.tirePosition = position;
        invalidate();
    }
    
    public void updateSensorData(TPMSSensor sensor) {
        this.sensorData = sensor;
        invalidate();
    }
    
    public void startFlashing() {
        if (isFlashing) return;
        
        isFlashing = true;
        flashAnimator = ValueAnimator.ofInt(255, 100, 255);
        flashAnimator.setDuration(500);
        flashAnimator.setRepeatCount(5); // Flash 3 times
        flashAnimator.setRepeatMode(ValueAnimator.REVERSE);
        
        flashAnimator.addUpdateListener(animation -> {
            flashAlpha = (int) animation.getAnimatedValue();
            invalidate();
        });
        
        flashAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                isFlashing = false;
                flashAlpha = 255;
                invalidate();
            }
        });
        
        flashAnimator.start();
    }
    
    public void stopFlashing() {
        if (flashAnimator != null && flashAnimator.isRunning()) {
            flashAnimator.cancel();
        }
        isFlashing = false;
        flashAlpha = 255;
        invalidate();
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (flashAnimator != null && flashAnimator.isRunning()) {
            flashAnimator.cancel();
        }
    }
}
