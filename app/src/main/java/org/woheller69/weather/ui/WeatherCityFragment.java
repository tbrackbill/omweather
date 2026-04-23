package org.woheller69.weather.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.woheller69.weather.R;
import org.woheller69.weather.activities.ForecastCityActivity;
import org.woheller69.weather.database.CityToWatch;
import org.woheller69.weather.database.CurrentWeatherData;
import org.woheller69.weather.database.HourlyForecast;
import org.woheller69.weather.database.SQLiteHelper;
import org.woheller69.weather.database.WeekForecast;
import org.woheller69.weather.preferences.AppPreferencesManager;
import org.woheller69.weather.ui.RecycleList.CourseOfDayAdapter;
import org.woheller69.weather.ui.RecycleList.OnSwipeDownListener;
import org.woheller69.weather.ui.updater.IUpdateableCityUI;
import org.woheller69.weather.ui.updater.ViewUpdater;
import org.woheller69.weather.ui.viewPager.WeatherPagerAdapter;
import org.woheller69.weather.ui.views.MeteographView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class WeatherCityFragment extends Fragment implements IUpdateableCityUI {

    private int mCityId = -1;

    private MeteographView mMeteographView;
    private WebView        mRadarWebView;
    private TextView       mRadarTime;
    private RecyclerView   mDayRecycler;
    private TextView       mDayHeader;

    private List<HourlyForecast> mAllHourly;
    private int     mTzOffsetMs = 0;
    private boolean mFahrenheit = false;
    private boolean mUseMetric  = true;

    private boolean mWebViewLoaded = false;

    public static WeatherCityFragment newInstance(Bundle args) {
        WeatherCityFragment f = new WeatherCityFragment();
        f.setArguments(args);
        return f;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        ViewUpdater.addSubscriber(this);
    }

    @Override
    public void onDetach() {
        ViewUpdater.removeSubscriber(this);
        super.onDetach();
    }

    @SuppressLint({"ClickableViewAccessibility", "SetJavaScriptEnabled"})
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        final View v = inflater.inflate(R.layout.fragment_weather_forecast_city_overview, container, false);

        mMeteographView = v.findViewById(R.id.meteograph_view);
        mRadarWebView   = v.findViewById(R.id.card_radar_map);
        mRadarTime      = v.findViewById(R.id.card_radar_time);
        mDayRecycler    = v.findViewById(R.id.recycler_view_course_day);
        mDayHeader      = v.findViewById(R.id.recycler_view_header);

        WebSettings ws = mRadarWebView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);
        mRadarWebView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);

        mDayRecycler.setLayoutManager(
                new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));

        v.setOnTouchListener(new OnSwipeDownListener(getContext()) {
            public void onSwipeDown() {
                WeatherPagerAdapter.refreshSingleData(getContext(), true, mCityId);
                ForecastCityActivity.startRefreshAnimation();
            }
        });

        mCityId = getArguments().getInt("city_id");
        loadData();
        return v;
    }

    public void loadData() {
        if (getContext() == null) return;

        SQLiteHelper db  = SQLiteHelper.getInstance(getContext());
        CurrentWeatherData cwd = db.getCurrentWeatherByCityId(mCityId);
        if (cwd == null || cwd.getTimestamp() == 0) return;

        mTzOffsetMs = cwd.getTimeZoneSeconds() * 1000;

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        AppPreferencesManager prefs = new AppPreferencesManager(sp);
        mFahrenheit = !prefs.getTemperatureUnit().equals("°C");
        mUseMetric  = sp.getString("precipitationUnit", "1").equals("1");

        mAllHourly = db.getForecastsByCityId(mCityId);
        if (mMeteographView != null)
            mMeteographView.setData(mAllHourly, mTzOffsetMs, mFahrenheit, mUseMetric);

        setupHourlyAdapter(mAllHourly);

        CityToWatch city = db.getCityToWatch(mCityId);
        if (city != null) fetchRadar(city);
    }

    private void setupHourlyAdapter(List<HourlyForecast> allHourly) {
        if (getContext() == null || mDayRecycler == null) return;
        List<HourlyForecast> current = new ArrayList<>();
        long cutoff = System.currentTimeMillis() - 60L * 60 * 1000;
        for (HourlyForecast f : allHourly) {
            if (f.getForecastTime() >= cutoff) current.add(f);
        }
        CourseOfDayAdapter adapter = new CourseOfDayAdapter(current, getContext(), mDayHeader, mDayRecycler);
        mDayRecycler.setAdapter(adapter);
        mDayRecycler.setFocusable(false);
    }

    private void fetchRadar(CityToWatch city) {
        if (getContext() == null || mRadarWebView == null) return;

        double lat = city.getLatitude();
        double lon = city.getLongitude();

        if (!mWebViewLoaded) {
            mWebViewLoaded = true;
            String url = "file:///android_asset/radar.html?lat=" + lat + "&lon=" + lon;
            mRadarWebView.loadUrl(url);
            Log.d("RadarCard", "WebView loading " + url);
        }

        RequestQueue queue = Volley.newRequestQueue(getContext());
        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.GET,
                "https://api.rainviewer.com/public/weather-maps.json",
                null,
                response -> {
                    try {
                        JSONObject radar = response.getJSONObject("radar");
                        JSONArray past   = radar.getJSONArray("past");
                        if (past.length() == 0) return;
                        JSONObject latest = past.getJSONObject(past.length() - 1);
                        String path = latest.getString("path");
                        long   time = latest.getLong("time");

                        String tileUrl = "https://tilecache.rainviewer.com" + path
                                + "/256/{z}/{x}/{y}/2/1_1.png";

                        mRadarWebView.evaluateJavascript(
                                "setRadarTiles('" + tileUrl + "')", null);

                        if (mRadarTime != null) {
                            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                            sdf.setTimeZone(TimeZone.getDefault());
                            mRadarTime.setText(sdf.format(new Date(time * 1000L)));
                        }
                        Log.d("RadarCard", "radar tiles injected: " + tileUrl);
                    } catch (JSONException e) {
                        Log.e("RadarCard", "JSON parse error", e);
                    }
                },
                error -> Log.e("RadarCard", "Rainviewer API error: " + error));

        req.setRetryPolicy(new DefaultRetryPolicy(10000, 1,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        queue.add(req);
    }

    @Override
    public void processNewCurrentWeatherData(CurrentWeatherData data) {
        if (data != null && data.getCity_id() == mCityId) loadData();
    }

    @Override
    public void processNewForecasts(List<HourlyForecast> hourlyForecasts) {
        if (hourlyForecasts == null || hourlyForecasts.isEmpty()
                || hourlyForecasts.get(0).getCity_id() != mCityId) return;
        mAllHourly = hourlyForecasts;
        if (mMeteographView != null)
            mMeteographView.setData(mAllHourly, mTzOffsetMs, mFahrenheit, mUseMetric);
        setupHourlyAdapter(mAllHourly);
    }

    @Override
    public void processNewWeekForecasts(List<WeekForecast> forecasts) {
        // week card removed
    }
}
