# Sense Messages

Native Java / Android 15+ SMS companion (`:messages`, `dev.sense.messages`).
It packages original artwork from `Mms.apk` and uses `:sense-framework` for
shared controls. The old APK and HTC runtime are not installed or executed.
See [asset/layout research](MESSAGES-RESEARCH.md) for source evidence.

## Build and use

```sh
source tools/android-env.sh
./gradlew :messages:assembleDebug
adb install -r messages/build/outputs/apk/debug/messages-debug.apk
adb shell am start -n dev.sense.messages/.MessagesActivity
```

Tap the default-SMS banner and approve Android's role picker. Grant SMS access
and notifications when requested. Reading remains available with READ_SMS if
another app is default; sending, receiving and modifying SMS require Sense
Messages to hold the role. No role or permissions are silently assigned on a
user device. Contact names are optional through **Message options → Show
contact names**. The contact picker can select a number without full address-book
access.

Features:

- Conversation inbox from the system SMS provider; unread counts, contact names,
  search of sender/name/latest snippet, and live provider updates.
- ROM-based 200 ms screen fades for conversation open/close and window transitions;
  header Back and Android Back share the animated route.
- Original blue received / gray sent compiled nine-patch bubbles, stock app,
  compose, contact and send artwork; Sense title/buttons and system-bar/IME insets.
- Single-recipient text composition, contact picker, local drafts, SMS segment
  counter, ACTION_SENDTO and shared-text entry points.
- Carrier SMS with an explicit active subscription. If multiple SIMs are active
  and Android has no default SMS subscription, the user chooses before sending.
- Multipart messages with durable per-part sent/delivery aggregation. Failure
  stays failed if a late or duplicate success callback arrives. Delivery receipts
  depend on carrier support; “Sent” does not claim delivery.
- Protected SMS_DELIVER receiver inserts incoming multipart SMS into the system
  inbox and posts private-content notifications. Platform-protected quick reply
  supports the call screen's RESPOND_VIA_MESSAGE action.
- Long press a bubble to copy text, review a failed send, or delete the message
  with confirmation. Retry fills the composer; it never automatically resends.

SMS provider queries and contact resolution run off the UI thread. Incoming
persistence and send callbacks have a separate executor so a large inbox scan
cannot hold up broadcast handling. Stale inbox scans stop early. Message history
loads 200 messages at a time with **Load earlier messages**. Inbox thread grouping
scans SMS and MMS providers on a worker and merges them by Android thread ID and timestamp.

Sending is coordinated outside the Activity. A recreated composer observes the
in-flight submission and clears only its corresponding submitted text. A private
SQLite ledger preserves callbacks across process death and default-role changes;
provider synchronization resumes when the app starts with access again. A row
fingerprint prevents stale callbacks from changing a deleted/reused provider ID.
Owned outbox entries still unacknowledged after two minutes are marked **Send
status unknown** on startup, requiring user review; they are never automatically
retransmitted. A carrier may still have received such an SMS.

## Scope

This version supports **SMS and one-recipient carrier MMS**. The original
attachment button is below Send. Picture, Video, Audio and Contact card open
Android's document picker. Private attachment copies are saved with drafts and
can be removed before sending. A message with attachments uses MMS; a message
without attachments remains SMS. The existing SIM selection flow applies to both.

Photos are resized/recompressed to the selected carrier's configured maximum
image dimensions and message size. GIF, audio, video and contact-card payloads
must already fit; the final encoded PDU is checked against the carrier limit.
Unsupported/oversized media leave the draft intact. The app does not transcode
video or capture new camera/voice recordings.

Incoming protected WAP notifications create download placeholders in the system
MMS provider. Tap **Download MMS** to retrieve media through Android's carrier
MMS service. Downloaded parts are stored in the provider, shown in conversations,
and open through a narrow temporary URI grant. Carrier deferred/acknowledgement
PDUs follow the AOSP flow. Failed downloads can be explicitly retried with a new
callback token. Carrier media may expire before download.

MMS transfer identities and results use a private SQLite ledger. Sent/failure
callbacks are checked against the provider transaction identity; completed
callbacks are idempotent and results can replay after the default role returns.
Stale transfers become uncertain after 20 minutes, without automatic resending.
Failed/uncertain outgoing messages can restore their parts into a draft for review.
Incoming imports expose the completed message only after parts are saved.

The AOSP Apache-licensed codec is vendored under `messages/mms`; see
`messages/third_party/NOTICE-AOSP.md`. Multipart counts, lengths and nesting are
bounded and malformed parser assertions are treated as invalid PDUs.

Group MMS composition, RCS, scheduled sending, blocking and backup/export remain
outside the implemented scope. Incoming group headers are retained, but the UI
currently groups incoming MMS by sender and replies to that sender.

Sending uses the device modem/SIM and the user's carrier. Physical-device sending,
carrier delivery receipts, and OEM-specific dual-SIM behavior have not been
validated in the emulator.

## Verification

```sh
python3 -m unittest discover -s tools -p test_messages_assets.py
./gradlew :messages:assembleDebug :messages:assembleDebugAndroidTest :messages:lintDebug
python3 tools/test_messages_emulator.py
python3 tools/test_messages_emulator.py --suite mms
```

The host test only accepts an `emulator-*` serial. It temporarily assigns the
SMS role, injects one inbound SMS through the emulator modem, exercises real
provider/UI behavior, and replaces the outgoing radio transport with an
interceptor. It checks multipart sent/failure/delivery aggregation and composer
recreation during submission. It removes its fixtures and restores the prior
SMS role and permission grants in a finally block. It never sends a carrier SMS.

Android contracts used: [default SMS components and provider ownership](https://developer.android.com/reference/android/provider/Telephony),
[SMS role](https://developer.android.com/reference/android/app/role/RoleManager#ROLE_SMS),
and [SmsManager submission and callbacks](https://developer.android.com/reference/android/telephony/SmsManager).

MMS verification uses an injected send/download transport and local PDU fixtures;
no MMS carrier operation is invoked by the suite. It checks Unicode text and
SMIL/media encoding, photo dimensions, size rejection, malformed PDUs, provider
parts, sent/failure states, duplicate callbacks, incoming placeholders/downloads,
shared conversations, read state and restoring attachment drafts. The emulator's
carrier configuration disables MMS, so production correctly refuses carrier MMS
there; the codec/transport fixtures use explicit test size limits. An MMS-enabled
physical SIM is required to validate actual carrier delivery.
