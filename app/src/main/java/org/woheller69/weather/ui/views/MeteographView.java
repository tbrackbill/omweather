package org.woheller69.weather.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import org.woheller69.weather.database.HourlyForecast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class MeteographView extends View {

    // Rain cap: 0.4 in/hr = 10.16 mm/hr; wind cap: 30 mph = 13.4 m/s
    private static final float RAIN_CAP_MM = 10.16f;
    private static final float WIND_CAP_MS = 13.4f;

    // Temperature color thresholds in °C (20/55/60/80/100 °F)
    private static final float T_PURPLE = -6.7f;
    private static final float T_BLUE   = 12.8f;
    private static final float T_TEAL   = 15.6f;
    private static final float T_GREEN  = 26.7f;
    private static final float T_ORANGE = 37.8f;

    private static final int COL_TEMP_PURPLE = 0xFF7B1FA2;
    private static final int COL_TEMP_BLUE   = 0xFF1565C0;
    private static final int COL_TEMP_TEAL   = 0xFF00838F;
    private static final int COL_TEMP_GREEN  = 0xFF388E3C;
    private static final int COL_TEMP_ORANGE = 0xFFD84315;
    private static final int COL_TEMP_MAROON = 0xFF880E4F;

    private static final int COLOR_RH          = 0x5529B6F6;

    private static final int COLOR_CLOUD      = 0xFFE6DFD0;
    private static final int COLOR_SUN        = 0xFFFFF7C0;
    private static final int COLOR_RAIN_FILL  = 0xCC1B4CF0;
    private static final int COLOR_RAIN_LABEL = 0xFF1B4CF0;
    private static final int COLOR_WIND       = 0xFF4CAF50;
    private static final int COLOR_COLD_SHADE = 0x556fa1d2;
    private static final int COLOR_WARM_SHADE = 0x55e01530;
    private static final int COLOR_LABEL      = 0xFF024265;
    private static final int COLOR_TICK_MAJOR = 0x70B0BEC8;
    private static final int COLOR_TICK_MINOR = 0x48B0BEC8;
    private static final int COLOR_TICK_MICRO = 0x28B0BEC8;

    private List<HourlyForecast> allForecasts;
    private List<HourlyForecast> plotForecasts;
    private float[] yesterdayTemp;
    private int timezoneOffsetMs = 0;
    private boolean useFahrenheit = false;
    private boolean useMetric = true;

    private final Paint cloudPaint     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sunPaint       = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rainPaint      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint windPaint      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rhPaint        = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tempSegPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tempHiLoPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint coldShadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint warmShadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dayLabelPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rainLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint windLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tempLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint legendPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint        = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint majorTickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint minorTickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint microTickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path  sunPath   = new Path();
    private final Path  rainPath  = new Path();
    private final Path  windPath  = new Path();
    private final Path  rhPath    = new Path();
    private final Path  shadePath = new Path();
    private final RectF labelBg   = new RectF();

    private float mZoom = 1f;
    private float mPanFrac = 0f;
    private ScaleGestureDetector mScaleDetector;
    private float mLastTouchX;
    private final RectF mResetBounds = new RectF();
    private final Paint mResetBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mResetTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float plotLeft, plotRight;
    private float p1Top, p1Bot, p2Top, p2Bot;
    private float labelBandMid;

    public MeteographView(Context context) { super(context); init(context); }
    public MeteographView(Context context, AttributeSet attrs) { super(context, attrs); init(context); }
    public MeteographView(Context context, AttributeSet attrs, int def) { super(context, attrs, def); init(context); }

    private void init(Context context) {
        float dp = context.getResources().getDisplayMetrics().density;

        cloudPaint.setColor(COLOR_CLOUD); cloudPaint.setStyle(Paint.Style.FILL);
        sunPaint.setColor(COLOR_SUN);     sunPaint.setStyle(Paint.Style.FILL);
        rainPaint.setColor(COLOR_RAIN_FILL); rainPaint.setStyle(Paint.Style.FILL);

        windPaint.setColor(COLOR_WIND);
        windPaint.setStyle(Paint.Style.STROKE);
        windPaint.setStrokeWidth(2f * dp);
        windPaint.setStrokeCap(Paint.Cap.ROUND);
        windPaint.setStrokeJoin(Paint.Join.ROUND);

        rhPaint.setColor(COLOR_RH);
        rhPaint.setStyle(Paint.Style.STROKE);
        rhPaint.setStrokeWidth(1.2f * dp);
        rhPaint.setStrokeCap(Paint.Cap.ROUND);
        rhPaint.setStrokeJoin(Paint.Join.ROUND);
        rhPaint.setPathEffect(new DashPathEffect(new float[]{3f * dp, 3f * dp}, 0));

        tempSegPaint.setStyle(Paint.Style.STROKE);
        tempSegPaint.setStrokeWidth(3f * dp);
        tempSegPaint.setStrokeCap(Paint.Cap.ROUND);

        tempHiLoPaint.setTextSize(7f * dp);
        tempHiLoPaint.setTextAlign(Paint.Align.CENTER);

        coldShadePaint.setColor(COLOR_COLD_SHADE); coldShadePaint.setStyle(Paint.Style.FILL);
        warmShadePaint.setColor(COLOR_WARM_SHADE); warmShadePaint.setStyle(Paint.Style.FILL);

        dayLabelPaint.setColor(COLOR_LABEL);
        dayLabelPaint.setTextSize(11f * dp);
        dayLabelPaint.setTextAlign(Paint.Align.LEFT);

        rainLabelPaint.setColor(COLOR_RAIN_LABEL);
        rainLabelPaint.setTextSize(8f * dp);

        windLabelPaint.setColor(COLOR_WIND);
        windLabelPaint.setTextSize(8f * dp);

        tempLabelPaint.setColor(COLOR_LABEL);
        tempLabelPaint.setTextSize(8f * dp);

        legendPaint.setTextSize(7f * dp);
        legendPaint.setTextAlign(Paint.Align.LEFT);

        bgPaint.setColor(0xBBFFFFFF);
        bgPaint.setStyle(Paint.Style.FILL);

        majorTickPaint.setColor(COLOR_TICK_MAJOR);
        majorTickPaint.setStyle(Paint.Style.STROKE);
        majorTickPaint.setStrokeWidth(1.5f * dp);

        minorTickPaint.setColor(COLOR_TICK_MINOR);
        minorTickPaint.setStyle(Paint.Style.STROKE);
        minorTickPaint.setStrokeWidth(1f * dp);

        microTickPaint.setColor(COLOR_TICK_MICRO);
        microTickPaint.setStyle(Paint.Style.STROKE);
        microTickPaint.setStrokeWidth(0.8f * dp);
        float dash = 4f * dp;
        microTickPaint.setPathEffect(new DashPathEffect(new float[]{dash, dash}, 0));

        mResetBgPaint.setColor(0xDDFFFFFF);
        mResetBgPaint.setStyle(Paint.Style.FILL);
        mResetTextPaint.setColor(0xFF1565C0);
        mResetTextPaint.setTextSize(8f * dp);
        mResetTextPaint.setTextAlign(Paint.Align.CENTER);

        mScaleDetector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector d) {
                        float newZoom = Math.max(1f, Math.min(20f, mZoom * d.getScaleFactor()));
                        float focusFrac = (d.getFocusX() - plotLeft)
                                / Math.max(plotRight - plotLeft, 1f);
                        mPanFrac += focusFrac / mZoom - focusFrac / newZoom;
                        mZoom = newZoom;
                        clampPan();
                        invalidate();
                        return true;
                    }
                });
        setClickable(true);
    }

    public void setData(List<HourlyForecast> allHourly, int tzOffsetMs, boolean fahrenheit, boolean useMetric) {
        this.allForecasts     = allHourly;
        this.timezoneOffsetMs = tzOffsetMs;
        this.useFahrenheit    = fahrenheit;
        this.useMetric        = useMetric;
        prepareData();
        invalidate();
    }

    private void prepareData() {
        if (allForecasts == null || allForecasts.isEmpty()) return;
        long cutoff = System.currentTimeMillis() - 60L * 60 * 1000;
        plotForecasts = new ArrayList<>();
        for (HourlyForecast f : allForecasts) {
            if (f.getForecastTime() >= cutoff) plotForecasts.add(f);
        }
        yesterdayTemp = new float[plotForecasts.size()];
        Arrays.fill(yesterdayTemp, Float.NaN);
        for (int i = 0; i < plotForecasts.size(); i++) {
            long target = plotForecasts.get(i).getForecastTime() - 24L * 3600 * 1000;
            for (HourlyForecast h : allForecasts) {
                if (Math.abs(h.getForecastTime() - target) <= 30L * 60 * 1000) {
                    yesterdayTemp[i] = h.getTemperature();
                    break;
                }
            }
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float dp   = getResources().getDisplayMetrics().density;
        float band = 17f * dp;
        plotLeft  = 0f;
        plotRight = w;
        float usableH = h - band;
        p1Top = 0f;
        p1Bot = usableH * 0.52f;
        p2Top = p1Bot + band;
        p2Bot = h;
        labelBandMid = p1Bot + band * 0.72f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (plotForecasts == null || plotForecasts.isEmpty()) return;
        int N = plotForecasts.size();
        float[] xs = new float[N];
        float viewW = plotRight - plotLeft;
        for (int i = 0; i < N; i++) {
            float dataFrac = (float) i / Math.max(N - 1, 1);
            xs[i] = plotLeft + (dataFrac - mPanFrac) * mZoom * viewW;
        }

        drawPanel1Background(canvas, xs, N);
        drawTickGrid(canvas, xs, N);
        drawPanel1Data(canvas, xs, N);
        drawDayLabels(canvas, xs, N);
        drawPanel2(canvas, xs, N);
        drawPanel1Labels(canvas);
        drawPanel2Labels(canvas);
        drawPanel2Legend(canvas);
        if (mZoom > 1.01f) drawResetBox(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mScaleDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mLastTouchX = event.getX();
                break;
            case MotionEvent.ACTION_MOVE:
                if (!mScaleDetector.isInProgress()) {
                    float dx = event.getX() - mLastTouchX;
                    mPanFrac -= dx / Math.max(plotRight - plotLeft, 1f) / mZoom;
                    clampPan();
                    invalidate();
                }
                mLastTouchX = event.getX();
                break;
            case MotionEvent.ACTION_UP:
                if (mZoom > 1.01f && mResetBounds.contains(event.getX(), event.getY())) {
                    mZoom = 1f;
                    mPanFrac = 0f;
                    invalidate();
                }
                break;
        }
        return true;
    }

    private void clampPan() {
        float maxPan = Math.max(0f, 1f - 1f / mZoom);
        mPanFrac = Math.max(0f, Math.min(maxPan, mPanFrac));
    }

    private void drawResetBox(Canvas canvas) {
        float dp  = getResources().getDisplayMetrics().density;
        float pad = 3f * dp;
        float ts  = mResetTextPaint.getTextSize();
        String label = "⊖ reset";
        float w = mResetTextPaint.measureText(label) + pad * 2f;
        float h = ts + pad * 2f;
        mResetBounds.set(plotRight - w - 2f * dp, 2f * dp,
                plotRight - 2f * dp, 2f * dp + h);
        canvas.drawRoundRect(mResetBounds, 4f * dp, 4f * dp, mResetBgPaint);
        canvas.drawText(label, mResetBounds.centerX(),
                mResetBounds.top + pad + ts * 0.85f, mResetTextPaint);
    }

    private void drawPanel1Background(Canvas canvas, float[] xs, int N) {
        canvas.drawRect(plotLeft, p1Top, plotRight, p1Bot, cloudPaint);
        float[] cloudBY = new float[N];
        for (int i = 0; i < N; i++) {
            float cover = plotForecasts.get(i).getCloudCover();
            if (cover < 0) cover = cloudCoverFromWeatherCode(plotForecasts.get(i).getWeatherID());
            cover = Math.max(0f, Math.min(100f, cover));
            cloudBY[i] = p1Top + (cover / 100f) * (p1Bot - p1Top);
        }
        sunPath.reset();
        sunPath.moveTo(xs[0], cloudBY[0]);
        for (int i = 1; i < N; i++) sunPath.lineTo(xs[i], cloudBY[i]);
        sunPath.lineTo(xs[N - 1], p1Bot);
        sunPath.lineTo(xs[0], p1Bot);
        sunPath.close();
        canvas.drawPath(sunPath, sunPaint);
    }

    private void drawTickGrid(Canvas canvas, float[] xs, int N) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        for (int i = 0; i < N; i++) {
            long localTime = plotForecasts.get(i).getForecastTime() + timezoneOffsetMs;
            cal.setTimeInMillis(localTime);
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            if (hour == 0)          canvas.drawLine(xs[i], p1Top, xs[i], p2Bot, majorTickPaint);
            else if (hour == 12)    canvas.drawLine(xs[i], p1Top, xs[i], p2Bot, minorTickPaint);
            else if (hour % 4 == 0) canvas.drawLine(xs[i], p1Top, xs[i], p2Bot, microTickPaint);
        }
    }

    private void drawPanel1Data(Canvas canvas, float[] xs, int N) {
        float panelH = p1Bot - p1Top;
        rainPath.reset();
        rainPath.moveTo(xs[0], p1Bot);
        for (int i = 0; i < N; i++) {
            float rain  = Math.min(plotForecasts.get(i).getPrecipitation(), RAIN_CAP_MM);
            rainPath.lineTo(xs[i], p1Bot - (rain / RAIN_CAP_MM) * panelH);
        }
        rainPath.lineTo(xs[N - 1], p1Bot);
        rainPath.close();
        canvas.drawPath(rainPath, rainPaint);

        windPath.reset();
        for (int i = 0; i < N; i++) {
            float wind  = Math.min(plotForecasts.get(i).getWindSpeed(), WIND_CAP_MS);
            float windY = p1Bot - (wind / WIND_CAP_MS) * panelH;
            if (i == 0) windPath.moveTo(xs[i], windY);
            else        windPath.lineTo(xs[i], windY);
        }
        canvas.drawPath(windPath, windPaint);

        rhPath.reset();
        for (int i = 0; i < N; i++) {
            float rh  = Math.max(0f, Math.min(100f, plotForecasts.get(i).getHumidity()));
            float rhY = p1Bot - (rh / 100f) * panelH;
            if (i == 0) rhPath.moveTo(xs[i], rhY);
            else        rhPath.lineTo(xs[i], rhY);
        }
        canvas.drawPath(rhPath, rhPaint);
    }

    private void drawDayLabels(Canvas canvas, float[] xs, int N) {
        SimpleDateFormat sdf = new SimpleDateFormat("EEE", Locale.getDefault());
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        float dp = getResources().getDisplayMetrics().density;
        int lastDay = -1;
        for (int i = 0; i < N; i++) {
            long localTime = plotForecasts.get(i).getForecastTime() + timezoneOffsetMs;
            cal.setTimeInMillis(localTime);
            int day = cal.get(Calendar.DAY_OF_YEAR);
            if (day != lastDay) {
                canvas.drawText(sdf.format(new Date(localTime)), xs[i] + 2f * dp, labelBandMid, dayLabelPaint);
                lastDay = day;
            }
        }
    }

    private void drawPanel2(Canvas canvas, float[] xs, int N) {
        float tMin =  Float.MAX_VALUE, tMax = -Float.MAX_VALUE;
        for (int i = 0; i < N; i++) {
            tMin = Math.min(tMin, plotForecasts.get(i).getTemperature());
            tMax = Math.max(tMax, plotForecasts.get(i).getTemperature());
            if (!Float.isNaN(yesterdayTemp[i])) {
                tMin = Math.min(tMin, yesterdayTemp[i]);
                tMax = Math.max(tMax, yesterdayTemp[i]);
            }
        }
        if (tMax <= tMin) tMax = tMin + 1f;

        float dp    = getResources().getDisplayMetrics().density;
        float pad   = 6f * dp;
        float drawH = (p2Bot - p2Top) - 2 * pad;

        // Horizontal gridlines at round temperature values
        Paint gridPaint = new Paint();
        gridPaint.setColor(0x28B0BEC8);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(0.8f * dp);
        for (float g : computeGridTempsC(tMin, tMax)) {
            float gy = tempY(g, tMin, tMax, drawH, pad);
            canvas.drawLine(plotLeft, gy, plotRight, gy, gridPaint);
        }

        // Yesterday shading
        for (int i = 0; i < N - 1; i++) {
            if (Float.isNaN(yesterdayTemp[i]) || Float.isNaN(yesterdayTemp[i + 1])) continue;
            float t0 = plotForecasts.get(i).getTemperature(), t1 = plotForecasts.get(i + 1).getTemperature();
            float r0 = yesterdayTemp[i], r1 = yesterdayTemp[i + 1];
            float curY0 = tempY(t0, tMin, tMax, drawH, pad), curY1 = tempY(t1, tMin, tMax, drawH, pad);
            float refY0 = tempY(r0, tMin, tMax, drawH, pad), refY1 = tempY(r1, tMin, tMax, drawH, pad);
            boolean c0 = t0 < r0, c1 = t1 < r1;
            if (c0 == c1) {
                drawShadeSegment(canvas, xs[i], xs[i + 1], curY0, curY1, refY0, refY1, c0);
            } else {
                float denom = (t1 - t0) - (r1 - r0);
                float frac  = (Math.abs(denom) < 1e-6f) ? 0.5f : Math.max(0f, Math.min(1f, (r0 - t0) / denom));
                float xMid  = xs[i] + frac * (xs[i + 1] - xs[i]);
                float yMid  = tempY(t0 + frac * (t1 - t0), tMin, tMax, drawH, pad);
                drawShadeSegment(canvas, xs[i], xMid,        curY0, yMid, refY0, yMid, c0);
                drawShadeSegment(canvas, xMid,  xs[i + 1],   yMid, curY1, yMid, refY1, c1);
            }
        }

        // Multi-color temperature line drawn segment-by-segment
        float[] tempYs = new float[N];
        for (int i = 0; i < N; i++) tempYs[i] = tempY(plotForecasts.get(i).getTemperature(), tMin, tMax, drawH, pad);
        for (int i = 0; i < N - 1; i++) {
            float avgC = (plotForecasts.get(i).getTemperature() + plotForecasts.get(i + 1).getTemperature()) / 2f;
            tempSegPaint.setColor(tempColor(avgC));
            canvas.drawLine(xs[i], tempYs[i], xs[i + 1], tempYs[i + 1], tempSegPaint);
        }

        // Daily high/low labels
        drawPanel2DayExtremes(canvas, xs, N, tMin, tMax, drawH, pad, dp);
    }

    private float[] computeGridTempsC(float tMin, float tMax) {
        List<Float> lines = new ArrayList<>();
        if (useFahrenheit) {
            float tMinF = tMin * 9f / 5f + 32f;
            float tMaxF = tMax * 9f / 5f + 32f;
            float stepF = 10f;
            if ((tMaxF - tMinF) / stepF > 7f) stepF = 20f;
            float firstF = (float) Math.ceil(tMinF / stepF) * stepF;
            for (float gF = firstF; gF <= tMaxF + 0.01f; gF += stepF) {
                lines.add((gF - 32f) * 5f / 9f);
            }
        } else {
            float stepC = 5f;
            if ((tMax - tMin) / stepC > 7f) stepC = 10f;
            float first = (float) Math.ceil(tMin / stepC) * stepC;
            for (float g = first; g <= tMax + 0.01f; g += stepC) {
                lines.add(g);
            }
        }
        float[] result = new float[lines.size()];
        for (int i = 0; i < lines.size(); i++) result[i] = lines.get(i);
        return result;
    }

    private void drawPanel2DayExtremes(Canvas canvas, float[] xs, int N,
                                        float tMin, float tMax, float drawH, float pad, float dp) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        int curDay = -1;
        int rangeStart = 0;
        List<int[]> dayRanges = new ArrayList<>();

        for (int i = 0; i < N; i++) {
            long localTime = plotForecasts.get(i).getForecastTime() + timezoneOffsetMs;
            cal.setTimeInMillis(localTime);
            int day = cal.get(Calendar.DAY_OF_YEAR) + cal.get(Calendar.YEAR) * 366;
            if (day != curDay) {
                if (curDay != -1) dayRanges.add(new int[]{rangeStart, i});
                rangeStart = i;
                curDay = day;
            }
        }
        if (curDay != -1) dayRanges.add(new int[]{rangeStart, N});

        float ts = tempHiLoPaint.getTextSize();
        float lineOff = 3f * dp;

        for (int[] range : dayRanges) {
            int start = range[0], end = range[1];
            if (end - start < 2) continue;

            int hiIdx = start, loIdx = start;
            for (int i = start + 1; i < end; i++) {
                if (plotForecasts.get(i).getTemperature() > plotForecasts.get(hiIdx).getTemperature()) hiIdx = i;
                if (plotForecasts.get(i).getTemperature() < plotForecasts.get(loIdx).getTemperature()) loIdx = i;
            }

            float hiTempC = plotForecasts.get(hiIdx).getTemperature();
            float hiY = tempY(hiTempC, tMin, tMax, drawH, pad);
            tempHiLoPaint.setColor((tempColor(hiTempC) & 0x00FFFFFF) | 0xCC000000);
            String hiLbl = useFahrenheit
                    ? Math.round(hiTempC * 9f / 5f + 32f) + "°"
                    : Math.round(hiTempC) + "°";
            drawLabelWithBg(canvas, hiLbl, xs[hiIdx], hiY - lineOff, tempHiLoPaint, Paint.Align.CENTER);

            float loTempC = plotForecasts.get(loIdx).getTemperature();
            float loY = tempY(loTempC, tMin, tMax, drawH, pad);
            tempHiLoPaint.setColor((tempColor(loTempC) & 0x00FFFFFF) | 0xCC000000);
            String loLbl = useFahrenheit
                    ? Math.round(loTempC * 9f / 5f + 32f) + "°"
                    : Math.round(loTempC) + "°";
            drawLabelWithBg(canvas, loLbl, xs[loIdx], loY + ts + lineOff, tempHiLoPaint, Paint.Align.CENTER);
        }
    }

    private int tempColor(float tempC) {
        if (tempC < T_PURPLE) return COL_TEMP_PURPLE;
        if (tempC < T_BLUE)   return COL_TEMP_BLUE;
        if (tempC < T_TEAL)   return COL_TEMP_TEAL;
        if (tempC < T_GREEN)  return COL_TEMP_GREEN;
        if (tempC < T_ORANGE) return COL_TEMP_ORANGE;
        return COL_TEMP_MAROON;
    }

    private float tempY(float tempC, float tMin, float tMax, float drawH, float pad) {
        return p2Top + pad + (1f - (tempC - tMin) / (tMax - tMin)) * drawH;
    }

    private void drawShadeSegment(Canvas canvas, float x0, float x1,
                                   float curY0, float curY1, float refY0, float refY1, boolean colder) {
        shadePath.reset();
        shadePath.moveTo(x0, curY0);
        shadePath.lineTo(x1, curY1);
        shadePath.lineTo(x1, refY1);
        shadePath.lineTo(x0, refY0);
        shadePath.close();
        canvas.drawPath(shadePath, colder ? coldShadePaint : warmShadePaint);
    }

    // Panel 1: labels — rain/wind/RH max stacked at top-right; min labels at corners
    private void drawPanel1Labels(Canvas canvas) {
        float dp  = getResources().getDisplayMetrics().density;
        float pad = 2f * dp;
        float gap = 5f * dp;
        float ts  = rainLabelPaint.getTextSize();
        float y   = p1Top + ts + pad;

        String rainMax = useMetric ? "💧 10mm/h" : "💧 0.4\"/h";
        String windMax = "💨 30mph";
        String rhLabel = "~ RH";

        // Build a plain text paint for the RH label (same size as other labels, RH colour)
        Paint rhTxt = new Paint(Paint.ANTI_ALIAS_FLAG);
        rhTxt.setColor(0x8829B6F6);
        rhTxt.setTextSize(ts);
        rhTxt.setTextAlign(Paint.Align.RIGHT);

        float wRh   = rhTxt.measureText(rhLabel);
        float wWind = windLabelPaint.measureText(windMax);
        float wRain = rainLabelPaint.measureText(rainMax);

        // Right-to-left: RH | wind | rain
        float xRhRight   = plotRight - pad;
        float xWindRight = xRhRight - wRh - gap;
        float xRainRight = xWindRight - wWind - gap;

        drawLabelWithBg(canvas, rhLabel, xRhRight,   y, rhTxt,          Paint.Align.RIGHT);
        drawLabelWithBg(canvas, windMax, xWindRight, y, windLabelPaint, Paint.Align.RIGHT);
        drawLabelWithBg(canvas, rainMax, xRainRight, y, rainLabelPaint, Paint.Align.RIGHT);

        // Min labels: rain bottom-left, wind bottom-right
        drawLabelWithBg(canvas, "0", plotLeft  + pad, p1Bot - pad, rainLabelPaint, Paint.Align.LEFT);
        drawLabelWithBg(canvas, "0", plotRight - pad, p1Bot - pad, windLabelPaint, Paint.Align.RIGHT);
    }

    // Panel 2: temperature Y-axis labels at round-number grid positions
    private void drawPanel2Labels(Canvas canvas) {
        if (plotForecasts == null || plotForecasts.isEmpty()) return;
        float tMin =  Float.MAX_VALUE, tMax = -Float.MAX_VALUE;
        for (int i = 0; i < plotForecasts.size(); i++) {
            tMin = Math.min(tMin, plotForecasts.get(i).getTemperature());
            tMax = Math.max(tMax, plotForecasts.get(i).getTemperature());
            if (!Float.isNaN(yesterdayTemp[i])) {
                tMin = Math.min(tMin, yesterdayTemp[i]);
                tMax = Math.max(tMax, yesterdayTemp[i]);
            }
        }
        if (tMax <= tMin) tMax = tMin + 1f;

        float dp    = getResources().getDisplayMetrics().density;
        float pad   = 6f * dp;
        float drawH = (p2Bot - p2Top) - 2 * pad;
        float ts    = tempLabelPaint.getTextSize();

        for (float g : computeGridTempsC(tMin, tMax)) {
            float gy  = tempY(g, tMin, tMax, drawH, pad);
            String lbl = useFahrenheit
                    ? Math.round(g * 9f / 5f + 32f) + "°"
                    : Math.round(g) + "°";
            drawLabelWithBg(canvas, lbl, plotLeft + 2f * dp, gy + ts / 3f, tempLabelPaint, Paint.Align.LEFT);
        }
    }

    // Temperature color legend right-aligned at top of panel 2
    private void drawPanel2Legend(Canvas canvas) {
        float dp  = getResources().getDisplayMetrics().density;
        float pad = 2f * dp;
        float ts  = legendPaint.getTextSize();
        float y   = p2Top + ts + pad;

        String[] labels = {"Freezing", "Cold", "Cool", "Comfy", "Hot", "Scorching"};
        int[] colors = {COL_TEMP_PURPLE, COL_TEMP_BLUE, COL_TEMP_TEAL, COL_TEMP_GREEN, COL_TEMP_ORANGE, COL_TEMP_MAROON};

        float[] widths = new float[labels.length];
        float totalW = 0f;
        for (int i = 0; i < labels.length; i++) {
            widths[i] = legendPaint.measureText(labels[i] + " ");
            totalW += widths[i];
        }

        float x0 = plotRight - pad - totalW;
        if (x0 < plotLeft + pad) x0 = plotLeft + pad;

        labelBg.set(x0 - 1f, y - ts, x0 + totalW + 1f, y + ts * 0.3f);
        canvas.drawRoundRect(labelBg, 2f, 2f, bgPaint);

        legendPaint.setTextAlign(Paint.Align.LEFT);
        float x = x0;
        for (int i = 0; i < labels.length; i++) {
            legendPaint.setColor(colors[i]);
            canvas.drawText(labels[i] + " ", x, y, legendPaint);
            x += widths[i];
        }
    }

    private void drawLabelWithBg(Canvas canvas, String text, float x, float y, Paint paint, Paint.Align align) {
        float ts = paint.getTextSize();
        float w  = paint.measureText(text);
        float l, r;
        if (align == Paint.Align.RIGHT) {
            l = x - w - 1f; r = x + 1f;
        } else if (align == Paint.Align.CENTER) {
            l = x - w / 2f - 1f; r = x + w / 2f + 1f;
        } else {
            l = x - 1f; r = x + w + 1f;
        }
        labelBg.set(l, y - ts, r, y + ts * 0.3f);
        canvas.drawRoundRect(labelBg, 2f, 2f, bgPaint);
        Paint.Align old = paint.getTextAlign();
        paint.setTextAlign(align);
        canvas.drawText(text, x, y, paint);
        paint.setTextAlign(old);
    }

    private float cloudCoverFromWeatherCode(int wmoCode) {
        if (wmoCode == 0)  return 5f;
        if (wmoCode == 1)  return 20f;
        if (wmoCode == 2)  return 50f;
        if (wmoCode == 3)  return 90f;
        if (wmoCode == 45 || wmoCode == 48) return 85f;
        if (wmoCode >= 51 && wmoCode <= 57) return 75f;
        if (wmoCode >= 61 && wmoCode <= 67) return 90f;
        if (wmoCode >= 71 && wmoCode <= 77) return 90f;
        if (wmoCode >= 80 && wmoCode <= 82) return 70f;
        if (wmoCode >= 85 && wmoCode <= 86) return 80f;
        if (wmoCode >= 95) return 95f;
        return 50f;
    }
}
