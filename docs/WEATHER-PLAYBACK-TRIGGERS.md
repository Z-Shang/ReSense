# Automatic full-screen weather entry

Returning from a widget's editor or another activity is an ordinary launcher
resume. It must not trigger full-screen weather. Examples include adding a
to-do item through a third-party widget, configuring a widget, and returning
from launcher settings.

`FullscreenWeather.resume` now schedules automatic playback only for initial
entry or an unlock. Explicit home entry is routed through `enterHome`, which
also remembers a HOME request delivered before the activity resumes. The
existing home/unlock settings, readiness checks and playback cooldown apply.
Drawer-to-home and deliberate desktop rebuild actions retain their existing
home-entry behavior.

`MainActivity.onNewIntent` recognizes ACTION_MAIN with HOME or LAUNCHER as an
explicit home request. Other intents rebuild the desktop without scheduling
weather. A saved-state activity recreation also rebuilds without scheduling;
widget completion paths use that same quiet desktop rebuild.

The `widget_weather_return` instrumentation suite uses an actual RemoteViews
click and PendingIntent to open an editor activity fixture. It checks that:

* Initial home entry still plays weather.
* Finishing the widget's activity does not replay weather.
* A non-home intent does not replay weather.
* Saved-state launcher recreation does not replay weather.
* An explicit HOME request while the editor is open still plays weather.

The existing `fullscreen_weather` suite covers unlock, touch dismissal,
drawer transitions and activity cleanup. Its explicit home-entry fixture uses
`enterHome` rather than treating an arbitrary resume as a home request.

The fixture does not modify real widget data and restores weather/effect
preferences afterward. The editor fixture uses the existing launcher settings
activity solely to exercise Android's activity pause/resume path.

Validation on API 35: shared APK build and launcher lint passed, as did the
`widget_weather_return` suite and the existing `fullscreen_weather` suite.
The latter passed on retry after the emulator's intermittent unlock timeout,
also observed before this fix. Logs are under `.toolchain/` with the
`widget-weather-return-` prefix; the first timeout is retained separately.
