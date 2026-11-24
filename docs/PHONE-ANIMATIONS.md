# Dialer controls and screen transitions

Sources inspected: `res/3.33.401.153.zip!system/app/HtcDialer.apk`,
`system/app/Phone.apk` and the ROM's framework animation resources. Compiled
manifest, style and animation dumps are under
`references/3.33.401.153/phone-app/transitions/`.

HtcDialer has no embedded `res/anim` or `res/animator` entries. Its inspected
HTCSkin styles do not supply a local window-animation style. Phone.apk contains
`touch_lock_fade_in`: alpha 0→1, 500 ms, decelerate. That animation is for the
touch-lock overlay and is not evidence for a general call-screen transition;
it is not applied to unrelated controls.

The ROM framework's activity-open enter and activity-close exit fade in/out
for 200 ms with accelerate/decelerate interpolation. Opposing window resources
have no animation children. These values are resolved in the
[Messages animation research](MESSAGES-RESEARCH.md#screen-transitions).
Legacy runtime overrides and the HTC carousel tab implementation have not been
traced, so this is a resource-based recreation, not proof of identical runtime
behavior.

Modern integration:

- PhoneActivity and InCallActivity use equivalent shared window animation XML.
- Phone's content host uses `SenseScreenHost` for changed tabs, entering a contact
  group and returning with header/system Back. The bottom tabs stay fixed.
  Same-page refreshes and keypad digit entry do not replay the fade.
- Each destination keeps its own provider view and observer lifecycle. The old
  view is detached after the fade, or immediately if a newer navigation interrupts
  it. Disabling system animators skips the content fade.
- `SenseHeaderIconButton` supplies original HTC horizontal overflow dots and
  previous-arrow artwork with pressed/focused states and 48 dp touch targets.
  Phone and Messages use the same class. Phone's popup anchors to the tapped
  control with end alignment; its existing long-press Phone fallback remains.

No fake call, permission grant, default-role change or carrier call is needed to
exercise tab/menu navigation. The contacts instrumentation suite uses temporary
provider fixtures, verifies group Back and intercepts voice input. It never
presses Call. Call routing logic is unchanged.

## Bottom carousel audit (confirmation only)

Confirmed by static inspection of the ROM's `system/framework/HTCExtension.jar`
and `system/app/HtcContacts.apk`. Analysis files are under
`references/3.33.401.153/phone-app/carousel-audit/`. The cached normalized DEX
used for HTC classes was compared byte-for-byte against the ROM HTCExtension
DEX with the same header-only 036→035 compatibility normalization. No original
APK/JAR was modified or executed.

`BrowseLayerCarouselActivity` extends `BaseCarouselActivity`, which extends HTC
`CarouselActivity`. It explicitly includes `DialerTabActivity` and the People,
Groups and Call History icon resources. Thus the tab host belongs to the Contacts
carousel framework even though the keypad itself is implemented by HtcDialer.

`CarouselWidget` inherits HTC `Gallery` and uses `FastScroller` for the tab strip.
`FastScroller.onTouchEvent` handles ACTION_UP (`pswitch_172`): it computes a tab
position, an alignment and an X travel distance. It invokes
`FastScroller.FlingRunnable.startUsingDistance(IIIZ)` in both the fitting-strip
and overflowing-strip cases. Its `mAnimationDuration` is initialized to `0x190`
(400 ms). `startUsingDistance` calls native `Scroller.startScroll` with that
duration; frame updates move `mThumbLeft`. In the overflowing case the gallery
position is adjusted too. `endFling` settles the thumb at the final X position
and completes the selected-position update.

This confirms horizontal motion of the selected tab thumb/highlight, plus strip
scrolling where there are more tabs than fit. It does not establish that the
entire bottom bar translates in/out on ordinary tab selection. Drag/reorder
animations also exist and are separate paths.

The current modern carousel still changes selected backgrounds immediately;
the screen fades implemented earlier do not reproduce this tab-thumb motion.
No application implementation changes were made during this confirmation audit.
