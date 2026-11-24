# Native Sense Contacts (People)

Build and install from the repository root:

```sh
source tools/android-env.sh
./gradlew :contacts:assembleDebug
adb install -r contacts/build/outputs/apk/debug/contacts-debug.apk
adb shell am start -n dev.sense.contacts/.ContactsActivity
```

The standalone Android 15+ app is `dev.sense.contacts`, labeled **Sense Contacts**.
It reconstructs the People interface with native Android views; it does not run
HTC's obsolete telephony or contacts framework. The existing Sense Phone contacts
tab remains a phone-number picker; this app presents each aggregated person once,
including contacts with no phone number.

`tools/prepare_contacts_app.py` recovers People-specific hdpi images from
`HtcContacts.apk` in `res/3.33.401.153.zip`. Common styling comes from
[`sense-framework`](SENSE-FRAMEWORK.md). Generated app resources stay
under `assets/htc/contacts/res`. Each asset's source and SHA-256 are
recorded in `raw/people_provenance.json`. Compiled nine-patch chunks are copied
unchanged, with `.png` filenames suitable for modern resource packaging. The default portrait, search icon, frames, input fields, tabs, list dividers
and highlighting come from the shared library; People-specific phone/message/
mail/map/organization artwork stays in this module. Silver detail icons
receive a dark multiply tint for legibility on the light cards. Native text and layouts
adapt to display density, safe system insets and the keyboard.

People lists system aggregated contacts with alphabetical headings and search by
name, any phone number or email. Search ignores name accents and common phone
number formatting. Favorites reflects the system `STARRED` value; changing it
requests write access only when the user taps the star. Groups and membership
come from the system groups provider; selecting a group filters its members.

Contact cards show all supported fields: phones, email, postal addresses,
organizations, websites, events and notes. A number opens Sense Phone when
installed, otherwise the system dialer. SMS/email/map/web actions open their
corresponding Android handlers and never send a message or place a call directly.
Add and Edit open Android's installed contact editor, preserving account and sync
handling; the editor itself is not HTC styled in this version. The app implements
VIEW for system contact lookup URIs and the APP_CONTACTS launcher category.

Read permission is requested from an explicit Allow button. Contact queries and
thumbnail decoding run on workers. Decoded photos use a bounded 4 MB cache;
missing/inaccessible photos show the HTC portrait placeholder. Provider observers
refresh live changes; returning from the contact editor reloads the snapshot.
The app does not delete contacts or alter group membership. Legacy social-network
updates, HTC account sync and HTC-specific online integrations are not recreated.

Validation:

```sh
./gradlew :contacts:assembleDebug :contacts:assembleDebugAndroidTest :contacts:lintDebug
adb install -r contacts/build/outputs/apk/androidTest/debug/contacts-debug-androidTest.apk
adb shell am instrument -w dev.sense.contacts.test/dev.sense.contacts.PeopleInstrumentation
```

The runner seeds temporary local contacts/groups, tests real provider data and
intercepts add/edit/SMS/email intents. It deletes only its fixture raw-contact and
group URIs in `finally`, and reports the original read/write permission states.
Restore any permission that was originally false after instrumentation returns;
revoking a permission inside the runner would kill its process. Test screenshots
are exported to `files/people-list-test.png` and `files/people-detail-test.png` in
the app's private storage.
