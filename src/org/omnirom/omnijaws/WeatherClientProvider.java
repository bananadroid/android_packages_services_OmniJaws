/*
 * Copyright (C) 2013 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.omnirom.omnijaws;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.omnirom.omnijaws.WeatherInfo.DayForecast;

import okhttp3.Cache;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import android.content.Context;
import android.content.res.Resources;
import android.location.Location;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

public class WeatherClientProvider extends AbstractWeatherProvider {
    private static final String TAG = "WeatherClientProvider";

    private static final int FORECAST_DAYS = 5;

    // WeatherClient resources
    private static final String WCL_SELECTION_LOCATION = "%f,%f";
    private static final String WCL_URL_LOCATION = "https://weather.codes/search/?q=%s";
    private static final String WCL_URL_WEATHER = "https://weather.com/%s/weather/today/l/%s?par=google&temp=%s";
    private static final String WCL_URL_FORECAST = "https://weather.com/%s/weather/tenday/l/%s?par=google&temp=%s";

    private boolean mUsesWeatherClientID = true;
    private OkHttpClient mHttpClient;
    private int mRequestNumber;
    private int mSunrise;
    private int mSunset;

    public WeatherClientProvider(Context context) {
        super(context);
        mHttpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .build();
    }

    public List<WeatherInfo.WeatherLocation> getLocations(String input) {
        String locationsUrl = String.format(WCL_URL_LOCATION, Uri.encode(input));
        try {
            Response response = mHttpClient.newCall(new Request.Builder()
                    .tag("WeatherChannelApi")
                    .url(locationsUrl)
                    .build()).execute();
            if (response.body() != null && response.isSuccessful()) {
                ArrayList<WeatherInfo.WeatherLocation> results = new ArrayList<WeatherInfo.WeatherLocation>();
                String result = response.body().string();
                Document doc = Jsoup.parse(result);
                Elements locationCountries = doc.select("div[class=country__codes__letter] h2");
                Elements locationCities = doc.select("div[class=country__codes__letter] ul li strong");
                Elements locationCitiesId = doc.select("div[class=country__codes__letter] ul li span");
                for (int i = 0; i < locationCountries.size(); i++) {
                    WeatherInfo.WeatherLocation locationInfo = new WeatherInfo.WeatherLocation();
                    // This ID is required to make the WeatherClient compatible with custom locations
                    locationInfo.id = locationCitiesId.get(i).text();
                    locationInfo.city = locationCities.get(i).text();
                    locationInfo.country = locationCountries.get(i).text();
                    locationInfo.countryId = locationCitiesId.get(i).text();
                    results.add(locationInfo);
                }
                return results;
            }
        } catch (Exception e) {
            Log.w(TAG, "Couldn't find the right locations", e);
        }
        return null;
    }

    public WeatherInfo getCustomWeather(String id, boolean metric) {
        return handleWeatherRequest(id, metric);
    }

    public WeatherInfo getLocationWeather(Location location, boolean metric) {
        String selection;
        selection = String.format(Locale.US, WCL_SELECTION_LOCATION,
            location.getLatitude(), location.getLongitude());
        return handleWeatherRequest(selection, metric);
    }

    private WeatherInfo handleWeatherRequest(String selection, boolean metric) {
        Locale locale = mContext.getResources().getConfiguration().locale;
        String localeSelector = locale.getLanguage() + "-" + locale.getCountry();
        String metricSelected = metric ? "c" : "f";
        String conditionUrl = String.format(Locale.US, WCL_URL_WEATHER, localeSelector, selection, metricSelected);
        String forecastUrl = String.format(Locale.US, WCL_URL_FORECAST, localeSelector, selection, metricSelected);

        try {
            Response conditionResponse = mHttpClient.newCall(new Request.Builder()
                    .tag("WeatherChannelApi")
                    .url(conditionUrl)
                    .build()).execute();
            Response conditionForecast = mHttpClient.newCall(new Request.Builder()
                    .tag("WeatherChannelApi")
                    .url(forecastUrl)
                    .build()).execute();
            mRequestNumber++;
            if ((conditionResponse.body() != null && conditionResponse.isSuccessful()) &&
                (conditionForecast.body() != null && conditionForecast.isSuccessful())) {
                String conditionResult = conditionResponse.body().string();
                String conditionForecastResult = conditionForecast.body().string();
                Document doc = Jsoup.parse(conditionResult);
                Document docForecast = Jsoup.parse(conditionForecastResult);
                Element todayTemp = doc.selectFirst("span[class*=feelsLike][data-testid=TemperatureValue]");
                double tempSanitized = Double.parseDouble(todayTemp.text().replace("°", ""));
                double humidity = Double.parseDouble(doc.selectFirst(
                    "div[class*=DetailsList] span[data-testid=PercentageValue]").text().replace("%", ""));

                // gets the core for the icons
                Element conditionIconElement = doc.selectFirst("svg[class*=CurrentConditions]");
                int weatherIcon = Integer.parseInt(conditionIconElement.attr("skycode"));

                // Checking the city name with the forecast because we can't get a raw name from the today section
                String localizedCityName = docForecast.selectFirst("span[class*=LocationText] span").text().split(",")[0];
                String localizedPhrase = doc.selectFirst("div[class*=phraseValue]").text();

                String[] windData = doc.selectFirst("div[class*=DetailsList] span[data-testid=Wind]").text().split(" ");

                // Mostly the wind speed float is the previous to last on the list. the last item is the measure, which jaws
                // add automatically
                float windSpeed = Float.parseFloat(windData[windData.length - 2]);

                // NOTE: DOING THIS WORKAROUND BECAUSE THE WEATHER PROVIDER STARTS IN A 180 DEGREE
                String windirRotate = doc.selectFirst("div[class*=DetailsList] svg[style]").attr("style").split(":")[1];
                Log.i(TAG, "Current output from the wind rotation: " + windirRotate);
                int windDir = 180;
                if (windirRotate != null || windirRotate != "") {
                    windDir = windDir + Integer.parseInt(windirRotate.substring(7, windirRotate.length()-4));
                    if (windDir >= 360) windDir = windDir - 360;
                }
                ArrayList<DayForecast> forecasts = parseForecasts(doc, metric);

                WeatherInfo w = new WeatherInfo(mContext, selection /*ID workaround*/, localizedCityName,
                        /* condition */ localizedPhrase,
                        /* conditionCode */ weatherIcon,
                        /* temperature */ (float) tempSanitized,
                        /* humidity */ (float) humidity,
                        /* wind */ windSpeed,
                        /* windDir */ windDir,
                        metric,
                        forecasts,
                        System.currentTimeMillis());

                log(TAG, "Weather updated: " + w);
                return w;
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception", e);
        }
        return null;
    }

    private ArrayList<DayForecast> parseForecasts(Document todayDoc, boolean metric) {
        ArrayList<DayForecast> result = new ArrayList<DayForecast>();
        int maxItems = 5;
        Elements maxTemps = todayDoc.select(
            "div[class*=DailyWeather] ul li a div[data-testid*=HighTemp] span");
        Elements minTemps = todayDoc.select(
            "div[class*=DailyWeather] ul li a div[data-testid*=LowTemp] span");
        Elements skyCodes =  todayDoc.select(
            "div[class*=DailyWeather] ul li a div svg[set=weather]");
        for (int i = 0; i < maxItems; i++) {
            DayForecast item = null;

            String tempMaxSanitized = maxTemps.get(i).text().replace("°", "");
            double maxTemp;
            if (tempMaxSanitized.equals("--")) {
                maxTemp = Double.parseDouble(todayDoc.selectFirst(
                    "span[class*=feelsLike][data-testid=TemperatureValue]").text().replace("°", ""));
            } else {
                maxTemp = Double.parseDouble(tempMaxSanitized);
            }
            double minTemp = Double.parseDouble(minTemps.get(i).text().replace("°", ""));
            int weatherIcon = Integer.parseInt(skyCodes.get(i).attr("skycode"));
            // TODO: Get a better way to retrieve conditions
            String condition = "NaN";
            item = new DayForecast(
                    /* low */ (float) minTemp,
                    /* high */ (float) maxTemp,
                    /* condition */ condition,
                    /* conditionCode */ weatherIcon,
                    "NaN",
                    metric);
            result.add(item);
        }
        return result;
    }

    public boolean shouldRetry() {
        return false;
    }
}
