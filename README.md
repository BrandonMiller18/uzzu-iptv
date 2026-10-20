# Uzzu IPTV Player

A single-user, sideloadable Android TV / Fire TV app that plays one uzzu.tv M3U playlist: live games
(Home/Away feeds) and live channels, built with Compose for TV and Media3 ExoPlayer.

See [PLAN.md](PLAN.md) for the full spec and milestones, and [design-handoff/HANDOFF.md](design-handoff/HANDOFF.md)
for the UI design.

## Build

Requires JDK 17 and the Android SDK (platform 36). From the repo root:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug   # what CI runs
./gradlew recordRoborazziDebug                         # JVM screenshots -> app/build/outputs/roborazzi/
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Version: `-PversionName=1.2.3` (and optionally `-PversionCode=10203`); defaults to 0.1.0.

Release signing reads `UZZU_KEYSTORE`, `UZZU_STORE_PASSWORD`, `UZZU_KEY_ALIAS`, `UZZU_KEY_PASSWORD` from
the environment, or `storeFile`/`storePassword`/`keyAlias`/`keyPassword` from a gitignored
`signing.properties`. Without either, `assembleRelease` produces an unsigned APK.

## Privacy

The playlist URL contains account credentials. It is entered on the device at first run and stored in
app-private DataStore only; it never belongs in source, tests, CI or logs. All logged URLs are masked.

Fonts: Roboto and Roboto Mono, SIL Open Font License 1.1 (see `app/src/main/assets/licenses/`).
