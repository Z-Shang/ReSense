# Drawer, cache and desktop input

The process-wide `AppCatalog` caches launchable components, locale-sorted labels,
icon constant states and alphabet sections. Scans run on a worker; package changes,
external-app availability and locale changes invalidate it. Generation checks reject
stale scans. Drawer opens and tab/layout switches reuse the catalog. Frequent filtering and
sorting use a launch-history snapshot on a separate drawer worker. Activity listeners are removed on
destruction; the cache intentionally lives for the application process.

Completed drawer contents are cached per All/Frequent and grid/list combination
for the activity lifetime. Reopening reparents those existing views, retaining
icons and alphabet index; there is no loading placeholder, sort
or icon-binding pass on a cache hit. A catalog refresh clears these view caches.
Launching an app invalidates Frequent through a usage revision while preserving
All apps. A process restart performs one new catalog scan.

The selected grid/list layout is prepared for both All and Frequent while the
home screen's main queue is idle. Frequent filtering and sorting remain on the
worker. An already loaded process catalog can therefore populate a newly created
activity before the first drawer opening. Generation, catalog identity and usage
checks discard stale warming requests. Activity destruction cancels pending work.

`DrawerAppViewport` retains only a screenful plus spare rows of native icon views,
recycling them as the user scrolls. The full content height preserves normal
ScrollView fling/pull behavior; alphabet targets use model positions, including
apps whose views do not exist yet. Initial preparation is bounded by screen size,
not installed app count. Icons retain their native click, long-press and accessible
labels; each recycled view receives the new app's listeners and drawable.

On a genuine cache miss (for example, opening immediately after process startup),
the header, weighted placeholder and tabs appear first. A worker prepares the
model, and initial visible views are bound after the opening animation settles.
An already visible drawer keeps its old content during refresh preparation, then
swaps to the new viewport. Package and locale broadcasts still trigger coalesced
background scans; update removals with EXTRA_REPLACING retain the last good catalog.
The process catalog is memory-only, so process restart still requires a scan.
There is no claim that a cold launch before that scan finishes is instantaneous.

The drawer reserves a transparent status-bar area above its normal 56 dp header.
Its panel background starts below that inset so wallpaper remains visible behind the status icons.
The drawer has header, weighted content and bottom tabs, including during the
first scan. A fixed 28 dp alphabet slot is reserved in the loading placeholder
and in the prepared viewport; populating its letters never changes the
grid width. Frequent and empty states keep the same reserved space.
The All apps alphabet rail uses actual section labels and jumps to
the first matching row in grid or list view. It provides accessibility scroll
forward/backward actions. Frequent remains ordered by usage and has no alphabet
rail. Opening rises from the bottom; closing slides down. Pull dismissal, the Back
button and committed Back gestures share one close route, restoring the already
attached home surface without rebuilding widgets before the animation. Predictive
Back progress moves the drawer down; cancellation restores it to its open pose. A downward pull when
content is at its top moves the whole panel with the finger and either closes or
returns on release/cancel. The home surface remains behind the drawer.

ROM audit: `Launcher` invokes `SlidingDrawer.animateOpen` and `animateClose`;
`com.htc.launcher.widget.SlidingDrawer` implements the motion in Java. Its
constructor scales major velocity (200), acceleration (2000) and velocity units
(1000) with display density. This is distinct from the carousel Mode10 scenes.
The modern drawer follows that bottom-slide behavior but uses modern timed easing
(334 ms open/close using the recovered dock curves), not a claim of exact original physics.

Horizontal desktop swipes sample progress from finger distance. The departing
page is a recorded Picture command stream rather than a new full-screen ARGB
bitmap. A committed swipe keeps the incoming views; rollback restores the original
views instead of constructing the widgets again. Settling uses a monotone cubic
with the measured release velocity, bounded duration and zero terminal velocity.
A new touch can grab a settling transition without snapping to its destination.
Static supersampled dock surfaces are cached separately from the moving indicator;
layout or child state changes invalidate them, while page position alone does not. Releasing
settles to a page; cancelling or insufficient travel rolls back. The tentative
page is not persisted until settling. Discover retains its Google-owned gesture
path. Vertical desktop gestures are suppressed when starting on widgets so their
scroll/touch handling and launcher long-press drag remain available.

Home settings → Desktop gestures configures Swipe up and Swipe down independently:
None, Open app drawer, or Open notifications. Defaults are drawer up and notifications
down. Notifications use an optional AccessibilityService global action. Android
requires the user to enable Sense desktop gestures; the launcher opens a setup
explanation if it is not enabled. The service requests no window-content access
and does not process accessibility events or notification text.

Rendering uses display-vsync-driven ValueAnimator/ViewPropertyAnimator and direct
touch updates, without a fixed 30/60 FPS animation timer. Window preferredRefreshRate
requests the highest supported refresh at the current resolution. Android's display
policy, battery saver and thermal limits still apply; 120 Hz hardware performance
is not established by emulator tests. Stock weather/Mode10 sample FPS remains
independent of the display refresh rate.

Instrumentation `-e suite drawer` checks tentative page state, cancellation and
commit, cache reuse, presence of the alphabet rail, bottom anchoring, and interactive
drawer pull/cancel. Existing widget and remove suites check drag/resize regressions.

The `catalog` instrumentation suite uses a temporary no-code fixture APK pushed to
`/data/local/tmp/sense-catalog-fixture.apk`; it verifies actual install, disable,
enable and uninstall events refresh the list, then removes its fixture.

## Bottom navigation area

Home and drawer use the larger bottom inset reported by system bars/display
cutouts and mandatory system gestures. The drawer adds 4 dp of breathing room
above that safe area for the pinned tabs. The dock's flat control band is now
60 dp, with 2 dp bottom padding; controls remain above navigation while a
full-width gradient continues into a dark translucent background behind system navigation. Insets are reapplied
when Android navigation mode or window geometry changes. The temporary dock
foreground layer wraps its measured content so it can respond to those changes.

The activity explicitly calls `Window.setDecorFitsSystemWindows(false)` and uses
transparent system bars with navigation contrast enforcement disabled. Android's
`WindowInsets` still reserve space for controls, but the dock background itself
reaches the physical window bottom. The `navigation` instrumentation suite checks
that screen coordinate and samples a screenshot pixel beneath system navigation.

## Drawer scroll reset

Cached ScrollViews reset to the top after the drawer finishes closing, including
all cached tab/layout combinations. Fresh opening resets again before attaching
cached contents, covering an interrupted close/reopen without a visible jump.
Stopping the activity with a drawer open also resets it for the next visit.
Cancelling predictive Back keeps the current scroll. Reset stops an existing
fling at the new top position, while preserving cached app/icon views.

Launcher preferences now live in the full-screen Sense settings activity. Drawer
layout, swipe actions, search and weather options are inline there; widget/clock
shortcuts deep-link to their matching settings sections. See LAUNCHER-SETTINGS.md.

## Alphabet rail hold and preview

Hold the rail for the configured delay (300 ms by default), then slide to scrub sections.
Quick taps do not jump. Motion beyond touch slop before activation cancels the
hold, leaving normal edge Back and drawer pull handling available. Only an active
scrub disallows parent interception and requests a 96 dp gesture exclusion region
around the current finger position; no permanent edge exclusion is installed.

A root overlay displays the selected letter in an 88 dp rounded panel centered
on the window. It does not take touch focus or alter drawer layout. Release,
cancellation, another pointer or rail detachment removes the overlay and gesture
exclusion. Accessibility scroll actions still select sections without holding and
show the same preview briefly.

Virtual app-cell widths are assigned during the content container's measure pass,
before FrameLayout measures the icons. Assigning them in onSizeChanged left a
short Frequent list with zero-width cells on its initial layout; switching tabs
only hid the problem by triggering another measure. Regression coverage checks
actual visible cell geometry on first open and reopen for cold grid and prewarmed
list drawers.

The recycled grid explicitly measures its content container to the full model
height (total rows × cell height), or the viewport/minimum height when larger.
ScrollView's unconstrained child measure must not derive that height from the
currently bound cells. This keeps native scrollbar range/size and fling bounds
stable while the visible row window changes. Regression tests remeasure 500-app
grid and list drawers at widely separated positions, checking content height,
native accessibility scroll range, exact scroll position and bounded recycling.

Sense Settings → App drawer → Alphabet hold delay configures 100–1000 ms in
50 ms steps. The delay is read on touch-down, so cached alphabet rails use the
new setting immediately without rebuilding the drawer. Movement cancellation
and system Back gesture behavior are unchanged.

The Frequent tab uses most-recently-used order with LRU eviction from the visible
page. Each successful launcher app launch records a monotonic last-used value in
`app_recency`; relaunching an app promotes it to the front. Old `app_launches`
counts identify previously used apps during migration, but no longer rank them:
entries without known recency stay in catalog order behind recent launches.
Both idle prewarming and cold drawer loads use the same ordering helper.

Frequent displays only complete rows that fit its measured viewport (four columns
in grid mode, one in list mode), without scrolling. The limit recalculates on
resize and keeps the newest entries, leaving least recently used entries outside
the page. Recency is retained so a larger viewport can display more history.
All apps keeps its full scrollable list and alphabet rail. Tests cover LRU
promotion, bounded grid/list capacity, resize, and first-open/reopen geometry.

The drawer header has a downward chevron to the left of its title. Its full-height
leading button exposes “Close app drawer” to accessibility and uses the same
animated `closeDrawer()` route as Back and the downward drawer gesture. The title
stays centered between equal-width leading and grid/list action slots.
