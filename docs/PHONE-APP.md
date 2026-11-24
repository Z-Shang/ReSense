# Sense Phone reconstruction

Build from the repository root:

```sh
source tools/android-env.sh
./gradlew :phone:assembleDebug
adb install -r phone/build/outputs/apk/debug/phone-debug.apk
adb shell am start -n dev.sense.phone/.PhoneActivity
```

`tools/prepare_phone_app.py` recovers 238 hdpi PNGs (98 dialer and 140 phone images)
from `HtcDialer.apk` and `Phone.apk` inside `res/3.33.401.153.zip`. Common tabs,
inputs, frames, call-direction icons and People portrait/search artwork now come
from [`sense-framework`](SENSE-FRAMEWORK.md). App-specific generated resources
remain under `phone/build/generated/htcPhone/res`. `raw/phone_provenance.json`
records each source APK, resource path and SHA-256 digest. Compiled nine-patch chunks are
preserved. The shared library recovers the People default portrait and search
icon from `HtcContacts.apk`. Digit and alphabet labels are native scalable text, as in the
original `htc_phone_keypad` layout. Portrait keypad sizing is adapted in dp;
this is a reconstruction rather than running the obsolete HTC framework.

The app supports number entry, long-press zero for +, backspace/long-press
clear, add-contact, permission-gated asynchronous contact search, and a
read-only system Call History list. History comes from `CallLog.Calls`, newest
first, with incoming/outgoing/missed/declined/blocked call types, duration,
local timestamps and day headers. The HTC **overflow → View** single-choice dialog offers All calls, Incoming calls,
Outgoing calls and Missed calls, preserving the choice across tabs. 100-row paging
keep the list usable for large logs. HTC artwork styles its tabs, direction
icons, dividers and pressed rows. Missed callers appear in red.

Provider queries and optional contact-name lookups run on a worker. A content
observer refreshes changes while visible; returning from the system phone app
also reloads history. Private/unknown numbers are redacted and cannot be
redialed. Selecting an available number fills the keypad for confirmation.
The old local dialed-number preference is no longer read or written, and no
app action inserts, clears or modifies system call records.

Call History requests `READ_CALL_LOG` only after tapping Allow. Denied access
shows a permission explanation; permanent denial offers app permissions.
Android marks this permission hard-restricted, so the installer must permit
it in addition to the user granting it. Builds installed with an installer
that restricts it may not have a Call logs toggle available.
[Android permission reference](https://developer.android.com/reference/android/Manifest.permission#READ_CALL_LOG).

The Call button uses Telecom when Sense Phone has the user-approved default Phone
role and Phone permission. Otherwise it opens the current system dialer explicitly.
Sense Launcher’s Phone dock button opens this companion when installed.

Incoming and ongoing call UI now uses the recovered Phone.apk assets and the
modern InCallService contract. See [Default Phone setup and implementation](DEFAULT-PHONE.md)
for role selection, controls, notifications, permissions and local test coverage.

The screenshot-based action row places keyboard and voice buttons around Call.
Add contact and default Phone setup are in the Phone options menu. Phone, People,
Groups and Call History use original textured icon tabs and the glossy selected
background. Both action rows stay above the system navigation inset.

Validation: `:phone:assembleDebug :phone:assembleDebugAndroidTest :phone:lintDebug`.
The instrumentation runner temporarily grants read access and inserts identifiable
emulator call-log fixtures using shell identity, deletes only those fixture IDs through the collection URI
in `finally`, and reports the original permission state. If
`callLogPermissionOriginallyGranted=false`, restore it **after** instrumentation
returns using `adb shell pm revoke dev.sense.phone android.permission.READ_CALL_LOG`
(revoking inside the runner kills its process). Tests cover permission UI, real
provider reads, privacy, duration, filters, observer updates, pagination and
intercepted dialer handoff. No test places a call.

## HTC contacts

The Contacts tab uses the original People app search icon and default portrait,
HTC input-field and photo-frame nine-patches, list dividers and pressed-row artwork.
Native rows show bold names, phone-type labels and numbers beneath alphabetical
section headers. A contact with multiple numbers has a row for each number.
Contact thumbnails load off the UI thread into a bounded 4 MB cache; missing or
inaccessible photos use the original HTC silhouette.

Phone-number queries run on a worker. Search matches names (including unaccented
queries for accented names) and numbers with or without spaces/dashes. Empty and
permission-denied states remain explicit. A contacts observer reloads changes
while the tab is visible. Selecting a number fills the keypad and dismisses the
search keyboard. No contacts are modified by the app.

`adb shell am instrument -w -e suite contacts dev.sense.phone.test/dev.sense.phone.PhoneInstrumentation` validates real
provider reads, type labels, sections, search, empty results, live updates and
keypad selection using temporary local contacts, deleted in `finally`. Restore
`READ_CONTACTS` after the runner if `contactsPermissionOriginallyGranted=false`;
revocation must happen outside instrumentation because Android kills the process.

The keypad uses the recovered `htc_pin_asset_rest`/`pressed` selector artwork,
matching the silver rounded keys in `res/dialer.png`; see the default Phone guide.

Screen transitions and shared HTC header controls are documented in
[PHONE-ANIMATIONS.md](PHONE-ANIMATIONS.md).

The history screen uses a Sense header and the shared HTC overflow control.
Its View dialog uses shared HTC radio artwork. Custom All calls/Missed tabs have
been removed. Stock evidence is `BrowseCallHistoryActivity.onOptionsItemSelected`:
menu ID 4 opens `HtcAlertDialog.Builder.setSingleChoiceItems` with
`R.array.call_history_type` (`0x7f060005`), containing the four text choices.
Direction icons remain on call-log rows. This adaptation keeps the modern
permission flow and provider pagination.

The Groups tab follows the stock badge and `name (count)` browse layout;
see [Groups layout evidence](GROUPS-LAYOUT.md).

The People tab now follows the HTC browse header, Add action, inset search
and framed list styling; see [People layout evidence](PEOPLE-LAYOUT.md).
