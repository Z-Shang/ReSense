# Stock HTC widget picker audit

Research only. No launcher/widget-picker implementation changes were made.
Sources are the user's `res/3.33.401.153.zip`, specifically Rosie.apk and
HomePersonalize.apk, with the corresponding HTC framework widgets as context.
Extracted originals, normalized analysis DEX, smali and XML/resource dumps are
under `references/3.33.401.153/widget-picker/`. DEX compatibility copies change
036 to 035 and recompute header hashes only; stock APKs remain unmodified and
were not installed/executed.

## Entry and catalogue

Rosie's `Launcher.onClickAddtoHome` records workspace vacancy information,
prepares its AddListAdapter/AddWidgetLayout and launches the
`com.htc.personalize.carousel` action. HomePersonalize implements a Personalize
carousel and an Add-to-home list through `PersonalizeMain`, `AddWidgetLayout`
and `AddAdapter`. `PersonalizeMain.setupSearchBar` attaches a search listener to
HeaderBarInput. Its adapter gathers widget/provider metadata and sorts lists.
This is a separate screen flow, unlike our modal, app-grouped expandable list.

Rosie also retains `WidgetItem.startWidgetPicker`, which supplies `VACANCY_INFO`
and `item_type` to the older `com.htc.AddProgramWidget` component. That package
is absent from this ROM. This is evidence for a retained older route, not proof
that the active HomePersonalize chooser universally filters every item by free
space. Actual fit/placement behavior should be traced separately before copying
that rule into the modern picker.

## HTC/Fusion styles (Sense 3D widgets)

`PersonalizeMain.AddToHomeItemClickListener.pickFxWidget` opens
`com.htc.home.personalize.fusionwidget.StyleChooser`, passing
`com.htc.launcher.intent.EXTRA_KEY_WIDGET_PROVIDER_ARRAY`. The all-Fusion-widget
route opens the same activity. A loader collects styles; a PreviewManager caches
preview Drawables using weak references. The selected preview is displayed in
an ImageView through a style adapter; it is not our hosted live-widget preview.

The style chooser uses HTC HtcFilmstripView, a horizontal swipeable preview
browser. Its title updates to the localized style/widget name; a separate
counter is assembled as `position + 1` and ` / ` plus the style count. Controls
and selection follow the selected filmstrip item. The right footer reads
**Select**, resolved from `variation_option_select` (`0x7f090043`). The left
footer reads **Details** (`0x7f090002`) and can be hidden for an index-filtered
chooser. Details displays widget/package information; it is not the Cancel
button. Android Back handles leaving the activity.

Style selection identifies provider name, style name and style ID. Some styles
invoke provider settings (`REQUEST_SETTINGS=200`) before returning to the
launcher. Return/launch-home data includes item type and selection information.
Do not replace this flow with a fake immediate placement before Android's
binding/configuration succeeds.

## Older HTC widgets and other paths

`PersonalizeMain.AddToHomeItemClickListener.pickWidget` launches the separate
`com.htc.home.personalize.widgetpicker.WidgetPicker` with `item_type` and request
code 500. It similarly uses a filmstrip (`WidgetAdapter` obtains
`MyWidgetInfo.getPreviewImage()`), localized title, position/count, **Details**
and **Select**. Its `choose` method invokes the plugin's `onSelectedAt` hook when
needed, or returns the selected item ID and `waitForActivityResult=false`.

Android AppWidgets are also represented in the add catalogue but follow a
binding/configuration route. HomePersonalize's `startAppWidget` calls the old
privileged bind API and starts configuration where applicable; modern code must
keep the existing user-approved AppWidget bind/configure flow instead of loading
the legacy app or using privileged permissions.

## Recovered layout evidence

- `HomePersonalize:widget_picker.xml`: full-screen vertical root; header at top;
  weighted HtcFilmstripView filling the middle; fixed footer at bottom.
- `widgetpicker_header.xml`: centered title plus a second centered row with
  left/right indicator artwork and position/count text.
- `widget_detail.xml`: detail-information layout; not the primary browse screen.
- `WidgetAdapter.getView`: an image inside a filmstrip-sized RelativeLayout,
  preserving preview image bounds through adjustViewBounds.
- `StyleChooser` has its own selection/settings flow; it must not be conflated
  with the older WidgetPicker merely because both use HtcFilmstripView.

Resource dimensions vary by configuration. One default gallery entry is
714×892 px; these are not universal dp values for a 540×960 modern viewport.
Do not carry those raw values into the rework. Exact carousel easing/velocity,
wallpaper treatment and every device/configuration-specific measurement have
not yet been established by this audit.

## Modern implementation implications

Our `launcher/WidgetPicker.java` is currently an AlertDialog with collapsible
provider-app groups and embedded preview cards. It has no separate style-browser
screen or explicit Select step. The closer HTC structure would be:

1. A dedicated, searchable Sense widget catalogue rather than an expandable
   modal preview list. Modern app/provider grouping can remain a deliberate
   adaptation; HTC style families are not simply provider application names.
2. A horizontal style/size preview screen for a selected family, with name,
   `1 / N`, HTC navigation artwork, Details and Select.
3. Existing native/recreated Sense widgets and Android providers represented by
   a common selection model, while retaining their different configuration and
   placement paths.
4. Adaptive preview scaling for the current grid and viewport, accessible
   previous/next navigation, system insets and frame scheduling.
5. Selection hands off to existing binding/configuration and placement checks;
   cancellation releases allocated widget IDs and returns to the correct screen.

This is a design recommendation from the recovered flow, not implementation
work or a claim that HTC had modern AppWidget previewLayout/RemoteViews APIs.

## Modern implementation

The launcher now opens `WidgetPickerActivity` instead of the collapsible dialog.
Its catalogue groups widget families under collapsible provider-app sections,
keyed by package and Android profile. Provider and widget names are sorted within
their respective levels; identically named apps from different packages/profiles
remain separate. Sense Music, Search and Weather share the Sense Launcher section.
Search matches both provider and widget names and expands filtered results
without discarding the normal catalogue's collapsed state. Collapsed sections
survive Back navigation and activity recreation. Selecting a widget still opens
the existing horizontal style/size preview, without allocating widget IDs.
Its searchable catalogue opens a horizontal, snapping style filmstrip with
original HTC previous/next artwork, a position counter, Details and Select.
Music's 4×1, 4×2 and 4×4 variants form one family; Android providers are grouped
by application/profile and exact widget label, avoiding guesses about unrelated
provider names. Search matches both widget and application names.

Metadata and provider preview images load asynchronously. Music uses the recovered
ROM preview artwork; Search uses the recreated Sense field behind a touch-blocking
preview container. Provider previews use their supplied image, falling back to the
application's widget icon. No live widget is bound merely to show a preview.
Preview images use native FIT_CENTER scaling within the available filmstrip viewport; grid dimensions are calculated from current workspace cells.

Select returns a provider/profile or native style to MainActivity, which retains
Android's binding/configuration consent and existing placement/resize flow.
The catalogue query, scroll position, family and selected style survive activity
recreation. Back from styles returns to the catalogue; Back from the catalogue
cancels. Insets protect the status bar and navigation area. Screen changes use
the shared recovered 200 ms fade; native scrolling follows Android frame timing.

This recreates the HTC interaction with current Android views; it does not run
HTC's proprietary FilmstripView or its plugin framework.

Validation: `:launcher:assembleDebug`, `:launcher:assembleDebugAndroidTest`,
`:launcher:lintDebug`, shared framework asset tests, and the emulator's
`widget_picker` instrumentation suite (family sizes, navigation, Back and no
widget-ID allocation while browsing).

### Stock layout reconstruction

The style screen now follows `widget_picker.xml` / `widgetpicker_header.xml`:
centered widget name, a compact central arrow/counter group, weighted filmstrip
and a fixed 60dp footer (the ROM's hdpi footer is 90px). The additional modern
“Choose widget” toolbar and dimensions appended to the counter were removed.
Grid dimensions remain in Details and the counter's accessibility description.
The header uses white primary and #c4c4c4 secondary text, matching the referenced
framework color selectors, over a black chooser background. Footer tiling uses
`common_app_bkg_src`, matching the background selected by StyleChooser.

The actual `home_widget_picker_arrow_left/right.png` images are extracted from
HomePersonalize.apk by the shared asset generator with byte hashes in provenance.
They retain their small artwork while receiving 48dp interactive targets. The
catalogue follows Personalize's icon / two-line text / trailing image row structure
on its declared black list background. Modern provider labels and style counts
remain because the old proprietary widget catalogue cannot enumerate modern apps.

Weather’s 2×1, 2×2 and 4×4 variants form one family and use original ROM previews.
