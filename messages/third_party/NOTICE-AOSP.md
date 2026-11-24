The Java sources under `dev/sense/messages/mms` are derived from the Android
Open Source Project Messaging app, licensed under Apache License 2.0.
Copyright notices are retained in each source file.

Source: https://android.googlesource.com/platform/packages/apps/Messaging/+/refs/heads/main/src/com/android/messaging/
Retrieved: 2026-11-17. Codec: mmslib/pdu (excluding PduPersister),
mmslib/MmsException.java, mmslib/InvalidHeaderValueException.java,
and util/ContentType.java.

Local changes: relocated package; replaced androidx SimpleArrayMap with HashMap;
bounded multipart counts, nesting and lengths before memory allocations.
