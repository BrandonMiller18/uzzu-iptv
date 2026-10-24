# Brand artwork

Source art for the launcher icon and TV banner. Not packaged into the APK.

- `masters/` — 1024 px logo on black and on transparent. Regenerate the `res/mipmap-*` assets from these.
- `store/icon-512x512.png` — store-listing icon.
- `fire-tv/app-icon-1280x720.png` — Amazon Appstore listing art. A sideloaded install shows the
  APK's `android:banner` (`@mipmap/tv_banner`) instead.

In the APK: adaptive icon `mipmap-anydpi/ic_launcher.xml` (black `@color/uzzu_icon_background` +
`ic_launcher_foreground` at 108 dp per density) and `tv_banner` at 160x90 dp per density. minSdk 28
means every device takes the adaptive icon, so there are no legacy `ic_launcher.png` files.
