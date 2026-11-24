# Unified Sense settings

The dock’s Settings button opens a quick popup: Add Widget, Manage Pages,
Choose Wallpaper, Sense Settings, and Android Settings. Sense Settings opens
LauncherSettingsActivity, a full-screen
Sense-styled scroll view with system-bar/gesture/keyboard insets and a Done
button. The activity is internal to the launcher and excluded from Recents.

Sections include:

- Desktop: widget picker, Leap page manager, inline grid columns/rows, clock
  visibility and wallpaper selection.
- App drawer: grid/list layout.
- Desktop gestures: inline swipe-up/down actions and Android accessibility access.
- Search: inline engine selection, shared by browser-search widgets.
- Weather: current city/conditions, inline city search/results, refresh,
  animation preview and attribution.
- Integrations: media-session access and Discover reconnection.
- Placed items: readable labels, removal and return-to-desktop resize actions.
- System: default home selection and Android settings.

Search-widget gears, clock/weather taps, gesture setup and legacy grid entry
points open the corresponding section of this same activity. Detailed launcher preferences remain together on this page. Desktop editing, widget selection,
animation previews and Android's own permission/role screens retain their
purpose-specific interfaces.

WorkspaceSettings persists grid reflow and clock changes atomically in one
SharedPreferences edit. Grid changes retain items, clamp spans to the new grid,
and place them without overlap, adding pages when needed. MainActivity reloads
changed workspace data on resume, preserving existing state when no layout data
changed. Item removal releases external widget IDs. Widget/page/resize shortcuts
return to MainActivity so live editing happens on the actual desktop, including
when MainActivity must be recreated.

The settings instrumentation suite backs up and restores user preferences. It
checks all sections, inline search/drawer/gesture changes, non-overlapping grid
reflow, clock visibility, native-item removal, resume/reload, dock/search/weather
entry points and the return-to-Leap action.

Weather settings also include **Full-screen weather after unlocking**, **Full-screen
weather when entering home**, and **Preview full-screen weather**. The preview
returns to home to show any of the seven recovered overlay effects. See
[Full-screen weather implementation](FULLSCREEN-WEATHER-RESEARCH.md).

App drawer settings include **Alphabet hold delay**, defaulting to 300 ms and
adjustable from 100 to 1000 ms in 50 ms steps. Changes are saved immediately and
read by the alphabet rail on the next touch-down, including cached drawer views.

## HTC preference styling

Launcher settings uses a dedicated light `SenseSettingsTheme`: white preference
rows, charcoal gradient section bars, dark text and a dark header/system-bar
surround. This theme is confined to the settings activity. Navigation actions
use full-width rows with chevrons; Apply grid, Find city, Refresh weather and
placed-item controls use original HTC silver button backgrounds. Grid counters
and the alphabet-delay picker remain native, with readable dark text.

The shared [`sense-framework`](SENSE-FRAMEWORK.md) supplies the original framework artwork: checkboxes, radio indicators, input fields, buttons, list dividers,
pressed-row treatment and popup framing. Compiled nine-patch chunks are retained;
the library’s `raw/sense_framework_provenance.json` records sources and SHA-256 digests.
`dev.sense.framework.SensePreferences` subclasses native Button, RadioButton and Switch controls.
The Switch retains checked-state/accessibility behavior while showing the stock
HTC checkbox as its trailing indicator; native thumb/track visuals are hidden.
Radio states and silver-button pressed/focus backgrounds use the original assets.
City input fields share the light treatment. HTC popup frames use a matching
dark dialog theme with light text, and animation previews keep a separate dark
control context for legibility. Existing
preference keys, callbacks, section navigation and system/keyboard insets remain
in use. The settings instrumentation suite verifies behavior and restores user
preferences; desktop and drawer section screenshots were reviewed on the emulator.
