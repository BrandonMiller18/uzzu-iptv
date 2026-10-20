# Uzzu IPTV Player

A single-user, sideloadable Android TV / Fire TV app that plays one uzzu.tv M3U playlist: live games
(Home/Away feeds) and live channels, built with Compose for TV and Media3 ExoPlayer.

See [PLAN.md](PLAN.md) for the full spec and milestones, and [design-handoff/HANDOFF.md](design-handoff/HANDOFF.md)
for the UI design. Developer docs (architecture, decisions, gotchas) start at [docs/index.md](docs/index.md).

## Install on Fire TV / Google TV

The app is not in any store; you sideload the APK from
[GitHub Releases](https://github.com/BrandonMiller18/uzzu-iptv/releases/latest).

1. **Turn on Developer Options.** Fire TV: Settings → My Fire TV → About, then click the device name
   7 times. Google TV: Settings → System → About, then click *Android TV OS build* 7 times.
2. **Allow installs.** In Developer Options, turn on **ADB debugging** (only needed for the adb route)
   and **Apps from Unknown Sources** (newer Fire OS and Google TV ask per app: allow the installer
   you use, for example Downloader).
3. **Install**, either way:
   - **Downloader app** (from the Amazon Appstore / Play Store): enter
     `https://github.com/BrandonMiller18/uzzu-iptv/releases/latest`, download the `.apk` asset and
     install it.
   - **adb from a computer** on the same network: find the TV's IP under Settings → About → Network, then
     ```sh
     adb connect <tv-ip>
     adb install -r <path-to-downloaded>.apk
     ```
     Accept the debugging prompt on the TV the first time. No Android Studio? Download just
     [Android SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools), unzip it,
     and run `adb` from that folder.
4. **Open Uzzu** and enter your playlist URL on the first-run screen.

Later versions install from inside the app. The first in-app update makes the system ask you to allow
**Install unknown apps** for Uzzu; that prompt is expected, so allow it once. On Fire TV, the system
installer screen may not focus its **Install** button right away: press Down (then Right) on the remote
until **Install** is highlighted, then OK.

## Build

Requires JDK 17 and the Android SDK (platform 36). From the repo root:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug   # what CI runs
./gradlew recordRoborazziDebug                         # JVM screenshots -> app/build/outputs/roborazzi/
adb install -r app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleBench                                # release code, debug key: use for perf timing
adb shell am start -n com.bmiller.uzzu/.MainActivity --ez uzzu.fixture true   # debug: fake playlist + ESPN data
```

Version: `-PversionName=1.2.3` (and optionally `-PversionCode=10203`); defaults to 0.1.0.

Release signing reads `UZZU_KEYSTORE`, `UZZU_STORE_PASSWORD`, `UZZU_KEY_ALIAS`, `UZZU_KEY_PASSWORD` from
the environment, or `storeFile`/`storePassword`/`keyAlias`/`keyPassword` from a gitignored
`signing.properties`. Without either, `assembleRelease` produces an unsigned APK.

## Releases

Push a tag `vX.Y.Z` (for example `git tag v1.0.0 && git push origin v1.0.0`). The
[Release workflow](.github/workflows/release.yml) runs the unit tests, builds the R8 release APK signed
with the key from the repo secrets (`RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`,
`RELEASE_KEY_PASSWORD`, `RELEASE_KEY_ALIAS`), verifies the signature and publishes it as
`uzzu-iptv-X.Y.Z.apk` on a GitHub Release named after the tag. versionName is the tag without `v`;
versionCode is derived from it (`1.2.3` -> `10203`). Tags with a suffix (`v1.1.0-rc.1`) become
pre-releases, which the app ignores.

- Downloader / browser URL for the newest APK: `https://github.com/BrandonMiller18/uzzu-iptv/releases/latest`
- Installed apps check `releases/latest` on launch (at most once per 24 h; debug builds only from
  Settings) and from Settings → Check for updates, then offer the update on the home screen.

## Privacy

The playlist URL contains account credentials. It is entered on the device at first run and stored in
app-private DataStore only; it never belongs in source, tests, CI or logs. All logged URLs are masked.

Fonts: Roboto and Roboto Mono, SIL Open Font License 1.1 (see `app/src/main/assets/licenses/`).
