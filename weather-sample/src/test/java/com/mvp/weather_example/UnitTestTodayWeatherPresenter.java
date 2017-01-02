package com.mvp.weather_example;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;

import com.google.gson.Gson;
import com.mvp.MvpEventBus;
import com.mvp.weather_example.model.forecast.threehours.ThreeHoursForecastWeather;
import com.mvp.weather_example.model.today.TodayWeather;
import com.mvp.weather_example.presenter.TodayWeatherPresenter;
import com.mvp.weather_example.presenter.WeatherPresenter;
import com.mvp.weather_example.service.DateProvider;
import com.mvp.weather_example.service.ImageRequestManager;
import com.mvp.weather_example.service.WeatherService;
import com.mvp.weather_example.view.IWeatherView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.concurrent.RoboExecutorService;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

import okhttp3.Request;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

@SuppressWarnings("MissingPermission")
@Config(sdk = 21, constants = com.mvp.weather_example.BuildConfig.class)
@RunWith(RobolectricTestRunner.class)
public class UnitTestTodayWeatherPresenter {

    public static final int REQUEST_CODE_PERM_ACCESS_FINE_LOCATION = TodayWeatherPresenter.REQUEST_CODE_PERM_ACCESS_FINE_LOCATION;
    public static final int REQUEST_CODE_PERM_ACCESS_COARSE_LOCATION = TodayWeatherPresenter.REQUEST_CODE_PERM_ACCESS_COARSE_LOCATION;
    public static final String PERM_ACCESS_FINE_LOCATION = Manifest.permission.ACCESS_FINE_LOCATION;
    public static final String PERM_ACCESS_COARSE_LOCATION = Manifest.permission.ACCESS_COARSE_LOCATION;

    @Mock
    private LocationManager locationManager;
    @Mock
    private DateProvider dateProvider;
    @Mock
    private ImageRequestManager requestManager;
    @Mock
    private WeatherService weatherService;

    @InjectMocks
    private TodayWeatherPresenter presenter;

    private IWeatherView view;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        view = mock(IWeatherView.class);
        presenter.setView(view);
        findAndSetField("executorService", new RoboExecutorService());
        findAndSetField("handler", new Handler(Looper.myLooper()));
        findAndSetField("eventBus", new MvpEventBus());
    }

    private void findAndSetField(String fieldName, Object objectToInject) throws NoSuchFieldException, IllegalAccessException {
        findAndSetField(presenter.getClass(), fieldName, objectToInject);
    }

    private void findAndSetField(Class<?> clazz, String fieldName, Object objectToInject) throws NoSuchFieldException, IllegalAccessException {
        if (clazz == null || clazz.equals(Object.class))
            return;
        Field field = null;
        try {
            field = clazz.getDeclaredField(fieldName);
        } catch (Exception e) {

        }
        if (field == null)
            findAndSetField(clazz.getSuperclass(), fieldName, objectToInject);
        else {
            field.setAccessible(true);
            field.set(presenter, objectToInject);
        }
    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void viewIsSet() {
        assertNotNull(presenter.getView());
    }

    @Test
    public void shouldRequestPermissionsIfRequired() {
        when(view.isPermissionGranted(PERM_ACCESS_COARSE_LOCATION)).thenReturn(false);
        when(view.isPermissionGranted(PERM_ACCESS_FINE_LOCATION)).thenReturn(false);
        presenter.onViewAttached(view);
        verify(view).requestPermission(PERM_ACCESS_FINE_LOCATION, REQUEST_CODE_PERM_ACCESS_FINE_LOCATION);
        verify(view).requestPermission(PERM_ACCESS_COARSE_LOCATION, REQUEST_CODE_PERM_ACCESS_COARSE_LOCATION);
    }

    @Test
    public void shouldNotInteractWithLocationManagerIfPermissionIsNotGranted() {
        when(view.isPermissionGranted(PERM_ACCESS_COARSE_LOCATION)).thenReturn(false);
        when(view.isPermissionGranted(PERM_ACCESS_FINE_LOCATION)).thenReturn(false);
        presenter.onViewAttached(view);
        verifyZeroInteractions(locationManager);
        presenter.onPermissionsResult(REQUEST_CODE_PERM_ACCESS_COARSE_LOCATION, new String[]{PERM_ACCESS_COARSE_LOCATION}, new int[]{PackageManager.PERMISSION_DENIED});
        verifyZeroInteractions(locationManager);
        presenter.onPermissionsResult(TodayWeatherPresenter.REQUEST_CODE_PERM_ACCESS_FINE_LOCATION, new String[]{PERM_ACCESS_FINE_LOCATION}, new int[]{PackageManager.PERMISSION_DENIED});
        verifyZeroInteractions(locationManager);
    }

    @Test
    public void mustNotRequestWeatherIfThereIsNoLastKnownLocation() {
        when(view.isPermissionGranted(PERM_ACCESS_COARSE_LOCATION)).thenReturn(true);
        when(view.isPermissionGranted(PERM_ACCESS_FINE_LOCATION)).thenReturn(true);
        when(locationManager.getBestProvider(any(Criteria.class), anyBoolean())).thenReturn("gps");
        when(locationManager.getLastKnownLocation(anyString())).thenReturn(null);
        presenter.onViewAttached(view);
        verifyZeroInteractions(weatherService);
    }

    @Test
    public void shouldRequestWeatherIfPermissionIsGrantedAndLocationIsPresent() {
        when(view.isPermissionGranted(PERM_ACCESS_COARSE_LOCATION)).thenReturn(true);
        when(view.isPermissionGranted(PERM_ACCESS_FINE_LOCATION)).thenReturn(true);
        when(locationManager.getBestProvider(any(Criteria.class), anyBoolean())).thenReturn("gps");
        Location location = createLocation(1.0, 1.0);

        when(weatherService.getCurrentWeather(1.0, 1.0, "metric", WeatherService.API_KEY))
                .thenReturn(new CallAdapter<TodayWeather>() {
                    @Override
                    protected Response<TodayWeather> createResponse() {
                        String response = "{\"coord\":{\"lon\":9.14,\"lat\":49.91},\"weather\":[{\"id\":701,\"main\":\"Mist\",\"description\":\"mist\",\"icon\":\"50d\"}],\"base\":\"stations\",\"main\":{\"temp\":3.76,\"pressure\":1037,\"humidity\":100,\"temp_min\":3,\"temp_max\":5},\"visibility\":5000,\"wind\":{\"speed\":1.5,\"deg\":180},\"clouds\":{\"all\":90},\"dt\":1482490680,\"sys\":{\"type\":1,\"id\":4881,\"message\":0.0027,\"country\":\"DE\",\"sunrise\":1482477611,\"sunset\":1482506746},\"id\":6557029,\"name\":\"Niedernberg\",\"cod\":200}";
                        TodayWeather todayWeather = new Gson().fromJson(response, TodayWeather.class);
                        return Response.success(todayWeather);
                    }
                });
        when(locationManager.getLastKnownLocation(anyString())).thenReturn(location);
        presenter.onViewAttached(view);
        verify(view, atLeastOnce()).isPermissionGranted(anyString());
        verify(view).requestStarted();
        verify(view).showWeather("3.76", "100");
        verify(view).requestFinished();
        verifyZeroInteractions(view);
    }

    @NonNull
    private Location createLocation(double longitude, double latitude) {
        Location location = new Location("");
        location.setLatitude(longitude);
        location.setLongitude(latitude);
        return location;
    }

    @Test
    public void shouldNotLoadForecastsIfNoLocationIsPresent() {
        presenter.onViewAttached(view);
        reset(locationManager, view);
        when(view.isPermissionGranted(PERM_ACCESS_COARSE_LOCATION)).thenReturn(true);
        when(view.isPermissionGranted(PERM_ACCESS_FINE_LOCATION)).thenReturn(true);
        when(locationManager.getBestProvider(any(Criteria.class), anyBoolean())).thenReturn("gps");
        when(locationManager.getLastKnownLocation(anyString())).thenReturn(null);
        presenter.loadForecastWeatherDataForToday();
        verifyZeroInteractions(weatherService, view);
    }

    @Test
    public void shouldLoadForecastsIfLocationIsPresent() {
        presenter.onViewAttached(view);
        reset(locationManager, view);
        when(view.isPermissionGranted(PERM_ACCESS_COARSE_LOCATION)).thenReturn(true);
        when(view.isPermissionGranted(PERM_ACCESS_FINE_LOCATION)).thenReturn(true);
        when(locationManager.getBestProvider(any(Criteria.class), anyBoolean())).thenReturn("gps");
        double longitude = 1.0;
        double latitude = 1.0;
        when(locationManager.getLastKnownLocation(anyString())).thenReturn(createLocation(longitude, latitude));
        Calendar calendar = createCurrentDate(2016, 11, 23, 12, 0, 1);
        when(dateProvider.getCurrentDate()).thenReturn(calendar);
        when(weatherService.getForecastWeather(longitude, latitude, "metric", WeatherService.API_KEY))
                .thenReturn(new CallAdapter<ThreeHoursForecastWeather>() {
                    @Override
                    protected Response<ThreeHoursForecastWeather> createResponse() {
                        String response = "{\"cod\":\"200\",\"message\":0.559,\"city\":{\"id\":2863088,\"name\":\"Niedernberg\",\"coord\":{\"lon\":9.13694,\"lat\":49.91222},\"country\":\"DE\",\"population\":0},\"cnt\":36,\"list\":[{\"dt\":1482494400,\"main\":{\"temp\":4,\"temp_min\":3.3,\"temp_max\":4,\"pressure\":1017.72,\"sea_level\":1049.55,\"grnd_level\":1017.72,\"humidity\":100,\"temp_kf\":0.7},\"weather\":[{\"id\":500,\"main\":\"Rain\",\"description\":\"light rain\",\"icon\":\"10d\"}],\"clouds\":{\"all\":80},\"wind\":{\"speed\":1.67,\"deg\":149.501},\"rain\":{\"3h\":0.1},\"sys\":{\"pod\":\"d\"},\"dt_txt\":\"2016-12-23 12:00:00\"},{\"dt\":1482505200,\"main\":{\"temp\":3.53,\"temp_min\":2.98,\"temp_max\":3.53,\"pressure\":1015.65,\"sea_level\":1047.47,\"grnd_level\":1015.65,\"humidity\":99,\"temp_kf\":0.56},\"weather\":[{\"id\":500,\"main\":\"Rain\",\"description\":\"light rain\",\"icon\":\"10d\"}],\"clouds\":{\"all\":80},\"wind\":{\"speed\":1.4,\"deg\":150.001},\"rain\":{\"3h\":0.069999999999999},\"sys\":{\"pod\":\"d\"},\"dt_txt\":\"2016-12-23 15:00:00\"},{\"dt\":1482516000,\"main\":{\"temp\":1.91,\"temp_min\":1.49,\"temp_max\":1.91,\"pressure\":1014.31,\"sea_level\":1046.3,\"grnd_level\":1014.31,\"humidity\":93,\"temp_kf\":0.42},\"weather\":[{\"id\":500,\"main\":\"Rain\",\"description\":\"light rain\",\"icon\":\"10n\"}],\"clouds\":{\"all\":88},\"wind\":{\"speed\":2.01,\"deg\":163.502},\"rain\":{\"3h\":0.090000000000001},\"sys\":{\"pod\":\"n\"},\"dt_txt\":\"2016-12-23 18:00:00\"},{\"dt\":1482526800,\"main\":{\"temp\":2.61,\"temp_min\":2.33,\"temp_max\":2.61,\"pressure\":1012.57,\"sea_level\":1044.61,\"grnd_level\":1012.57,\"humidity\":96,\"temp_kf\":0.28},\"weather\":[{\"id\":500,\"main\":\"Rain\",\"description\":\"light rain\",\"icon\":\"10n\"}],\"clouds\":{\"all\":92},\"wind\":{\"speed\":3.76,\"deg\":201.002},\"rain\":{\"3h\":0.17},\"sys\":{\"pod\":\"n\"},\"dt_txt\":\"2016-12-23 21:00:00\"},{\"dt\":1482537600,\"main\":{\"temp\":2.98,\"temp_min\":2.85,\"temp_max\":2.98,\"pressure\":1011.84,\"sea_level\":1043.66,\"grnd_level\":1011.84,\"humidity\":97,\"temp_kf\":0.14},\"weather\":[{\"id\":500,\"main\":\"Rain\",\"description\":\"light rain\",\"icon\":\"10n\"}],\"clouds\":{\"all\":88},\"wind\":{\"speed\":6.1,\"deg\":225.001},\"rain\":{\"3h\":0.12},\"sys\":{\"pod\":\"n\"},\"dt_txt\":\"2016-12-24 00:00:00\"},{\"dt\":1482548400,\"main\":{\"temp\":3.47,\"temp_min\":3.47,\"temp_max\":3.47,\"pressure\":1011.02,\"sea_level\":1042.61,\"grnd_level\":1011.02,\"humidity\":95},\"weather\":[{\"id\":500,\"main\":\"Rain\",\"description\":\"light rain\",\"icon\":\"10n\"}],\"clouds\":{\"all\":92},\"wind\":{\"speed\":6.65,\"deg\":233.002},\"rain\":{\"3h\":0.58},\"sys\":{\"pod\":\"n\"},\"dt_txt\":\"2016-12-24 03:00:00\"},{\"dt\":1482559200,\"main\":{\"temp\":4.85,\"temp_min\":4.85,\"temp_max\":4.85,\"pressure\":1011.2,\"sea_level\":1042.71,\"grnd_level\":1011.2,\"humidity\":95},\"weather\":[{\"id\":500,\"main\":\"Rain\",\"description\":\"light rain\",\"icon\":\"10n\"}],\"clouds\":{\"all\":92},\"wind\":{\"speed\":6.01,\"deg\":238.001},\"rain\":{\"3h\":0.63},\"sys\":{\"pod\":\"n\"},\"dt_txt\":\"2016-12-24 06:00:00\"},{\"dt\":1482570000,\"main\":{\"temp\":5.75,\"temp_min\":5.75,\"temp_max\":5.75,\"pressure\":1011.43,\"sea_level\":1042.92,\"grnd_level\":1011.43,\"humidity\":96},\"weather\":[{\"id\":500,\"main\":\"Rain\",\"description\":\"light rain\",\"icon\":\"10d\"}],\"clouds\":{\"all\":88},\"wind\":{\"speed\":5.01,\"deg\":242.002},\"rain\":{\"3h\":0.24},\"sys\":{\"pod\":\"d\"},\"dt_txt\":\"2016-12-24 09:00:00\"},{\"dt\":1482580800,\"main\":{\"temp\":6.85,\"temp_min\":6.85,\"temp_max\":6.85,\"pressure\":1012.08,\"sea_level\":1043.36,\"grnd_level\":1012.08,\"humidity\":96},\"weather\":[{\"id\":500,\"main\":\"Rain\",\"description\":\"light rain\",\"icon\":\"10d\"}],\"clouds\":{\"all\":68},\"wind\":{\"speed\":5.17,\"deg\":259.505},\"rain\":{\"3h\":0.04},\"sys\":{\"pod\":\"d\"},\"dt_txt\":\"2016-12-24 12:00:00\"},{\"dt\":1482591600,\"main\":{\"temp\":6,\"temp_min\":6,\"temp_max\":6,\"pressure\":1011.72,\"sea_level\":1043.11,\"grnd_level\":1011.72,\"humidity\":95},\"weather\":[{\"id\":500,\"main\":\"Rain\",\"description\":\"light rain\",\"icon\":\"10d\"}],\"clouds\":{\"all\":88},\"wind\":{\"speed\":4.83,\"deg\":256.003},\"rain\":{\"3h\":0.02},\"sys\":{\"pod\":\"d\"},\"dt_txt\":\"2016-12-24 15:00:00\"},{\"dt\":1482602400,\"main\":{\"temp\":5.1,\"temp_min\":5.1,\"temp_max\":5.1,\"pressure\":1012.4,\"sea_level\":1043.89,\"grnd_level\":1012.4,\"humidity\":94},\"weather\":[{\"id\":500,\"main\":\"Rain\",\"description\":\"light rain\",\"icon\":\"10n\"}],\"clouds\":{\"all\":88},\"wind\":{\"speed\":5.11,\"deg\":245.501},\"rain\":{\"3h\":0.04},\"sys\":{\"pod\":\"n\"},\"dt_txt\":\"2016-12-24 18:00:00\"},{\"dt\":1482613200,\"main\":{\"temp\":4.67,\"temp_min\":4.67,\"temp_max\":4.67,\"pressure\":1011.79,\"sea_level\":1043.27,\"grnd_level\":1011.79,\"humidity\":95},\"weather\":[{\"id\":500,\"main\":\"Rain\",\"description\":\"light rain\",\"icon\":\"10n\"}],\"clouds\":{\"all\":76},\"wind\":{\"speed\":5.81,\"deg\":241.005},\"rain\":{\"3h\":0.02},\"sys\":{\"pod\":\"n\"},\"dt_txt\":\"2016-12-24 21:00:00\"},{\"dt\":1482624000,\"main\":{\"temp\":4.74,\"temp_min\":4.74,\"temp_max\":4.74,\"pressure\":1011.04,\"sea_level\":1042.39,\"grnd_level\":1011.04,\"humidity\":96},\"weather\":[{\"id\":500,\"main\":\"Rain\",\"description\":\"light rain\",\"icon\":\"10n\"}],\"clouds\":{\"all\":92},\"wind\":{\"speed\":5.92,\"deg\":237.501},\"rain\":{\"3h\":0.07},\"sys\":{\"pod\":\"n\"},\"dt_txt\":\"2016-12-25 00:00:00\"},{\"dt\":1482634800,\"main\":{\"temp\":5.2,\"temp_min\":5.2,\"temp_max\":5.2,\"pressure\":1009.67,\"sea_level\":1040.98,\"grnd_level\":1009.67,\"humidity\":96},\"weather\":[{\"id\":500,\"main\":\"Rain\",\"description\":\"light rain\",\"icon\":\"10n\"}],\"clouds\":{\"all\":92},\"wind\":{\"speed\":6.22,\"deg\":243.007},\"rain\":{\"3h\":0.03},\"sys\":{\"pod\":\"n\"},\"dt_txt\":\"2016-12-25 03:00:00\"},{\"dt\":1482645600,\"main\":{\"temp\":5.17,\"temp_min\":5.17,\"temp_max\":5.17,\"pressure\":1008.52,\"sea_level\":1039.75,\"grnd_level\":1008.52,\"humidity\":97},\"weather\":[{\"id\":500,\"main\":\"Rain\",\"description\":\"light rain\",\"icon\":\"10n\"}],\"clouds\":{\"all\":92},\"wind\":{\"speed\":6.47,\"deg\":238.5},\"rain\":{\"3h\":0.3},\"sys\":{\"pod\":\"n\"},\"dt_txt\":\"2016-12-25 06:00:00\"},{\"dt\":1482656400,\"main\":{\"temp\":5.55,\"temp_min\":5.55,\"temp_max\":5.55,\"pressure\":1008.61,\"sea_level\":1039.71,\"grnd_level\":1008.61,\"humidity\":96},\"weather\":[{\"id\":500,\"main\":\"Rain\",\"description\":\"light rain\",\"icon\":\"10d\"}],\"clouds\":{\"all\":92},\"wind\":{\"speed\":7.16,\"deg\":244.501},\"rain\":{\"3h\":0.44},\"sys\":{\"pod\":\"d\"},\"dt_txt\":\"2016-12-25 09:00:00\"},{\"dt\":1482667200,\"main\":{\"temp\":7.21,\"temp_min\":7.21,\"temp_max\":7.21,\"pressure\":1008.28,\"sea_level\":1039.18,\"grnd_level\":1008.28,\"humidity\":96},\"weather\":[{\"id\":500,\"main\":\"Rain\",\"description\":\"light rain\",\"icon\":\"10d\"}],\"clouds\":{\"all\":92},\"wind\":{\"speed\":7.21,\"deg\":245.501},\"rain\":{\"3h\":0.43},\"sys\":{\"pod\":\"d\"},\"dt_txt\":\"2016-12-25 12:00:00\"},{\"dt\":1482678000,\"main\":{\"temp\":8.61,\"temp_min\":8.61,\"temp_max\":8.61,\"pressure\":1008.29,\"sea_level\":1038.98,\"grnd_level\":1008.29,\"humidity\":96},\"weather\":[{\"id\":500,\"main\":\"Rain\",\"description\":\"light rain\",\"icon\":\"10d\"}],\"clouds\":{\"all\":92},\"wind\":{\"speed\":7.01,\"deg\":248.001},\"rain\":{\"3h\":0.46},\"sys\":{\"pod\":\"d\"},\"dt_txt\":\"2016-12-25 15:00:00\"},{\"dt\":1482688800,\"main\":{\"temp\":9.44,\"temp_min\":9.44,\"temp_max\":9.44,\"pressure\":1008.55,\"sea_level\":1039.33,\"grnd_level\":1008.55,\"humidity\":96},\"weather\":[{\"id\":500,\"main\":\"Rain\",\"description\":\"light rain\",\"icon\":\"10n\"}],\"clouds\":{\"all\":92},\"wind\":{\"speed\":6.61,\"deg\":247.001},\"rain\":{\"3h\":0.31},\"sys\":{\"pod\":\"n\"},\"dt_txt\":\"2016-12-25 18:00:00\"},{\"dt\":1482699600,\"main\":{\"temp\":9.82,\"temp_min\":9.82,\"temp_max\":9.82,\"pressure\":1008.2,\"sea_level\":1039.09,\"grnd_level\":1008.2,\"humidity\":96},\"weather\":[{\"id\":500,\"main\":\"Rain\",\"description\":\"light rain\",\"icon\":\"10n\"}],\"clouds\":{\"all\":92},\"wind\":{\"speed\":6.66,\"deg\":244.001},\"rain\":{\"3h\":0.21},\"sys\":{\"pod\":\"n\"},\"dt_txt\":\"2016-12-25 21:00:00\"},{\"dt\":1482710400,\"main\":{\"temp\":9.54,\"temp_min\":9.54,\"temp_max\":9.54,\"pressure\":1008.2,\"sea_level\":1039.04,\"grnd_level\":1008.2,\"humidity\":96},\"weather\":[{\"id\":500,\"main\":\"Rain\",\"description\":\"light rain\",\"icon\":\"10n\"}],\"clouds\":{\"all\":92},\"wind\":{\"speed\":6.82,\"deg\":245.503},\"rain\":{\"3h\":0.15},\"sys\":{\"pod\":\"n\"},\"dt_txt\":\"2016-12-26 00:00:00\"},{\"dt\":1482721200,\"main\":{\"temp\":9.24,\"temp_min\":9.24,\"temp_max\":9.24,\"pressure\":1008.17,\"sea_level\":1039,\"grnd_level\":1008.17,\"humidity\":96},\"weather\":[{\"id\":500,\"main\":\"Rain\",\"description\":\"light rain\",\"icon\":\"10n\"}],\"clouds\":{\"all\":92},\"wind\":{\"speed\":7.16,\"deg\":244.003},\"rain\":{\"3h\":0.15},\"sys\":{\"pod\":\"n\"},\"dt_txt\":\"2016-12-26 03:00:00\"},{\"dt\":1482732000,\"main\":{\"temp\":8.92,\"temp_min\":8.92,\"temp_max\":8.92,\"pressure\":1008.49,\"sea_level\":1039.45,\"grnd_level\":1008.49,\"humidity\":96},\"weather\":[{\"id\":500,\"main\":\"Rain\",\"description\":\"light rain\",\"icon\":\"10n\"}],\"clouds\":{\"all\":92},\"wind\":{\"speed\":7.45,\"deg\":251.509},\"rain\":{\"3h\":0.23},\"sys\":{\"pod\":\"n\"},\"dt_txt\":\"2016-12-26 06:00:00\"},{\"dt\":1482742800,\"main\":{\"temp\":9.06,\"temp_min\":9.06,\"temp_max\":9.06,\"pressure\":1009.22,\"sea_level\":1040.36,\"grnd_level\":1009.22,\"humidity\":96},\"weather\":[{\"id\":500,\"main\":\"Rain\",\"description\":\"light rain\",\"icon\":\"10d\"}],\"clouds\":{\"all\":92},\"wind\":{\"speed\":7.81,\"deg\":250.502},\"rain\":{\"3h\":0.43},\"sys\":{\"pod\":\"d\"},\"dt_txt\":\"2016-12-26 09:00:00\"},{\"dt\":1482753600,\"main\":{\"temp\":8,\"temp_min\":8,\"temp_max\":8,\"pressure\":1011.76,\"sea_level\":1042.79,\"grnd_level\":1011.76,\"humidity\":98},\"weather\":[{\"id\":500,\"main\":\"Rain\",\"description\":\"light rain\",\"icon\":\"10d\"}],\"clouds\":{\"all\":12},\"wind\":{\"speed\":6.26,\"deg\":303.5},\"rain\":{\"3h\":2.29},\"sys\":{\"pod\":\"d\"},\"dt_txt\":\"2016-12-26 12:00:00\"},{\"dt\":1482764400,\"main\":{\"temp\":5.54,\"temp_min\":5.54,\"temp_max\":5.54,\"pressure\":1014.3,\"sea_level\":1045.57,\"grnd_level\":1014.3,\"humidity\":91},\"weather\":[{\"id\":804,\"main\":\"Clouds\",\"description\":\"overcast clouds\",\"icon\":\"04d\"}],\"clouds\":{\"all\":88},\"wind\":{\"speed\":6.35,\"deg\":287.501},\"rain\":{\"3h\":0},\"sys\":{\"pod\":\"d\"},\"dt_txt\":\"2016-12-26 15:00:00\"},{\"dt\":1482775200,\"main\":{\"temp\":4.95,\"temp_min\":4.95,\"temp_max\":4.95,\"pressure\":1016.99,\"sea_level\":1048.49,\"grnd_level\":1016.99,\"humidity\":90},\"weather\":[{\"id\":500,\"main\":\"Rain\",\"description\":\"light rain\",\"icon\":\"10n\"}],\"clouds\":{\"all\":88},\"wind\":{\"speed\":5.66,\"deg\":285.005},\"rain\":{\"3h\":0.0099999999999998},\"sys\":{\"pod\":\"n\"},\"dt_txt\":\"2016-12-26 18:00:00\"},{\"dt\":1482786000,\"main\":{\"temp\":4.18,\"temp_min\":4.18,\"temp_max\":4.18,\"pressure\":1019.22,\"sea_level\":1050.89,\"grnd_level\":1019.22,\"humidity\":87},\"weather\":[{\"id\":500,\"main\":\"Rain\",\"description\":\"light rain\",\"icon\":\"10n\"}],\"clouds\":{\"all\":76},\"wind\":{\"speed\":5.63,\"deg\":279.001},\"rain\":{\"3h\":0.02},\"sys\":{\"pod\":\"n\"},\"dt_txt\":\"2016-12-26 21:00:00\"},{\"dt\":1482796800,\"main\":{\"temp\":3.98,\"temp_min\":3.98,\"temp_max\":3.98,\"pressure\":1020.76,\"sea_level\":1052.48,\"grnd_level\":1020.76,\"humidity\":86},\"weather\":[{\"id\":803,\"main\":\"Clouds\",\"description\":\"broken clouds\",\"icon\":\"04n\"}],\"clouds\":{\"all\":68},\"wind\":{\"speed\":5.94,\"deg\":279.503},\"rain\":{\"3h\":0},\"sys\":{\"pod\":\"n\"},\"dt_txt\":\"2016-12-27 00:00:00\"},{\"dt\":1482807600,\"main\":{\"temp\":4.33,\"temp_min\":4.33,\"temp_max\":4.33,\"pressure\":1021.71,\"sea_level\":1053.55,\"grnd_level\":1021.71,\"humidity\":86},\"weather\":[{\"id\":803,\"main\":\"Clouds\",\"description\":\"broken clouds\",\"icon\":\"04n\"}],\"clouds\":{\"all\":68},\"wind\":{\"speed\":5.4,\"deg\":287.5},\"rain\":{\"3h\":0},\"sys\":{\"pod\":\"n\"},\"dt_txt\":\"2016-12-27 03:00:00\"},{\"dt\":1482818400,\"main\":{\"temp\":4.2,\"temp_min\":4.2,\"temp_max\":4.2,\"pressure\":1022.89,\"sea_level\":1054.86,\"grnd_level\":1022.89,\"humidity\":85},\"weather\":[{\"id\":803,\"main\":\"Clouds\",\"description\":\"broken clouds\",\"icon\":\"04n\"}],\"clouds\":{\"all\":80},\"wind\":{\"speed\":4.76,\"deg\":299.001},\"rain\":{\"3h\":0},\"sys\":{\"pod\":\"n\"},\"dt_txt\":\"2016-12-27 06:00:00\"},{\"dt\":1482829200,\"main\":{\"temp\":4.66,\"temp_min\":4.66,\"temp_max\":4.66,\"pressure\":1025.25,\"sea_level\":1057.2,\"grnd_level\":1025.25,\"humidity\":86},\"weather\":[{\"id\":802,\"main\":\"Clouds\",\"description\":\"scattered clouds\",\"icon\":\"03d\"}],\"clouds\":{\"all\":32},\"wind\":{\"speed\":3.57,\"deg\":300.002},\"rain\":{\"3h\":0},\"sys\":{\"pod\":\"d\"},\"dt_txt\":\"2016-12-27 09:00:00\"},{\"dt\":1482840000,\"main\":{\"temp\":6.45,\"temp_min\":6.45,\"temp_max\":6.45,\"pressure\":1026.42,\"sea_level\":1058.23,\"grnd_level\":1026.42,\"humidity\":90},\"weather\":[{\"id\":802,\"main\":\"Clouds\",\"description\":\"scattered clouds\",\"icon\":\"03d\"}],\"clouds\":{\"all\":36},\"wind\":{\"speed\":3.57,\"deg\":299.001},\"rain\":{\"3h\":0},\"sys\":{\"pod\":\"d\"},\"dt_txt\":\"2016-12-27 12:00:00\"},{\"dt\":1482850800,\"main\":{\"temp\":5.72,\"temp_min\":5.72,\"temp_max\":5.72,\"pressure\":1027.42,\"sea_level\":1059.12,\"grnd_level\":1027.42,\"humidity\":86},\"weather\":[{\"id\":802,\"main\":\"Clouds\",\"description\":\"scattered clouds\",\"icon\":\"03d\"}],\"clouds\":{\"all\":32},\"wind\":{\"speed\":2.27,\"deg\":300.002},\"rain\":{\"3h\":0},\"sys\":{\"pod\":\"d\"},\"dt_txt\":\"2016-12-27 15:00:00\"},{\"dt\":1482861600,\"main\":{\"temp\":2.21,\"temp_min\":2.21,\"temp_max\":2.21,\"pressure\":1028.24,\"sea_level\":1060.34,\"grnd_level\":1028.24,\"humidity\":85},\"weather\":[{\"id\":802,\"main\":\"Clouds\",\"description\":\"scattered clouds\",\"icon\":\"03n\"}],\"clouds\":{\"all\":32},\"wind\":{\"speed\":1.22,\"deg\":254.001},\"rain\":{\"3h\":0},\"sys\":{\"pod\":\"n\"},\"dt_txt\":\"2016-12-27 18:00:00\"},{\"dt\":1482872400,\"main\":{\"temp\":0.99,\"temp_min\":0.99,\"temp_max\":0.99,\"pressure\":1029.14,\"sea_level\":1061.34,\"grnd_level\":1029.14,\"humidity\":73},\"weather\":[{\"id\":802,\"main\":\"Clouds\",\"description\":\"scattered clouds\",\"icon\":\"03n\"}],\"clouds\":{\"all\":32},\"wind\":{\"speed\":1.17,\"deg\":219.501},\"rain\":{\"3h\":0},\"sys\":{\"pod\":\"n\"},\"dt_txt\":\"2016-12-27 21:00:00\"}]}";
                        ThreeHoursForecastWeather threeHoursForecastWeather = new Gson().fromJson(response, ThreeHoursForecastWeather.class);
                        return Response.success(threeHoursForecastWeather);
                    }
                });
        presenter.loadForecastWeatherDataForToday();
        String expected = createExpectedResult();
        verify(view).requestStarted();
        verify(view).showForecastWeather(expected);
        verify(view).requestFinished();
    }

    private String createExpectedResult() {
        StringBuilder sb = new StringBuilder();
        sb.append("2016-12-23 15:00:00: 3.53°C").append("\n");
        sb.append("2016-12-23 18:00:00: 1.91°C").append("\n");
        sb.append("2016-12-23 21:00:00: 2.61°C").append("\n");
        return sb.toString();
    }

    private Calendar createCurrentDate(int year, int month, int day, int hour, int minute, int second) {
        Calendar instance = Calendar.getInstance(Locale.GERMANY);
        instance.setTimeZone(TimeZone.getTimeZone("Europe/Berlin"));
        instance.set(year, month, day, hour, minute, second);
        return instance;
    }


    private abstract static class CallAdapter<T> implements Call<T> {

        @Override
        public Response<T> execute() throws IOException {
            return createResponse();
        }

        protected abstract Response<T> createResponse();

        @Override
        public void enqueue(Callback<T> callback) {

        }

        @Override
        public boolean isExecuted() {
            return false;
        }

        @Override
        public void cancel() {

        }

        @Override
        public boolean isCanceled() {
            return false;
        }

        @Override
        public Call<T> clone() {
            return null;
        }

        @Override
        public Request request() {
            return null;
        }
    }

}