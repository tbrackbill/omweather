package org.woheller69.weather.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.MapTileProviderBasic;
import org.osmdroid.tileprovider.cachemanager.CacheManager;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.TilesOverlay;
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
    private MapView        mRadarMap;
    private TextView       mRadarTime;
    private RecyclerView   mDayRecycler;
    private TextView       mDayHeader;

    private List<HourlyForecast> mAllHourly;
    private int     mTzOffsetMs = 0;
    private boolean mFahrenheit = false;
    private boolean mUseMetric  = true;

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

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // Must be called before the MapView inflates so it can find its cache directory
        Configuration.getInstance().load(requireContext(),
                PreferenceManager.getDefaultSharedPreferences(requireContext()));
        Configuration.getInstance().setUserAgentValue(requireContext().getPackageName());

        final View v = inflater.inflate(R.layout.fragment_weather_forecast_city_overview, container, false);

        mMeteographView = v.findViewById(R.id.meteograph_view);
        mRadarMap       = v.findViewById(R.id.card_radar_map);
        mRadarTime      = v.findViewById(R.id.card_radar_time);
        mDayRecycler    = v.findViewById(R.id.recycler_view_course_day);
        mDayHeader      = v.findViewById(R.id.recycler_view_header);

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
        if (city != null) fetchRadar(city, cwd.getTimeZoneSeconds());
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

    private void fetchRadar(CityToWatch city, int tzSeconds) {
        if (getContext() == null) return;

        // Set up OSM basemap
        mRadarMap.setTileSource(TileSourceFactory.MAPNIK);
        mRadarMap.setMultiTouchControls(false);
        mRadarMap.setBuiltInZoomControls(false);
        mRadarMap.setMinZoomLevel(7.0);
        mRadarMap.setMaxZoomLevel(7.0);
        mRadarMap.getController().setZoom(7.0);
        mRadarMap.getController().setCenter(new GeoPoint(city.getLatitude(), city.getLongitude()));

        // Pre-cache basemap tiles for offline use on unmetered connections
        preCacheBasemap(city);

        // Overlay the latest Rainviewer radar frame
        RequestQueue queue = Volley.newRequestQueue(getContext().getApplicationContext());
        JsonObjectRequest jsonReq = new JsonObjectRequest(Request.Method.GET,
                "https://api.rainviewer.com/public/weather-maps.json", null,
                response -> {
                    if (!isAdded()) return;
                    try {
                        String host   = response.getString("host");
                        JSONArray past = response.getJSONObject("radar").getJSONArray("past");
                        JSONObject latest = past.getJSONObject(past.length() - 1);
                        String path   = latest.getString("path");
                        long timeGmt  = latest.getLong("time") * 1000L;

                        // URL pattern: host + path + "/256/" + z + "/" + x + "/" + y + "/2/1_1.png"
                        XYTileSource radarSource = new XYTileSource(
                                "Rainviewer_" + timeGmt, 1, 12, 256, "/2/1_1.png",
                                new String[]{host + path + "/256/"});

                        MapTileProviderBasic radarProvider =
                                new MapTileProviderBasic(getContext().getApplicationContext());
                        radarProvider.setTileSource(radarSource);

                        TilesOverlay radarOverlay = new TilesOverlay(radarProvider, getContext());
                        radarOverlay.setLoadingBackgroundColor(Color.TRANSPARENT);

                        mRadarMap.getOverlayManager().clear();
                        mRadarMap.getOverlayManager().add(radarOverlay);
                        mRadarMap.invalidate();

                        long localMs = timeGmt + tzSeconds * 1000L;
                        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                        mRadarTime.setText(sdf.format(new Date(localMs)));
                    } catch (JSONException e) {
                        Log.d("RadarCard", "json: " + e);
                    }
                },
                error -> Log.d("RadarCard", "json: " + error));
        jsonReq.setRetryPolicy(new DefaultRetryPolicy(5000, 1, 1f));
        queue.add(jsonReq);
    }

    // Download basemap tiles around this city for offline rendering. Skipped on metered connections.
    private void preCacheBasemap(CityToWatch city) {
        if (getContext() == null || mRadarMap == null) return;
        ConnectivityManager cm =
                (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null && cm.isActiveNetworkMetered()) return;
        try {
            CacheManager cache = new CacheManager(mRadarMap);
            double lat = city.getLatitude(), lon = city.getLongitude();
            BoundingBox bbox = new BoundingBox(lat + 2.5, lon + 2.5, lat - 2.5, lon - 2.5);
            cache.downloadAreaAsyncNoUI(getContext().getApplicationContext(), bbox, 5, 8, null);
        } catch (Exception e) {
            Log.d("RadarCard", "pre-cache skipped: " + e.getMessage());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mRadarMap != null) mRadarMap.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mRadarMap != null) mRadarMap.onPause();
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
