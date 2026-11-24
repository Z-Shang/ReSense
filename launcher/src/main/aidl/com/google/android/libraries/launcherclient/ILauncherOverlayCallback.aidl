// Interface from LawnchairLauncher/lawnchair (Apache-2.0).
// See third_party/launcher-overlay/NOTICE and LICENSE for source and attribution.
package com.google.android.libraries.launcherclient;

interface ILauncherOverlayCallback {

    oneway void overlayScrollChanged(float progress);

    oneway void overlayStatusChanged(int status);

}
