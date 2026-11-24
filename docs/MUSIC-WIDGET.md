# Sense music widget

The supplied `3.33.401.153.zip` contains `system/app/com.htc.MusicWidget.apk`,
including `assets/Port/Music_4x1.m10`, `Music_4x2.m10`, and `Music_4x4.m10`.
The native widgets adapt all three original layouts: 4×1 (530×142), 4×2
(530×344), and 4×4 (530×748). This does not run the old HTC music service or
Fusion engine. Original animated album transitions are not implemented yet.

`tools/extract_music.py` recovers fifteen Mode10 textures, the no-cover artwork,
all three original picker previews, and source geometry/provenance. Artwork is
built from the user's ROM, not checked into source. SenseMusicView uses the
original transport strip/glyphs, album frame, reflection and panel texture with
modern text, seek interaction and Android accessibility descriptions. It scales
uniformly to the available workspace cell bounds. Resize minimum is three
columns and the selected style’s nominal row count. Long-press takes priority over controls for launcher dragging.

Choose **Add widget → Sense Launcher**, then select **HTC Sense Music · 4 × 1**,
**4 × 2**, or **4 × 4**. Tap **Enable media access** to
open Sense settings → Integrations → Media control access, then explicitly grant Android notification-listener access to
**Sense music controls**. This is required by MediaSessionManager for access to
other apps' active sessions. The service does not process or store notification
contents; its default notification type filter is empty.

MediaRepository maintains one active-session subscription while music views are
attached. It observes metadata/playback/session destruction callbacks on the
main thread, prefers playing sessions over paused ones, and respects each
session's advertised transport actions. Play/pause, next/previous and seeking
use MediaController.TransportControls. Unsupported controls are disabled. Album
art uses metadata bitmaps, with bounded background decoding for readable content
or android.resource URIs; remote artwork URLs are not downloaded. No local music
library permission or HTC service is required. The music app must publish an
Android media session.

Progress extrapolates PlaybackState's position/speed/update timestamp and refreshes
once a second while playing/visible. Revocation clears session subscriptions and
returns the widget to setup. With no session, it shows an empty playback state.
The widget persists as a launcher item and participates in resizing, dragging,
removal and Leap previews without allocating an Android app-widget ID.

Validation includes real platform MediaSessions and transport callbacks,
metadata/artwork changes, playing/paused selection, session destruction,
unsupported controls, seek, long-press priority, persisted placement, Leap
preview and permission revocation. Emulator tests temporarily grant listener
access and restore its original setting. Real third-party players and Android's
user-facing grant screen still depend on their device behavior.

API reference:
https://developer.android.com/reference/android/media/session/MediaSessionManager#getActiveSessions(android.content.ComponentName)

The widget picker groups choices by the provider application's display name,
using package/profile identity to keep separate providers distinct. Group names
and widget labels are sorted. Groups initially collapse to provider headings with
widget counts; tapping a heading expands or collapses its previews. Each heading
exposes its expanded/collapsed state for accessibility. The three native music items appear in the Sense Launcher group. Selecting
it cancels any pending external widget allocation before placing a native item.
Provider artwork remains asynchronous and row-recycling guards prevent stale
previews. Album covers draw above the original opaque plate, matching the
Mode10 dynamic image's z=1; screenshot validation checks a known cover color.

## Music style persistence

The workspace stores `musicRows` (1, 2 or 4) separately from the resizable cell
span. Old music items without this field retain the 4×2 layout. New placement
uses four columns (or the available columns on a smaller grid), and one, two or
four rows. Reloading, dragging, resizing and Leap previews preserve the selected
style. All variants share the same callback-driven media repository.

The compact style has album art, title and transport controls; the medium and
tall styles also expose seeking and elapsed/remaining time. The tall style uses
its original large album frame, background, reflection and divider. Its recovered
shuffle/repeat icons are dim decorative elements; generic Android platform
transport controls do not provide interoperable shuffle/repeat actions across
players, so they do not pretend to change those modes.

Tests verify all three sizes, saved style and cell span, reload, real play/pause
callbacks, Leap previews and visible album-art pixels. Captured emulator fixtures
are `music-widget-1.png`, `music-widget-2.png` and `music-widget-4.png` in the
instrumented app's private files directory.

Tapping the album cover, frame or track text opens the selected media session's
own activity (usually its now-playing screen). The send supplies the Android 14+
foreground sender opt-in only for that explicit tap. If the session has no
activity, or its PendingIntent was canceled, the launcher opens that exact media
source package's launch activity. Sources with no launchable screen show a brief
message. The action applies to all three widget sizes and keeps the widget's
existing long-press drag interception. Tests exercise a real session activity
launch plus missing/canceled destination fallbacks.

Platform sender rules: https://developer.android.com/guide/components/activities/secure-bal
