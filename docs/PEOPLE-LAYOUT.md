# HTC People styling in the Phone app

The dialer's People tab was compared with HtcContacts.apk's
`BrowseContactsAllActivity2.setTitleBar`, `widget.title.BrowseHeaderBar`,
`ContactsListActivity.ContactsItemListAdapter` and `HtcListItemSearchBox`.
The normal browse header uses a HeaderBarDropDown/text area and a HeaderBarImage
Add action. The image is looked up as `common_icon_glance_add`.

HTC `SearchBoxView` in the ROM's HTCExtension.jar inflates
`com.htc:layout/search_box_layout` (`0x020900ab`). It has an inset editable field,
a right clear control and an optional progress indicator. The clear control
starts GONE. Its `close_search` selector resolves to
`common_icon_clear_input_rest/press`. The panel uses `common_panel_input_base`.
The original search height is 75 px at hdpi (about 50 dp); the modern control
uses 56 dp to retain a 48 dp clear target plus panel insets.

`SenseSearchBox` recreates the field, focus background, original clear artwork
and conditional clear visibility. The People page uses it instead of a separate
blue-person search image and persistent Android X. Group members share the
search control without adding a second header.

The browse page now has a Sense People header, stock Add icon with pressed/focus
states, original framed contact photos, regular-weight primary names, secondary
phone labels, compact rows, alphabet sections and HTC pressed-row artwork. Add
opens Android's contact editor; the app does not write contacts itself. It does
not emulate HTC account dropdown filters, smart contacts or social-network status.
The dialer remains a phone-number picker, while the standalone People app has
its own contact-detail flow. Full legacy runtime equivalence is not claimed.

Contacts queries, photos/cache, normalized name/number search, group membership
and permission handling remain native and asynchronous. Verification uses
fixture contacts/groups and intercepts Add/voice handoffs; it never saves a real
contact or places a call. Checks include hidden clear on empty search, visible
clear after typing, restored rows after clearing, and contact selection.
