package org.woheller69.weather.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.preference.PreferenceManager;

import org.woheller69.weather.database.HourlyForecast;
import org.woheller69.weather.preferences.AppPreferencesManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * FlowX-style dual-panel meteograph.
 *
 * Panel 1 (top): stacked cloud/sun background, rain rate (filled dark blue), wind (green line).
 * Panel 2 (bottom): temperature line with shading vs. 24-hours-prior comparison.
 */
public class MeteographView extends View {

    // Precipitation capped at 1 inch/hr = 25.4 mm/hr; wind at ~30 mph = 13.4 m/s
    private static final float RAIN_CAP_MM  = 25.4f;
    private static final float WIND_CAP_MS  = 13.4f;

    // Colors
    private static final int COLOR_CLOUD      = 0xFFCDCDCD;  // pastel gray
    private static final int COLOR_SUN        = 0xFFFFF0A0;  // pastel yellow
    private static final int COLOR_RAIN       = 0xCC1B4CF0;  // dark blue semi-transparent
    private static final int COLOR_WIND       = 0xFF4CAF50;  // green
    private static final int COLOR_TEMP_LINE  = 0xFF024265;  // dark navy (matches colorPrimary)
    private static final int COLOR_COLD_SHADE = 0x556fa1d2;  // blue tint
    private static final int COLOR_WARM_SHADE = 0x55e01530;  // red tint
    private static final int COLOR_LABEL      = 0xFF024265;
    private static final int COLOR_DIVIDER    = 0x40024265;

    // Data
    private List<HourlyForecast> allForecasts;   // full set from DB, starting today 00:00
    private List<HourlyForecast> plotForecasts;  // filtered to ~now onward
    private float[] yesterdayTemp;               // parallel array; NaN if no comparison available
    private int timezoneOffsetMs = 0;            // city timezone offset in milliseconds

    // Paints — allocated once
    private final Paint cloudPaint     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sunPaint       = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rainPaint      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint windPaint      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tempLinePaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint coldShadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint warmShadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dividerPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint yLabelPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Reusable paths
    private final Path sunPath  = new Path();
    private final Path rainPath = new Path();
    private final Path windPath = new Path();
    private final Path tempPath = new Path();
    private final Path shadePath = new Path();

    // Layout (computed in onSizeChanged)
    private float plotLeft, plotRight;
    private float p1Top, p1Bot, p2Top, p2Bot;
    private float labelBandMid;

    public MeteographView(Context context) {
        super(context);
        init(context);
    }

    public MeteographView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public MeteographView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        float dp = context.getResources().getDisplayMetrics().density;

        cloudPaint.setColor(COLOR_CLOUD);
        cloudPaint.setStyle(Paint.Style.FILL);

        sunPaint.setColor(COLOR_SUN);
        sunPaint.setStyle(Paint.Style.FILL);

        rainPaint.setColor(COLOR_RAIN);
        rainPaint.setStyle(Paint.Style.FILL);

        windPaint.setColor(COLOR_WIND);
        windPaint.setStyle(Paint.Style.STROKE);
        windPaint.setStrokeWidth(2f * dp);
        windPaint.setStrokeCap(Paint.Cap.ROUND);
        windPaint.setStrokeJoin(Paint.Join.ROUND);

        tempLinePaint.setColor(COLOR_TEMP_LINE);
        tempLinePaint.setStyle(Paint.Style.STROKE);
        tempLinePaint.setStrokeWidth(3f * dp);
        tempLinePaint.setStrokeCap(Paint.Cap.ROUND);
        tempLinePaint.setStrokeJoin(Paint.Join.ROUND);

        coldShadePaint.setColor(COLOR_COLD_SHADE);
        coldShadePaint.setStyle(Paint.Style.FILL);

        warmShadePaint.setColor(COLOR_WARM_SHADE);
        warmShadePaint.setStyle(Paint.Style.FILL);

        labelPaint.setColor(COLOR_LABEL);
        labelPaint.setTextSize(11f * dp);
        labelPaint.setTextAlign(Paint.Align.LEFT);

        yLabelPaint.setColor(COLOR_LABEL);
        yLabelPaint.setTextSize(9f * dp);
        yLabelPaint.setTextAlign(Paint.Align.RIGHT);

        dividerPaint.setColor(COLOR_DIVIDER);
        dividerPaint.setStyle(Paint.Style.STROKE);
        dividerPaint.setStrokeWidth(1f);
    }

    /**
     * Set data. allHourly should be the full unfiltered list from DB (sorted ascending).
     * tzOffsetMs is the city's UTC offset in milliseconds (from CurrentWeatherData.getTimeZoneSeconds()*1000).
     */
    public void setData(List<HourlyForecast> allHourly, int tzOffsetMs) {
        this.allForecasts = allHourly;
        this.timezoneOffsetMs = tzOffsetMs;
        prepareData();
        invalidate();
    }

    private void prepareData() {
        if (allForecasts == null || allForecasts.isEmpty()) return;

        long cutoff = System.currentTimeMillis() - 60L * 60 * 1000; // start from 1h ago
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
        float dp = getResources().getDisplayMetrics().density;
        float leftMargin  = 38f * dp;
        float rightMargin = 34f * dp;
        float labelBand   = 17f * dp;

        plotLeft  = leftMargin;
        plotRight = w - rightMargin;

        float usableH = h - labelBand;
        p1Top = 0f;
        p1Bot = usableH * 0.52f;
        p2Top = p1Bot + labelBand;
        p2Bot = h;
        labelBandMid = p1Bot + labelBand * 0.72f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (plotForecasts == null || plotForecasts.isEmpty()) return;

        int N = plotForecasts.size();
        float plotW = plotRight - plotLeft;
        float[] xs = new float[N];
        for (int i = 0; i < N; i++) {
            xs[i] = plotLeft + i * plotW / Math.max(N - 1, 1);
        }

        drawPanel1(canvas, xs, N);
        drawDayLabels(canvas, xs, N);
        drawPanel2(canvas, xs, N);
        drawYAxisLabels(canvas);
    }

    // ── Panel 1: cloud/sun background + rain + wind ──────────────────────────

    private void drawPanel1(Canvas canvas, float[] xs, int N) {
        float panelH = p1Bot - p1Top;

        // 1. Fill entire panel with cloud gray
        canvas.drawRect(plotLeft, p1Top, plotRight, p1Bot, cloudPaint);

        // 2. Sun yellow polygon: from cloud-boundary line down to panel bottom
        float[] cloudBY = new float[N];
        for (int i = 0; i < N; i++) {
            float cover = plotForecasts.get(i).getCloudCover();
            if (cover < 0) cover = cloudCoverFromWeatherCode(plotForecasts.get(i).getWeatherID());
            cover = Math.max(0f, Math.min(100f, cover));
            cloudBY[i] = p1Top + (cover / 100f) * panelH;
        }

        sunPath.reset();
        sunPath.moveTo(xs[0], cloudBY[0]);
        for (int i = 1; i < N; i++) sunPath.lineTo(xs[i], cloudBY[i]);
        sunPath.lineTo(xs[N - 1], p1Bot);
        sunPath.lineTo(xs[0], p1Bot);
        sunPath.close();
        canvas.drawPath(sunPath, sunPaint);

        // 3. Rain filled area (dark blue, anchored at bottom)
        rainPath.reset();
        rainPath.moveTo(xs[0], p1Bot);
        for (int i = 0; i < N; i++) {
            float rain = Math.min(plotForecasts.get(i).getPrecipitation(), RAIN_CAP_MM);
            float rainY = p1Bot - (rain / RAIN_CAP_MM) * panelH;
            rainPath.lineTo(xs[i], rainY);
        }
        rainPath.lineTo(xs[N - 1], p1Bot);
        rainPath.close();
        canvas.drawPath(rainPath, rainPaint);

        // 4. Wind line (green, unfilled)
        windPath.reset();
        for (int i = 0; i < N; i++) {
            float wind = Math.min(plotForecasts.get(i).getWindSpeed(), WIND_CAP_MS);
            float windY = p1Bot - (wind / WIND_CAP_MS) * panelH;
            if (i == 0) windPath.moveTo(xs[i], windY);
            else        windPath.lineTo(xs[i], windY);
        }
        canvas.drawPath(windPath, windPaint);
    }

    // ── Day labels between panels ─────────────────────────────────────────────

    private void drawDayLabels(Canvas canvas, float[] xs, int N) {
        SimpleDateFormat sdf = new SimpleDateFormat("EEE", Locale.getDefault());
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

        int lastDay = -1;
        for (int i = 0; i < N; i++) {
            // Shift by timezone so day boundaries fall at local midnight
            long localTime = plotForecasts.get(i).getForecastTime() + timezoneOffsetMs;
            cal.setTimeInMillis(localTime);
            int dayOfYear = cal.get(Calendar.DAY_OF_YEAR);

            if (dayOfYear != lastDay) {
                canvas.drawLine(xs[i], p1Bot, xs[i], p2Top, dividerPaint);
                String label = sdf.format(new Date(localTime));
                float dp = getResources().getDisplayMetrics().density;
                canvas.drawText(label, xs[i] + 2f * dp, labelBandMid, labelPaint);
                lastDay = dayOfYear;
            }
        }
    }

    // ── Panel 2: temperature with yesterday comparison shading ────────────────

    private void drawPanel2(Canvas canvas, float[] xs, int N) {
        // Gather temp range including yesterday values
        float tMin =  Float.MAX_VALUE;
        float tMax = -Float.MAX_VALUE;
        for (int i = 0; i < N; i++) {
            float t = plotForecasts.get(i).getTemperature();
            tMin = Math.min(tMin, t);
            tMax = Math.max(tMax, t);
            if (!Float.isNaN(yesterdayTemp[i])) {
                tMin = Math.min(tMin, yesterdayTemp[i]);
                tMax = Math.max(tMax, yesterdayTemp[i]);
            }
        }
        if (tMax <= tMin) tMax = tMin + 1f;

        float panelH = p2Bot - p2Top;
        float dp = getResources().getDisplayMetrics().density;
        float pad = 6f * dp;

        // Convert temp to Y within panel 2
        // tMax → p2Top+pad, tMin → p2Bot-pad
        float drawH = panelH - 2 * pad;

        // Draw yesterday-comparison shading segment by segment
        for (int i = 0; i < N - 1; i++) {
            if (Float.isNaN(yesterdayTemp[i]) || Float.isNaN(yesterdayTemp[i + 1])) continue;

            float t0 = plotForecasts.get(i).getTemperature();
            float t1 = plotForecasts.get(i + 1).getTemperature();
            float r0 = yesterdayTemp[i];
            float r1 = yesterdayTemp[i + 1];

            float curY0 = p2Top + pad + (1f - (t0 - tMin) / (tMax - tMin)) * drawH;
            float curY1 = p2Top + pad + (1f - (t1 - tMin) / (tMax - tMin)) * drawH;
            float refY0 = p2Top + pad + (1f - (r0 - tMin) / (tMax - tMin)) * drawH;
            float refY1 = p2Top + pad + (1f - (r1 - tMin) / (tMax - tMin)) * drawH;

            // Detect line crossing within segment; if crossing, split into two sub-segments
            boolean seg0colder = t0 < r0;
            boolean seg1colder = t1 < r1;

            if (seg0colder == seg1colder) {
                drawShadeSegment(canvas, xs[i], xs[i + 1], curY0, curY1, refY0, refY1, seg0colder);
            } else {
                // Lines cross: find intersection via linear interpolation
                float frac = (r0 - t0) / ((t1 - t0) - (r1 - r0));
                float xMid  = xs[i] + frac * (xs[i + 1] - xs[i]);
                float yMid  = curY0 + frac * (curY1 - curY0);
                drawShadeSegment(canvas, xs[i],  xMid,         curY0, yMid, refY0, yMid, seg0colder);
                drawShadeSegment(canvas, xMid, xs[i + 1],      yMid, curY1, yMid, refY1, seg1colder);
            }
        }

        // Temperature line on top of shading
        tempPath.reset();
        for (int i = 0; i < N; i++) {
            float t = plotForecasts.get(i).getTemperature();
            float y = p2Top + pad + (1f - (t - tMin) / (tMax - tMin)) * drawH;
            if (i == 0) tempPath.moveTo(xs[i], y);
            else        tempPath.lineTo(xs[i], y);
        }
        canvas.drawPath(tempPath, tempLinePaint);
    }

    private void drawShadeSegment(Canvas canvas,
                                   float x0, float x1,
                                   float curY0, float curY1,
                                   float refY0, float refY1,
                                   boolean colder) {
        shadePath.reset();
        shadePath.moveTo(x0, curY0);
        shadePath.lineTo(x1, curY1);
        shadePath.lineTo(x1, refY1);
        shadePath.lineTo(x0, refY0);
        shadePath.close();
        canvas.drawPath(shadePath, colder ? coldShadePaint : warmShadePaint);
    }

    // ── Y-axis labels ─────────────────────────────────────────────────────────

    private void drawYAxisLabels(Canvas canvas) {
        float dp = getResources().getDisplayMetrics().density;

        // Panel 1: rain scale (left side), wind scale (right side)
        // Left: rain max label at top, 0 at bottom
        float p1H = p1Bot - p1Top;
        canvas.drawText("1\"", plotLeft - 2f * dp, p1Top + yLabelPaint.getTextSize(), yLabelPaint);
        canvas.drawText("0",   plotLeft - 2f * dp, p1Bot, yLabelPaint);

        // Right: wind max at top, 0 at bottom
        String windMax = "30₀ᵐᵖʰ"; // fallback: just "30mph"
        yLabelPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("30mph", plotRight + 2f * dp, p1Top + yLabelPaint.getTextSize(), yLabelPaint);
        yLabelPaint.setTextAlign(Paint.Align.RIGHT);

        // Panel 2: draw horizontal gridline at 0°C / 32°F if in range, labeled
        // (skipped for brevity — the line itself is sufficient visual anchor)
    }

    // ── Fallback: approximate cloud cover from WMO weather code ──────────────

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
