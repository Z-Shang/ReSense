# Sense browser search widget

The ROM's Rosie.apk contains a native search widget in
`res/layout/widget_search.xml`, with a 42 dp search field and 14/13/14/12 dp
outer padding. `tools/extract_search.py` copies its original search floater,
normal/pressed/focused search-field artwork and widget icon, preserving compiled
nine-patch metadata and original density qualifiers. The adaptation replaces the
stock voice-search action with a search-engine settings button.

Choose **Add widget → Sense Launcher → HTC Sense Search · 4 × 1**. Tap the field
to enter a query, then press Search or the keyboard's Search action. Tap the gear
to open the Search section of Sense settings and choose Google (default),
DuckDuckGo, Bing or Brave. This setting persists for
the launcher and updates all search-widget instances. Queries are encoded as a
single `q` parameter on the selected engine's HTTPS search URL.

BrowserSearch resolves the default browser using a neutral web origin, avoiding
search-engine app-link handlers, and restricts the search intent to that browser.
A browser-category selector is a fallback. When no default resolves, a chooser
contains only general web/browser handlers. With no browser installed, the widget
shows a short unavailable message. The launcher does not issue search requests
or fetch suggestions; the query is passed to the browser only after submission.

The native item saves with the workspace, spans up to four columns and one row,
supports horizontal resizing (minimum two columns), dragging/removal, and Leap
previews. Long-press has priority over the search field and settings control.

Validation covers original nine-patch decoding, Google default, settings updates,
Unicode and reserved-character URL encoding for all four engines, default-browser
package routing, persisted placement, intercepted IME submission, long-press and
Leap preview. Instrumentation blocks the browser launch, so test queries are not
sent to external search services. Tests restore the prior search-engine setting.

Android references:
- https://developer.android.com/guide/components/intents-common#Browser
- https://developer.android.com/reference/android/content/Intent#CATEGORY_APP_BROWSER
