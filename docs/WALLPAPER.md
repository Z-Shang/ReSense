# System wallpaper

The launcher window enables Android's `windowShowWallpaper` and uses a transparent
window background. The home panel and its stage are transparent, allowing the
system wallpaper service to draw behind the workspace and translucent widget/dock
surfaces. No wallpaper bitmap is read or copied, and no storage permission is
required. This also permits live wallpapers to render through the system service.

The app drawer retains its opaque dark background. Sliding the panel in or out
reveals the home/wallpaper behind it. The page overview hides the underlying home
panel while its dialog is visible and restores it on dismissal; the dialog uses a
translucent shade so the wallpaper stays visible behind the page cards.

Dock Settings → Wallpaper opens the system wallpaper chooser. The external
wallpaper application handles selection and applies it; the launcher does not
request direct SET_WALLPAPER access.

Android reference:
https://developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_SHOW_WALLPAPER
