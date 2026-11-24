# Sense Weather

The `:weather` module builds a separate native Android 15+ app,
`dev.sense.weather`. It adapts the recovered Weather.apk layout structure:
`main.xml`, `header_bar.xml`, `today_condition.xml`, `tab_now.xml`,
`tab_fday.xml`, `tab_fday_item.xml` and the hourly/detail panels. Original
compiled XML cannot be inflated against stock Android because it references
HTC framework resource IDs and custom classes. `weather_main.xml` is the new
source XML counterpart; this is an adaptation, not the original executable.

`tools/prepare_weather_app.py` reads the local ROM directly. It preserves the
Weather.apk PNG/nine-patch artwork and framework forecast icons, plus all 40
condition textures/tracks already decoded for the launcher. These build-ready
resources are checked in; the ROM is only needed for explicit regeneration.
Hashes/source paths are retained in
`assets/htc/weather/res/raw/weather_app_provenance.json`.
The original AccuWeather logos are excluded because the data comes from Open-Meteo.

The Now view follows `res/weather_now.png`: original full-scene video with
current conditions overlaid at the top, a single-line provider/update ribbon, and light
Today/Tonight/Tomorrow forecast cards. The rounded text-only Now / Hourly / 5-day
group uses original HTC green selected and dark resting button assets.

[Animation recovery](WEATHER-APP-ANIMATIONS.md) records the original assets and
loading path. `WeatherSceneView` now uses asynchronous native `MediaPlayer`
playback on a center-cropped `TextureView`, with matching original JPEG fallbacks.
Playback stops outside the activity and on other forecast tabs; tapping the scene
replays it. Disabled system animations show the still image. The footage remains
30 fps, while native UI rendering uses Android's frame clock. MP4 resources are
uncompressed so the decoder can access their file descriptors.

Today and Tomorrow show the actual daily condition and high temperature on those
dates in the city's timezone. Tonight uses hourly samples between local sunset
and tomorrow's sunrise (18:00–06:00 for older caches), showing the minimum
temperature and most frequent valid night condition. Missing hourly data can
fall back to the daily low without inventing a condition. The provider/update
ribbon sits above the cards on Now and 5-day and above the buttons on Hourly, using
Open-Meteo/GeoNames attribution instead of the original AccuWeather branding.

The title-bar city dropdown has a permanently pinned **Current location** row
above its scrolling saved-city list, including when that list is empty. The row
uses HTC's location icon and cannot be removed. Selecting it requests foreground
coarse/fine location permission; approximate access is accepted. Android's native
fused/network/GPS providers obtain a current fix, with a 30-second timeout and
cancellation when leaving Weather or selecting a saved city. No background
location permission or Google Play services dependency is used.

Current-location mode refreshes its position when opening Weather or pressing
Refresh. GPS coordinates remain separate from saved cities, retain the normal
per-coordinate forecast cache, and discard stale responses after moving or
selecting another city. Permission denial, disabled services and a missing fix
leave the previous city/forecast available. Manual city search still needs no
location permission.

The app has current conditions, hourly forecasts (the next 24 entries), five
upcoming calendar days, Today/Tonight/Tomorrow summaries,
city search, refresh, original condition animation replay, and last-good cached
data when a refresh fails. Temperatures are Celsius and wind speed is km/h.
Forecast times use the selected city's timezone. Search does not require device
location access and runs independently from forecast fetches.

The module's `generateSharedWeather` task compiles the same Java repository,
condition mapping used by the launcher, with the package changed
into the weather namespace. This avoids a second fork of their implementations.
With Sense Framework Services installed, both apps share the active city,
GPS mode, saved locations and per-city forecasts through the framework's data API.
Changing a city in Weather updates the launcher clock and its weather effects;
launcher weather changes update Weather too. Existing private stores migrate on
first connection. Rendering uses local mirrors, so IPC stays off the UI thread.
See [framework-owned shared weather](SENSE-FRAMEWORK.md#framework-owned-shared-weather).
Without the runtime, either app can still use its own local store. The launcher's
clock widget opens Weather without overwriting a shared GPS selection.

Build from the repository root:

```sh
source tools/android-env.sh
./gradlew :weather:assembleDebug
# Optional release build (unsigned until signing is configured):
./gradlew :weather:assembleRelease
```

Debug APK: `weather/build/outputs/apk/debug/weather-debug.apk`.
Install both launcher and weather APKs to enable the widget entry point.

```sh
adb install -r weather/build/outputs/apk/debug/weather-debug.apk
adb shell am start -n dev.sense.weather/.WeatherActivity
```

Validation includes native activity tests with Open-Meteo-shaped fixture data,
24 chart samples, five upcoming-day cards, full-screen toggling, actual recovered NinePatchDrawable/icon resources,
missing optional weather codes, cached-data error state, recreation and city
picker opening. A screenshot is saved as `files/sense-weather-test.png` by the
weather instrumentation. The live emulator fetch also returned five daily and
120 hourly records for Sydney during development.

API references:
- https://open-meteo.com/en/docs
- https://open-meteo.com/en/docs/geocoding-api

Hourly follows `res/weather_hourly.png`: a light grid with a gold temperature
area/line chart, hour labels, and a matching weather-icon/temperature strip.
Swipe horizontally through the next 24 available hours. Temperature gaps are
left as gaps, the scale adapts to the actual values, and times use the city's
zone. It replaces the earlier vertical hourly list.

The 5-day tab follows `res/weather_5_day.png`: the same current-weather scene and
single-line provider/update ribbon above five upcoming-day columns, each with
a weekday, date, original weather icon and high/low temperature. The data request
includes six calendar days to provide today plus five future days. Older caches
request the extended data and show missing values rather than repeat a day.

A title-bar full-screen/fit-screen control expands the animation below the title
bar on Now and 5-day, hiding the ribbon, cards, condition text and tab group.
Pressing it again or Back restores the forecast view; the mode survives rotation.
Hourly hides this control.

Launcher regression coverage also taps an actual `SenseClockView` and verifies
that it targets `dev.sense.weather/.WeatherActivity` with the selected city's
name, latitude and longitude. Weather controls inside Sense Settings continue to
open the settings section; the home widget opens the companion app.

## Saved cities and vertical navigation

The title opens a dropdown with pinned Current location and saved cities. The
Add city action at the bottom of the dropdown opens city search. Selecting a search
result saves it; subsequent dropdown selections reuse it. Existing single-city
preferences migrate naturally as the first saved location. Coordinate keys dedupe
saved entries, while region/country labels distinguish similarly named cities.
The selected city and ordered list survive app restart and rotation.

Swipe **up** on the city title or current-weather panel to move to the next saved
city; swipe **down** to move to the previous city. The sequence wraps at either
end. With one saved city, swipes do nothing. The forecast tab stays selected and
returns to the top for the new city. Hourly chart gestures scroll horizontally;
hidden current-condition views are excluded from hit testing.
Horizontal and multi-pointer movement before activation do not switch cities.
A vertical city change cancels the original child touch before changing contents.

The shared repository keeps a last-good forecast for every coordinate key.
Switching restores that city's cache immediately, refreshing only when needed.
Results arriving for a city that is no longer selected update only its cache;
they cannot overwrite the selected city's forecast or error status. Both legacy
current-city data and forecasts fetched before switching are preserved.

Emulator regression tests exercise the actual dropdown and checked selection,
up/down wraparound, tab preservation, city-specific cached temperatures,
late-response isolation, normal hourly scrolling, duplicate/invalid saved entries,
rotation and the dropdown Add city action. Test preferences are backed up before
fixtures and restored afterward.

The city dropdown uses the ROM's `common_popupmenu.9.png` panel, list divider,
pressed-row highlight, dropdown button and radio/check artwork from
`com.htc.resources.apk`. Compiled nine-patch chunks are retained by extraction.
Rows use native checkable rows, CheckedTextView labels and ListPopupWindow behavior with the recovered HTC
visuals. A pinned Current location entry sits above the saved-city rows; adding
a saved city uses the dropdown Add city action.

Each saved-city row has a separate trash button using HTC's original delete
artwork. Removing another city keeps the current forecast. Removing the selected
city selects the next remaining row (or previous row at the end), restoring its
cache. Removing the final city shows an Add city state and leaves the title-bar
dropdown Add city action available. The empty list persists; no default city or late network
response recreates a removed entry. The removed city's cache is deleted too.
The header dropdown indicator uses the earlier white chevron, with the HTC popup
panel/rows retained.

An open city dropdown updates its adapter in place when saved cities or the
selected location change. Deleting a row does not dismiss/reopen the window;
scroll position is retained, and saved-city/GPS checks are refreshed without
rebuilding the popup during weather-data updates. Current location and Add city
remain available after the last saved city is removed.

Common tab, popup, divider, radio and city-delete styling now comes from
[`sense-framework`](SENSE-FRAMEWORK.md), using `dev.sense.framework.SenseStyle`.
Weather scenes, condition icons and Weather-specific chrome remain local.
