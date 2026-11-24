# Sense ring lock screen

`:lockscreen` (`dev.sense.lockscreen`) recreates the classic HTC Sense 3.6 ring
screen with the local 3.33.401.153 ROM's artwork. Build with:

```sh
source tools/android-env.sh
./gradlew :lockscreen:assembleDebug
```

Open **Sense Lock Screen** in the app drawer, or **Sense settings → HTC lock
screen**. Settings can request an **HTC lock screen** Quick Settings tile.

**Allow screenshots and screen recording** is off by default. Enabling it clears
`FLAG_SECURE` on the HTC ring-screen window; disabling it restores capture
protection. The preference is applied before the screen first draws and whenever
it resumes. This controls the HTC screen only, not Android authentication screens
or other apps' capture policies. The regression suite exercises the real settings
switch in both modes and verifies the resulting window flags, then restores the
previous preference.
Pull the ring upward to unlock, or drag one of four shortcuts into it. TalkBack
and keyboard users can activate the native controls directly. Phone, Messages,
Camera and Browser are the defaults; each slot can select an installed app.
The screen always uses the current system wallpaper through a wallpaper-backed
window, including live wallpapers. **Choose system wallpaper** opens Android's wallpaper picker. The old bundled-wallpaper override and full-screen dim
gradient are no longer rendered; the stock ring/navbar texture alpha is preserved. The HTC surface hides the navigation bar using WindowInsetsController; an edge
swipe temporarily reveals it. The status bar stays visible and other app windows
retain their own navigation-bar behavior. It respects system bars,
cutouts and the gesture navigation area, and supports 12/24-hour time and locales.

## Android integration

Manual mode opens the normal app surface from settings or the tile. The optional
**Show after system unlock** toggle presents it automatically after Android's
system keyguard has been dismissed. This remains **not a system keyguard
replacement**: Android handles all credentials first.

Enable the toggle in Sense Lock Screen settings, allow **Display over other apps**
in Android's special-access screen, and allow notifications when prompted. This
special access permits the requested background activity launch; it is not used
to read other apps. A special-use foreground service has a persistent notification
with **Turn off**, registers only SCREEN_OFF and protected USER_PRESENT broadcasts,
and restores enabled operation after boot/package updates when Android permits.
It never reads accessibility events, notifications, credentials or window contents.
No device administrator, accessibility service or alarm/call fullscreen intent is
used. OEM background restrictions can still affect automatic presentation.

The feature is off by default. Turning it off stops the service and unregisters
its receiver. It presents at most once per screen-off/unlock cycle, checks that
the device is interactive and both keyguard/device locks are dismissed, and skips
an already-visible HTC screen. Unlocking through the manual HTC surface suppresses
that cycle so it cannot reopen itself in a loop. Automatically opened activities
explicitly turn off showWhenLocked and recheck device state on creation, so they
cannot precede Android's lock screen during a screen-off race.

`LockScreenActivity` uses `showWhenLocked` and `KeyguardManager.requestDismissKeyguard`.
If Android requires authentication, it displays its own credential/biometric flow.
Only a successful dismissal followed by an unlocked keyguard/device state can
launch the selected shortcut. Cancel/error resets the ring. Stale and duplicate
authentication callbacks cannot execute another request. No credentials are
collected by this app. The screen is excluded from Recents and its window uses
FLAG_SECURE. Home/Back retain normal Android behavior.

## ROM evidence and adaptations

`tools/prepare_lockscreen.py` recovers seven ETC1 color/alpha texture pairs from
`fusion.apk!/assets/Default/drawable/shared.m10`: ring, highlight, center cover,
wave, shortcut disc, clock shadow and curved navbar. Four stock app icons, HTC wallpaper 01 and
Helvetica Neue thin font are copied byte-for-byte from the ROM.

`Idlescreen_Base.apk!/assets/Port/base/Lock_ring_motion.m10`,
`Lockscreen_container.m10`, `Lockscreen_shortcut.m10` and `Lockscreen_time.m10`
provide raw geometry and timelines. The generated `ls_recovery.json` records
source hashes, material IDs, decoded geometry, markers and the 29.97fps authored
timebase. The ring is 271×270 in a 540×960 design. Shortcut x positions are
67/203/338/473, with the outside pair 12 design pixels lower.

The modern view adapts these horizontal proportions to the viewport and anchors
the idle ring center 48 design pixels below the bottom edge above the navigation inset, leaving only its upper crescent visible as in `res/lockscreen idle.webp`. The curved base and shortcut row keep their independent anchors. Dragged shortcuts rise above the ring/highlight and return to normal depth on reset. Picking up a shortcut raises the ring to expose its drop target over 334ms. Shortcut names are shown underneath the discs, and the pull hint is hidden at rest. The clock/date are positioned higher, with the real network operator shown when available; the reference carrier name is never hardcoded. Clock placement/font sizing and unlock/drop hit regions
are adaptations, not a complete replay of Fusion's camera or runtime transforms.
Return easing is native Android interpolation; durations use the stock
`drop_out_1` (34 frames ≈1134ms) and `touch_bounce` (35 frames ≈1168ms) ranges.
Native ValueAnimator/ViewPropertyAnimator use the device frame clock; the ROM's
29.97fps timebase does not cap rendering refresh rate. This first version does
not implement HTC's alternate weather/photo/social lock-screen plugins,
notification contents, call handling or music controls.

## Verification

```sh
python3 -m unittest discover -s tools -p test_lockscreen_assets.py
./gradlew :lockscreen:assembleDebug :lockscreen:assembleDebugAndroidTest :lockscreen:lintDebug
adb shell am instrument -w dev.sense.lockscreen.test/dev.sense.lockscreen.LockScreenInstrumentation
```

Asset tests check texture dimensions/alpha, original icon/wallpaper bytes, font
hash, timing and the manifest contract. Instrumentation tests exercise ring
thresholds, cancelled gestures, shortcut-to-ring dragging, accessible actions,
authentication ordering and duplicate callbacks through an injected keyguard.
They never change a device credential or launch Phone/Camera/Messages/Browser.
The startup regression also taps **Open HTC lock screen**, waits for the real
LockScreenActivity, and checks that its view is attached and visible. Window
inset controller setup happens after setContentView creates the decor view.
They export the app's own rendered preview as `files/htc-lock-screen.png`.
Secure credential/biometric dismissal still needs validation on a physical phone.

Android references:
- https://developer.android.com/reference/android/app/Activity#setShowWhenLocked(boolean)
- https://developer.android.com/reference/android/app/KeyguardManager#requestDismissKeyguard(android.app.Activity,android.app.KeyguardManager.KeyguardDismissCallback)
- https://developer.android.com/guide/components/activities/background-starts

## Dock audit (corrected against `res/lockscreen.jpg`)

The reference photo shows a continuous curved translucent base behind the ring.
The exact stock mesh **`lockscreen_navbar.png`** is present in
`Idlescreen_Base.apk!/assets/Port/base/Lock_ring_motion.m10`, under the
`Lock_Ring_motion` root. Its size is **540×94**, material ID **1123561786**.
Its texture is stored in `fusion.apk!/assets/Default/drawable/shared.m10` and
has been recovered to `references/3.33.401.153/lockscreen/lockscreen_navbar.png`.
The clock-shadow texture is retained for recovery/provenance but is not rendered: its rectangular cutoff caused a visible seam below the clock. Text shadows preserve readability without that panel edge or a full-screen dim layer.

The mesh includes animated Position samples; its initial position is
[270, 53, 0] in its parent scene's local coordinates.

The initial audit missed this mesh by focusing on the shortcut and container
scenes. The lock screen does have a dock-like base, separate from Rosie's
Apps / Phone / Settings dock. LockSurface now draws it behind the ring and shortcuts, spanning the screen width. The decoded texture is flipped vertically at rendering time so the arc rises toward the center, matching the reference photo. Its resting top follows the mesh position/pivot (53−47 = 6 design pixels below the ring-container anchor); the lower edge extends underneath modern gesture navigation.

The four independent shortcut discs are still present: 83×83
`lockscreen_shortcutpanel_b.png` quads with 72×72 dynamic icons.
`ShortcutContainer.smali` controls their visibility independently.
`Lockscreen_fake_menu.m10` is a different layer for incoming-call controls,
including Send message and Mute, as used by `IncomingCallView.smali`.

Runtime evidence is disassembled under
`references/3.33.401.153/lockscreen/Idlescreen_Base/`; the normalized analysis DEX
is separate from the untouched ROM APK.

## After-unlock verification

`python3 tools/test_lockscreen_after_unlock.py` is emulator-only. It temporarily
grants display-over-other-apps and notification access, tests the actual toggle,
foreground notification, a screen-off/wake/system-keyguard-dismiss cycle and a
second cycle with the feature disabled, then restores permission modes and the
previous feature preference. It refuses to change an existing secure credential.
Policy tests cover locked/missing-permission/duplicate/manual-suppression cases.
Secure PIN/biometric unlock and vendor background restrictions require device testing.
