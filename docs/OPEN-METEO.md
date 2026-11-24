# Open-Meteo integration

The native clock uses HTTPS Open-Meteo forecasts instead of demo readings.
Sydney is the initial saved-city default; this is not device-location detection.
Tap the clock/weather widget or choose dock Settings → Weather to search for a
city, choose among geocoding matches, refresh, or open the attribution source.
Coordinates and city selection are saved locally. No GPS permission is requested.

Forecast parameters request current `temperature_2m`, `weather_code`, and
`is_day`, plus daily `temperature_2m_max` and `temperature_2m_min`, Celsius,
`timezone=auto`, `forecast_days=1`, and Unix timestamps. Daily bounds therefore
follow the forecast location's timezone; the clock/date remain device-local.

A shared repository performs requests on one worker thread with connection/read
timeouts and a 1 MiB response bound. It validates required numeric values before
saving the last successful response. Visible clocks check freshness each minute;
a reading is reused for 30 minutes. Failed requests have a two-minute retry
backoff. The widget displays cached data with a cached label when appropriate,
and dashes when no valid response exists. It does not revert to demo values.
Requests for a previously selected city cannot overwrite the new city's data.
No periodic background job is scheduled when the clock is detached.

Open-Meteo WMO codes now select original HTC condition scenes, including
night skies, rain, snow, freezing rain, fog and thunderstorms. All 40 original
clock conditions are available in the preview; see WEATHER-CATALOG.md for the
explicit WMO mapping and semantic approximations. Hand-drawn moon and
precipitation placeholders are no longer used. `tools/WeatherCodesCheck.java` verifies documented codes and unknown
fallbacks. API request fixtures from validation remain local under
`.toolchain/open-meteo/`.

Sources and attribution:
- [Forecast API](https://open-meteo.com/en/docs)
- [Geocoding API](https://open-meteo.com/en/docs/geocoding-api)
- [API terms](https://open-meteo.com/en/terms)
- [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)

The public endpoint requires no key for this non-commercial prototype. The
weather panel credits Open-Meteo; city search credits GeoNames via Open-Meteo.
