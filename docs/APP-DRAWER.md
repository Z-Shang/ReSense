# Original HTC app drawer audit

Sources: the user's `3.33.401.153` stock ROM, Rosie's compiled Android XML,
resource table, and `classes.dex`. This describes this ROM's implementation,
not every HTC Sense release. No drawer UI changes were made during this audit.

## Confirmed structure

- `res/layout/all_apps_view.xml` is a native `com.htc.launcher.AllAppsView`.
  It contains separate `AllAppsListView` and `AllAppsGridView` controls.
- `AllAppsGridView` extends `com.htc.launcher.widget.PagedGridView`, which extends
  HTC's `BouncingGridView`. Defaults enable page scrolling and the page indicator,
  and disable the normal vertical scrollbar. Its fling handling is vertical.
- `integer/allprogram_grid_columns` is four by default, with a five-column
  landscape override. Other landscape column resources exist; effective
  landscape behavior needs its own configuration/caller check.
- `color/allapps_background_color` is opaque `#ff1e1e1e`.
- `res/layout/all_apps_tab.xml` uses an HTC `CarouselHost` and `CarouselWidget`,
  with an image layer anchored to the bottom. `AllAppsView` identifies tabs
  `tab_allapps`, `tab_frequent`, and `tab_downloaded`; corresponding default
  labels are “All apps”, “Frequent”, and “Downloaded”.
- Sort constants identify alphabetical, newest, oldest, default and no-sort
  modes. Resources include menu labels for grid/list layout, sorting, finding,
  and removing applications. Not every mode is necessarily presented together.
- `res/layout/application_boxed.xml` is a top-centered TextView, with icon/text
  padding and text-size/cell-height resources. The native list row uses an icon
  and primary/secondary text.
- `AllAppsView` implements DragSource and the grid implements item-click and
  item-long-click interfaces, consistent with launching and dragging shortcuts.

## Comparison with the modern prototype

The prototype currently has a Home button and a continuously scrolling,
four-column app grid. It lacks the original drawer's bottom filter tabs,
page snapping/indicator, grid/list switch and sort controls. Its background
uses the home gradient instead of the recovered dark drawer color. Native
reconstruction should prioritize those visible differences while preserving
our existing app launching and drawer-to-desktop drag behavior.

This drawer is chiefly Android views and HTC framework controls; it is not
primarily a Mode10 scene that can simply be played back. The original HTC
framework controls cannot be directly reused as Android 15 widgets.

## Local evidence

Decoded layouts are saved in `.toolchain/app-drawer/`:
`all_apps_view.txt`, `all_apps_tab.txt`, `rosie_application_list.txt`, and
`launcher.txt`. The resource table is in
`references/3.33.401.153/dock-layer-audit/Rosie-resources.txt`; original DEX
inspection is in that directory's `rosie-dexdump.txt`. These generated and
proprietary source artifacts remain local.


## Modern two-tab drawer

The implemented filters are All apps (alphabetical) and Frequent (descending
launch count, alphabetical ties). Frequent counts successful explicit app launches
through this launcher, including placed home shortcuts. Counts persist locally;
no system-wide usage access is requested. The first-use empty state explains
how to populate the list. Both tabs support launching and dragging shortcuts.
The drawer refreshes on resume, and uses the recovered #1e1e1e background.
Downloaded is omitted. Original vertical page snapping and list/sort controls
remain future work.


## Header and revised tab order

The modern drawer now puts Frequent on the left and All apps on the right,
while All apps remains the initial filter. It uses a reusable `SenseHeaderBar`
subclass with a centered title and no Home button. System Back closes the
panel with the downward transition; the HOME intent also returns to the desktop.

Original evidence: `allapps_handle.xml` contains a `HeaderBarShort`. Rosie's
Launcher setup creates `HeaderBarText`, adds it through `HeaderBar.addCenterView`
(DEX 0x897d0–0x897dc), and assigns it to `AllAppsView.setTitleBar` (0x897f6).
`AllAppsView.setTitleText` calls `HeaderBarText.setPrimaryText` (0x64d0c).
The XML and that title setup do not provide a Home button; our earlier Home
button was a prototype addition. A separate search header is also present in
Rosie and is not yet reproduced by the modern drawer.


## Grid/list control

The drawer header now includes a List/Grid action. Grid uses four columns;
list uses full-width `SenseAppRow` cells with left-aligned icons, labels and
subtle separators. `SenseAppRow` inherits from `SenseAppIcon` and preserves
launching and long-press dragging. The selected layout persists in the drawer
preferences and applies to both Frequent and All apps. Changing layout refreshes
content without replaying the drawer entrance. This header action is a modern
placement of the original layout choice, which Rosie exposed through menu strings.


The drawer also supports a downward swipe to close, using its existing slide-down
transition. The gesture must start while the app list is at its top and travel
at least 80 dp, predominantly vertically. Upward/horizontal gestures remain with
the content, and cancelled or multi-touch pulls do not dismiss. The behavior is
shared by grid and list layouts and both tabs.

## HTC control artwork

The header uses shared HTC down and list/grid state pairs instead of a vector
chevron and text toggle. Footer icons follow `Rosie.AllAppsView`'s original
CarouselHost.addTab arguments: `common_icon_my_apps_rest/on` for All apps and
`common_icon_starred_rest/on` for Frequent (Rosie disassembly offsets 0xd5/0xe2
and 0x1fc/0x209 in the tab construction method). Native accessible buttons keep
their labels, stable footer geometry and original content descriptions. App
icons themselves continue to come from each installed app.

## Anchored app actions

Long-pressing an app in either grid or list mode now opens a Sense-styled
popup next to its icon. It offers App info, Uninstall for removable apps, and
Add to Home screen. System apps without removable updates omit Uninstall.
App info targets the selected package's Android settings page. Uninstall sends
an `ACTION_DELETE` package URI and opens Android's confirmation; it does not
silently remove packages. The launcher declares `REQUEST_DELETE_PACKAGES`.

The popup anchors to the compound drawable rather than the full list row and
clamps to the visible window. It dismisses on Back, outside touch, drawer/tab
changes, scrolling/recycling of the anchor, pause and destruction. It initially
keeps the ongoing hold gesture nonfocusable: moving beyond touch slop hands off
to the existing drag-to-home path. Releasing the hold makes the popup focusable
for normal keyboard/back handling. Selecting Add to Home screen places a
shortcut in the first available cell using workspace persistence.

Native suite `drawer_actions` exercises both layouts, held touch and drag
handoff, correct package intents, popup dismissal, home placement and the real
system uninstall confirmation, which the test cancels. Home/drawer/recency
preferences are backed up and restored.
