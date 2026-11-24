package dev.sense.launcher;

import android.service.notification.NotificationListenerService;

/** Enables system media-session access; notification contents are not read or stored. */
public final class MediaAccessService extends NotificationListenerService {
    @Override public void onListenerConnected(){MediaRepository.get(this).refreshAccess();}
    @Override public void onListenerDisconnected(){MediaRepository.get(this).refreshAccess();}
}
