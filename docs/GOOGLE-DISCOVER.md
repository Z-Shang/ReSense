# Google Discover minus-one overlay

Swipe right from the first desktop to reveal the Google app's own Discover window.
Google renders and fetches the feed; it is not an embedded Activity, WebView or a
copy of the feed. The launcher retains its first desktop behind the overlay, so
Discover does not add a desktop or change the page indicator's size.

`DiscoverOverlay` binds the explicit Google package's
`com.android.launcher3.WINDOW_OVERLAY` service using the app://package:uid URI and
protocol versions v=7, cv=9. It passes the actual attached window token/layout and
configuration, advertises feed support, and forwards activity start/resume/pause
state. API metadata selects the older attach/state calls where necessary. Google
callbacks report availability and scroll position; the latter translates the home
surface. Swipe progress uses raw coordinates so moving the home surface cannot
feed back into gesture distance. Cancel closes the overlay, as do Home, Back,
opening the drawer and stopping the activity. A new foreground session reconnects.
Connections, callbacks and the handshake timeout are cleaned up on destruction.

The AIDL wire declarations are reused from Lawnchair with attribution and license
in `third_party/launcher-overlay`. The local lifecycle/gesture client is independent.
Reference implementation inspected at commit
`c4fe52d588fa39fb3306c8872a6c6aabc919a2d3`:

- https://github.com/LawnchairLauncher/lawnchair/blob/c4fe52d588fa39fb3306c8872a6c6aabc919a2d3/lawnchair/src/com/google/android/libraries/launcherclient/LauncherClient.java
- https://github.com/LawnchairLauncher/lawnchair/blob/c4fe52d588fa39fb3306c8872a6c6aabc919a2d3/lawnchair/src/app/lawnchair/FeedBridge.kt

Lawnchair can bind directly for debug/system builds; other builds can use an
approved companion provider (Lawnfeed, Pixel Bridge or AIDLBridge). This launcher
currently implements the direct Google connection used by our debug APK. Companion
binding is not implemented. A release APK may require it, depending on Google's
caller checks. This is a private protocol and Google app updates may change it.

If the app is missing, rejects binding or does not complete its handshake, the
minus-one fallback provides a status, Retry connection and Open Google app.
No overlay permission, account credentials, root or user-installed companion is
needed for the tested emulator debug configuration.

## Validation

Android 15 Google APIs emulator, Google app `15.22.33.28.x86_64`, overlay API 11:
connection accepted and status 3 (ready) received. A real right swipe opened Google's
Discover panel and a real left swipe dismissed it. Google displayed its own
“Couldn't connect to Discover” content error on this emulator; account/network/feed
availability remains Google's responsibility and article loading was not verified.
The launcher did not change accounts or install/update the Google app.

The `discover` instrumentation suite exercises the actual Google service handshake,
programmatic open/close, workspace swipe and cancellation, desktop page preservation,
and lifecycle disconnect/reconnect. Run on an emulator/device with the Google app:

```
adb shell am instrument -w -e suite discover dev.sense.launcher.test/dev.sense.launcher.WeatherSceneInstrumentation
```
