package org.woheller69.weather.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
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

/**
 * FlowX-style dual-panel meteograph.
 *
 * Panel 1 (top ~52%): stacked cloud/sun background, rain rate filled dark blue, wind green line.
 * Panel 2 (bottom ~48%): temperature line, blue/red shading vs 24-hours-prior.
 *
 * Grid: midnight major ticks spanning both panels, noon minor ticks, 4-hour micro dotted ticks.
 */
public class MeteographView extends View {

    // Precipitation capped at 0.4 in/hr = ~10.16 mm/hr
    private static final float RAIN_CAP_MM = 10.16f;
    // Wind capped at ~30 mph = 13.4 m/s
    private static final float WIND_CAP_MS = 13.4f;

    // ── Colors ──────────────────────────────────────────────────────────────
    private static final int COLOR_CLOUD       = 0xFFE6DFD0;  // warm beige-gray
    private static final int COLOR_SUN         = 0xFFFFF7C0;  // pastel yellow
    private static final int COLOR_RAIN        = 0xCC1B4CF0;  // dark blue semi-transparent
    private static final int COLOR_WIND        = 0xFF4CAF50;  // green
    private static final int COLOR_TEMP_LINE   = 0xFF024265;  // dark navy
    private static final int COLOR_COLD_SHADE  = 0x556fa1d2;  // blue tint
    private static final int COLOR_WARM_SHADE  = 0x55e01530;  // red tint
    private static final int COLOR_LABEL       = 0xFF024265;
    private static final int COLOR_TICK_MAJOR  = 0x70B0BEC8;  // midnight — more visible
    private static final int COLOR_TICK_MINOR  = 0x48B0BEC8;  // noon
    private static final int COLOR_TICK_MICRO  = 0x28B0BEC8;  // 4-hour dotted

    // ── Data ────────────────────────────────────────────────────────────────
    private List<HourlyForecast> allForecasts;
    private List<HourlyForecast> plotForecasts;
    private float[] yesterdayTemp;
    private int timezoneOffsetMs = 0;
    private boolean useFahrenheit = false;

    // ── Paints ──────────────────────────────────────────────────────────────
    private final Paint cloudPaint      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sunPaint        = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rainPaint       = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint windPaint       = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tempLinePaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint coldShadePaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint warmShadePaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dayLabelPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint yLabelPaint     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint yLabelRightPaint= new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint majorTickPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint minorTickPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint microTickPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ── Reusable paths ──────────────────────────────────────────────────────
    private final Path sunPath   = new Path();
    private final Path rainPath  = new Path();
    private final Path windPath  = new Path();
    private final Path tempPath  = new Path();
    private final Path shadePath = new Path();

    // ── Layout (computed in onSizeChanged) ──────────────────────────────────
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

        dayLabelPaint.setColor(COLOR_LABEL);
        dayLabelPaint.setTextSize(11f * dp);
        dayLabelPaint.setTextAlign(Paint.Align.LEFT);

        yLabelPaint.setColor(COLOR_LABEL);
        yLabelPaint.setTextSize(9f * dp);
        yLabelPaint.setTextAlign(Paint.Align.RIGHT);

        yLabelRightPaint.setColor(COLOR_LABEL);
        yLabelRightPaint.setTextSize(9f * dp);
        yLabelRightPaint.setTextAlign(Paint.Align.LEFT);

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
    }

    /**
     * @param allHourly   full unfiltered hourly list from DB, sorted ascending
     * @param tzOffsetMs  city UTC offset in ms (getTimeZoneSeconds() * 1000)
     * @param fahrenheit  true when user preference is Fahrenheit
     */
    public void setData(List<HourlyForecast> allHourly, int tzOffsetMs, boolean fahrenheit) {
        this.allForecasts     = allHourly;
        this.timezoneOffsetMs = tzOffsetMs;
        this.useFahrenheit    = fahrenheit;
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
        float lm   = 38f * dp;   // left margin for Y labels
        float rm   = 44f * dp;   // right margin for wind label
        float band = 17f * dp;   // day-label band height

        plotLeft  = lm;
        plotRight = w - rm;

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
        float plotW = plotRight - plotLeft;
        float[] xs = new float[N];
        for (int i = 0; i < N; i++) {
            xs[i] = plotLeft + i * plotW / Math.max(N - 1, 1);
        }

        drawPanel1Background(canvas, xs, N);
        drawTickGrid(canvas, xs, N);     // grid over background, under data
        drawPanel1Data(canvas, xs, N);
        drawDayLabels(canvas, xs, N);
        drawPanel2(canvas, xs, N);
        drawYAxisLabels(canvas);
    }

    // ── Panel 1 background (cloud/sun) ────────────────────────────────────

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

    // ── Tick grid spanning both panels ────────────────────────────────────

    private void drawTickGrid(Canvas canvas, float[] xs, int N) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

        for (int i = 0; i < N; i++) {
            long localTime = plotForecasts.get(i).getForecastTime() + timezoneOffsetMs;
            cal.setTimeInMillis(localTime);
            int hour = cal.get(Calendar.HOUR_OF_DAY);

            if (hour == 0) {
                // Major: midnight — spans both panels
                canvas.drawLine(xs[i], p1Top, xs[i], p2Bot, majorTickPaint);
            } else if (hour == 12) {
                // Minor: noon
                canvas.drawLine(xs[i], p1Top, xs[i], p2Bot, minorTickPaint);
            } else if (hour % 4 == 0) {
                // Micro: every 4 hours, dotted
                canvas.drawLine(xs[i], p1Top, xs[i], p2Bot, microTickPaint);
            }
        }
    }

    // ── Panel 1 data overlays (rain + wind) ──────────────────────────────

    private void drawPanel1Data(Canvas canvas, float[] xs, int N) {
        float panelH = p1Bot - p1Top;

        rainPath.reset();
        rainPath.moveTo(xs[0], p1Bot);
        for (int i = 0; i < N; i++) {
            float rain  = Math.min(plotForecasts.get(i).getPrecipitation(), RAIN_CAP_MM);
            float rainY = p1Bot - (rain / RAIN_CAP_MM) * panelH;
            rainPath.lineTo(xs[i], rainY);
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
    }

    // ── Day labels in the band between panels ─────────────────────────────

    private void drawDayLabels(Canvas canvas, float[] xs, int N) {
        SimpleDateFormat sdf = new SimpleDateFormat("EEE", Locale.getDefault());
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        float dp = getResources().getDisplayMetrics().density;

        int lastDay = -1;
        for (int i = 0; i < N; i++) {
            long localTime = plotForecasts.get(i).getForecastTime() + timezoneOffsetMs;
            cal.setTimeInMillis(localTime);
            int dayOfYear = cal.get(Calendar.DAY_OF_YEAR);

            if (dayOfYear != lastDay) {
                String label = sdf.format(new Date(localTime));
                canvas.drawText(label, xs[i] + 2f * dp, labelBandMid, dayLabelPaint);
                lastDay = dayOfYear;
            }
        }
    }

    // ── Panel 2: temperature line + yesterday shading ─────────────────────

    private void drawPanel2(Canvas canvas, float[] xs, int N) {
        float tMin =  Float.MAX_VALUE;
        float tMax = -Float.MAX_VALUE;
        for (int i = 0; i < N; i++) {
            tMin = Math.min(tMin, plotForecasts.get(i).getTemperature());
            tMax = Math.max(tMax, plotForecasts.get(i).getTemperature());
            if (!Float.isNaN(yesterdayTemp[i])) {
                tMin = Math.min(tMin, yesterdayTemp[i]);
                tMax = Math.max(tMax, yesterdayTemp[i]);
            }
        }
        if (tMax <= tMin) tMax = tMin + 1f;

        float dp     = getResources().getDisplayMetrics().density;
        float pad    = 6f * dp;
        float panelH = p2Bot - p2Top;
        float drawH  = panelH - 2 * pad;

        // Yesterday shading
        for (int i = 0; i < N - 1; i++) {
            if (Float.isNaN(yesterdayTemp[i]) || Float.isNaN(yesterdayTemp[i + 1])) continue;

            float t0 = plotForecasts.get(i).getTemperature();
            float t1 = plotForecasts.get(i + 1).getTemperature();
            float r0 = yesterdayTemp[i];
            float r1 = yesterdayTemp[i + 1];

            float curY0 = tempY(t0, tMin, tMax, drawH, pad);
            float curY1 = tempY(t1, tMin, tMax, drawH, pad);
            float refY0 = tempY(r0, tMin, tMax, drawH, pad);
            float refY1 = tempY(r1, tMin, tMax, drawH, pad);

            boolean seg0colder = t0 < r0;
            boolean seg1colder = t1 < r1;

            if (seg0colder == seg1colder) {
                drawShadeSegment(canvas, xs[i], xs[i + 1], curY0, curY1, refY0, refY1, seg0colder);
            } else {
                float denom = (t1 - t0) - (r1 - r0);
                float frac  = (Math.abs(denom) < 1e-6f) ? 0.5f : (r0 - t0) / denom;
                frac = Math.max(0f, Math.min(1f, frac));
                float xMid  = xs[i] + frac * (xs[i + 1] - xs[i]);
                float yMid  = curY0 + frac * (curY1 - curY0);
                drawShadeSegment(canvas, xs[i],  xMid,        curY0, yMid, refY0, yMid, seg0colder);
                drawShadeSegment(canvas, xMid, xs[i + 1],     yMid, curY1, yMid, refY1, seg1colder);
            }
        }

        // Temperature line
        tempPath.reset();
        for (int i = 0; i < N; i++) {
            float y = tempY(plotForecasts.get(i).getTemperature(), tMin, tMax, drawH, pad);
            if (i == 0) tempPath.moveTo(xs[i], y);
            else        tempPath.lineTo(xs[i], y);
        }
        canvas.drawPath(tempPath, tempLinePaint);
    }

    private float tempY(float tempC, float tMin, float tMax, float drawH, float pad) {
        return p2Top + pad + (1f - (tempC - tMin) / (tMax - tMin)) * drawH;
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

    // ── Y-axis labels ──────────────────────────────────────────────────────

    private void drawYAxisLabels(Canvas canvas) {
        float dp = getResources().getDisplayMetrics().density;

        // Panel 1 left: rain scale (0 at bottom, 0.4" at top)
        canvas.drawText("0.4\"", plotLeft - 2f * dp, p1Top + yLabelPaint.getTextSize(), yLabelPaint);
        canvas.drawText("0",     plotLeft - 2f * dp, p1Bot, yLabelPaint);

        // Panel 1 right: wind scale (30 mph at top, 0 at bottom)
        canvas.drawText("30mph", plotRight + 2f * dp, p1Top + yLabelRightPaint.getTextSize(), yLabelRightPaint);
        canvas.drawText("0",     plotRight + 2f * dp, p1Bot, yLabelRightPaint);

        // Panel 2 left: temperature scale derived from data
        if (plotForecasts == null || plotForecasts.isEmpty()) return;

        float tMin =  Float.MAX_VALUE;
        float tMax = -Float.MAX_VALUE;
        for (int i = 0; i < plotForecasts.size(); i++) {
            tMin = Math.min(tMin, plotForecasts.get(i).getTemperature());
            tMax = Math.max(tMax, plotForecasts.get(i).getTemperature());
            if (!Float.isNaN(yesterdayTemp[i])) {
                tMin = Math.min(tMin, yesterdayTemp[i]);
                tMax = Math.max(tMax, yesterdayTemp[i]);
            }
        }
        if (tMax <= tMin) tMax = tMin + 1f;

        float pad    = 6f * dp;
        float drawH  = (p2Bot - p2Top) - 2 * pad;

        // Draw 3 evenly spaced labels
        int steps = 2;
        for (int s = 0; s <= steps; s++) {
            float frac  = (float) s / steps;
            float tempC = tMin + frac * (tMax - tMin);
            float y     = p2Top + pad + (1f - frac) * drawH;
            String label = useFahrenheit
                    ? Math.round(tempC * 9f / 5f + 32) + "°"
                    : Math.round(tempC) + "°";
            canvas.drawText(label, plotLeft - 2f * dp, y + yLabelPaint.getTextSize() / 3f, yLabelPaint);
        }
    }

    // ── Fallback cloud cover from WMO code ────────────────────────────────

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
