package com.mvp.weather_example.presenter;

import android.location.Location;

import com.mvp.annotation.BackgroundThread;
import com.mvp.annotation.Presenter;
import com.mvp.weather_example.di.ComponentWeather;
import com.mvp.weather_example.model.Weather;
import com.mvp.weather_example.model.forecast.threehours.ThreeHoursForecastWeather;
import com.mvp.weather_example.service.LocationProvider;
import com.mvp.weather_example.service.WeatherResponseFilter;
import com.mvp.weather_example.service.WeatherService;

import java.io.IOException;
import java.text.ParseException;

import javax.inject.Inject;
import javax.inject.Named;

@Presenter(needsComponents = { ComponentWeather.class })
public class TomorrowWeatherPresenter extends WeatherPresenter {
    private static final int FORECAST_DAYS = 1;


    protected TomorrowWeatherPresenter() { }

    @Inject
    public TomorrowWeatherPresenter(LocationProvider locationProvider, WeatherService weatherService, @Named("Tomorrow") WeatherResponseFilter weatherParser) {
        super(locationProvider, weatherService, weatherParser);
    }

    @Override
    protected void loadWeather(double longitude, double latitude)
    {
        try{
            dispatchRequestStarted();
            final Weather weather = weatherService.getTomorrowWeather(longitude, latitude, "metric", FORECAST_DAYS);
            updateState(weather);
            submitOnUiThread(() -> postWeatherResult(weather));
        }catch(IOException e){
            dispatchEvent(e).toAny();
        }
    }

    private void postWeatherResult(Weather weather)
    {
        weatherService.loadIcon(weather.icon(), TomorrowWeatherPresenter.this);
        getView().showWeather(lastTemperature, lastHumidity);
        dispatchRequestFinished();
    }

    @BackgroundThread
    public void loadForecastWeatherDataForTomorrow()
    {
        try
        {
            Location lastKnownLocation = locationProvider.lastLocation();
            if (lastKnownLocation != null)
            {
                dispatchRequestStarted();
                double longitude = lastKnownLocation.getLongitude();
                double latitude = lastKnownLocation.getLatitude();
                ThreeHoursForecastWeather weather = weatherService.getForecastWeather(longitude, latitude, "metric");
                final String forecastData = weatherParser.parse(weather);
                submitOnUiThread(() -> postForecastWeather(forecastData));
            }
        }catch(IOException e) {
            dispatchEvent(e).toAny();
        }catch(ParseException e) {
            e.printStackTrace();
        }finally{
            dispatchRequestFinished();
        }
    }

    private void postForecastWeather(String forecastData)
    {
        getView().showForecastWeather(forecastData);
        dispatchRequestFinished();
    }
}
