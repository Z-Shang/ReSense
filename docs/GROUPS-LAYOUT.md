# HTC Groups layout in Sense Phone

Stock code inspected from the ROM's HtcContacts.apk:
`BrowseGroupsActivity.GroupListItemAdapter.bindView`, `initListItemCache`,
`generateHtcListItemBuilder`, `BrowseGroupsActivity.setTitleBar`, and the group
DetailHeaderBar setup. Static analysis copies are under
`references/3.33.401.153/phone-app/carousel-audit/HtcContacts/`; related XML dumps
are under `phone-app/groups-audit/`. The compiled `display_group.xml` is a
selection-dialog layout and is not used as evidence for the normal browse row.

The normal adapter builds an HTC list item with a QuickContactBadge and text
container, explicitly clears secondary text, and appends ` (count)` to the group
name in primary text. Without a custom bitmap it requests the default group
image from HTC's group contract. The title uses a BrowseHeaderBar, and group
details use the selected group's name and a group image.

The modern Phone Groups tab now recreates those browse rows: original
`common_icon_default_group.png` and photo frame from com.htc.resources.apk,
single-line `Group name (count)`, ellipsis, pale list background, HTC dividers
and pressed-state artwork, plus a Sense header. Its member picker shows the
selected group name, HTC badge and Back control, retaining the screen fade.
Row heights/text sizes are modern approximations of framework HtcListItem;
legacy widget classes are not loaded.

Membership counts come from unique aggregate contact IDs in the system group;
the dialer's member picker continues to show members with phone numbers. Group
names survive Activity recreation. Groups/membership changes are observed,
debounced and loaded off the UI thread without rebuilding the title header or
resetting the visible scroll position. Empty and permission states remain usable.
This layout change does not add group creation, editing or HTC smart groups.

The contacts instrumentation suite uses temporary local contacts/groups to check
membership, `name (count)` rows including an empty group, actual group titles,
Back navigation, and keypad selection. It removes its fixtures and never calls.
