# Default Sense Phone

Open **Sense Phone → ⋮ (Phone options) → Make default Phone**, choose **Sense Phone** in Android's role
picker, then **Set as default**. Android asks for any needed Phone/notification
permissions. If notifications or full-screen incoming-call access is disabled,
the setup strip offers the corresponding permission/settings action. The app
requests the role only from this explicit action; installing it does not change
the current default. It can also be selected in Android Settings → Default apps →
Phone app.

The app handles ACTION_DIAL with or without a tel URI and registers an exported
InCallService protected by BIND_INCALL_SERVICE, with IN_CALL_SERVICE_UI metadata.
The inherited binding is retained. This implements
[Android's default Phone contract](https://developer.android.com/develop/connectivity/telecom/dialer-app).

When the role is held, tapping Call uses TelecomManager.placeCall after checking
CALL_PHONE. If the role is not held, it opens the currently selected system dialer
explicitly, excluding itself to avoid an intent loop. No ACTION_DIAL/VIEW intent
automatically places a call. Emergency dialing goes through Telecom so Android
retains its preloaded emergency-call handling.

## Calls

SenseInCallService tracks each Telecom Call with a distinct token, registers state,
detail and conference callbacks, and cleans up on removal. InCallActivity shows
incoming/ongoing calls with recovered Phone.apk portrait, answer and end surfaces.
It offers answer, decline, end, mute, audio-device selection, hold/resume, DTMF,
multiple-call selection and merge when supported by the connection. Telecom
phone-account suggestions provide a SIM/account chooser when required.

A phoneCall foreground notification is kept while live calls exist. Its CallStyle
switches between incoming and ongoing; the service stops foreground mode after
the final call disconnects. Incoming calls have a full-screen intent, with Android
controlling whether it appears full-screen or as a heads-up notification. Ringtone
playback remains with Telecom; IN_CALL_SERVICE_RINGING is deliberately not claimed.
Audio routing uses API 34+ CallEndpoint callbacks and requestCallEndpointChange.
Telecom owns the microphone; the app does not record call audio.

Immutable notification actions target a specific token. Stale answer/end actions
do not affect subsequent calls. Private/unknown caller presentation is respected,
and contact-name lookup is optional and asynchronous. End/answer controls remain
outside the scrolling content so short/landscape windows retain those actions.

Two local lint annotations document AGP 8.9's inability to infer the ROLE_DIALER
alternative to MANAGE_OWN_CALLS and to associate startForeground with an indirect
InCallService subclass. The manifest declares FOREGROUND_SERVICE_PHONE_CALL and
phoneCall; the selected role is its runtime prerequisite, as described in
[Android's service-type documentation](https://developer.android.com/develop/background-work/services/fgs/service-types).
The app does not request MANAGE_OWN_CALLS or create a production ConnectionService.

Audio calling is implemented; carrier/SIM behavior, emergency calls, Bluetooth
hardware and physical-device calls remain untested. Video calling UI and visual
voicemail are not implemented.

## Original keypad texture

`res/dialer.png` is the visual reference. Stock HtcDialer resources show
`image_style_phone_button` referencing `phone_keypad_btn.xml`, whose resting and
pressed states reference `htc_pin_asset_rest.9.png` / `htc_pin_asset_pressed.9.png`.
These are the actual silver keys; `phone_btn_bg.9.png` was a different gray panel.
The native keypad now uses the original pin assets, separated rounded keys,
black backing, gray alphabet labels and original backspace-button artwork.
Recovered text metrics are 46 px numbers / 22 px letters in the original hdpi
layout (approximately 31 sp / 14 sp); modern labels remain native text. Existing
Contacts/Call History navigation and the add-contact action are retained.

## Local verification

```sh
source tools/android-env.sh
./gradlew :phone:assembleDebug :phone:assembleDebugAndroidTest :phone:assembleRelease :phone:lintDebug
```

The `default` instrumentation suite needs Sense Phone temporarily assigned the
real ROLE_DIALER role. Back up the current role and runtime permissions first.
It registers a debug-only local PhoneAccount, enables it with the emulator shell,
creates incoming calls through that account and explicitly supplies the same
account for outgoing Telecom calls. It never uses a cellular account. It checks
role assignment, DIAL prefill, service binding, answer/reject/end, mute, speaker,
hold/resume, DTMF and stale-action isolation.

```sh
adb shell am instrument -w -e suite default dev.sense.phone.test/dev.sense.phone.PhoneInstrumentation
```

LocalTestConnectionService exists only under src/debug and is absent from release
APKs. The runner disconnects its calls and unregisters its account in finally.
After testing, restore the prior role/permissions and remove any call-log rows
whose account component/id match this debug provider. Production components
never insert/delete call history. Existing history/contact suites run afterward
under the prior default dialer, intercepting all call-button handoffs.

## Reference layout

The dialer follows `res/dialer.png`: a black empty area above a compact two-line
number strip, separate silver keys, inline 0+, keyboard/Call/voice actions, and
Phone/People/Groups/Call History icon tabs. Default-app setup and Add contact are
in the small Phone options menu, also available by holding the Phone tab.
The generic title and setup banner no longer consume the top of the dial pad.

`SenseCarouselTabs` uses the original repeating `common_app_bkg_src` texture,
`common_subnav_selector_tab` highlight, dividers and tab icons. The tab strip
remains above system navigation insets. The Call row is pinned above the tabs;
the keys and number strip scroll in short windows instead of clipping Call.

The keyboard button hides/shows the keys. Voice input delegates to Android's
recognition activity and prefills a number for review; it never automatically
calls. The Groups tab reads real system groups and filters the existing phone-
number picker to a selected group's members. Tests use temporary contacts/groups
and an intercepted speech-recognition result, then remove their fixture data.
