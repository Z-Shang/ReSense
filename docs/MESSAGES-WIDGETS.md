# Messages widgets

Sense Messages supplies three resizable Android home widgets, grouped as
**Messages** under **Sense Messages** in the launcher's widget picker:

- **Cards**: a native swipeable stack with the original HTC message-card artwork.
- **List**: compact conversation rows with sender, snippet, time and unread styling.
- **Summary**: expanded conversation previews.

All three default to 4 × 4 cells, as declared by the stock
`com.htc.htcmsgwidgets3d.apk!/res/xml/htcmsgwidgets.xml`. They read the system
SMS/MMS store through Sense Messages' SMS permission. If access is missing,
tapping the widget opens Messages to enable access. The footer opens the inbox
or a new-message composer, and tapping a conversation opens its thread.

The six Mode10 documents, original picker previews and decoded shared textures
are in `assets/htc/messages/res/`. Source identifiers, scene nodes and hashes are
recorded in `raw/msg_widget_provenance.json`. Mode10 texture rows are flipped
vertically when exported for native views; the original `.m10` bytes are retained.
The card interaction uses Android StackView rather than the legacy Fusion runtime.
The stock destructive delete shortcut is replaced with opening the conversation.

`tools/prepare_messages_app.py` includes widget extraction during explicit ROM
asset regeneration. Normal builds use the checked-in asset bundle, with no ROM
extraction. Collection items use Android's
[RemoteCollectionItems API](https://developer.android.com/reference/android/widget/RemoteViews.RemoteCollectionItems).
Updates run off the UI thread, observe SMS/MMS provider changes while the process
is alive, and refresh on message receipt, widget placement and periodic updates.
The empty card state has a separate layout to avoid an Android StackView crash
when its collection transitions to zero items.

To validate against a disposable emulator with shared APKs:

```sh
./gradlew assembleSharedDebug
python3 tools/build_shared_launcher_tests.py --module messages
python3 tools/test_messages_emulator.py --shared --suite widgets
```

The suite binds temporary widget instances, inserts SMS/MMS fixtures, checks live
updates and empty states, removes its test widgets/messages, and restores the
prior SMS role and permissions. It never sends a message to a carrier.
