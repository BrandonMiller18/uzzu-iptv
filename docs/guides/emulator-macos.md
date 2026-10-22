---
type: Guide
title: Testing on an Android TV emulator (macOS)
description: One-time setup of JDK 17, the Android SDK and a 1080p TV emulator on Apple Silicon, plus what it can and can't stand in for.
tags: [testing, emulator, macos]
generated: {by: claude/opus-5.5, at: 2026-10-21T23:00:00Z}
---
Use the emulator for quick UI and flow checks between sessions on the real device. Every adb command in
[Fire TV testing](/guides/fire-tv-testing.md) works against it unchanged (keyevents, screencap, logcat,
`run-as`, fixture mode, debug hooks).

## Setup (Apple Silicon, Homebrew)
```bash
brew install openjdk@17
brew install --cask android-commandlinetools
# ~/.zshrc
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

yes | sdkmanager --licenses
sdkmanager "platform-tools" "emulator" "platforms;android-36" \
  "system-images;android-31;android-tv;arm64-v8a"
echo no | avdmanager create avd -n uzzu_tv31 -k "system-images;android-31;android-tv;arm64-v8a" -d tv_1080p
echo "sdk.dir=$ANDROID_HOME" > local.properties   # gitignored
```
- Use the `openjdk@17` formula, not the `zulu@17` cask. The cask's .pkg installer needs sudo and fails
  silently in a non-interactive shell.
- The lowest arm64 TV image is **API 31** (there is no API 28 arm64 image). That is three levels above
  the Fire TV Stick HD (API 28), so API-28-only behavior still needs the stick. A `google-tv` image
  (31–36) is also available for checking the Google TV launcher.
- `tv_1080p` already matches the stick: 1920x1080 at 320 dpi (960x540 dp).
- In `~/.android/avd/uzzu_tv31.avd/config.ini`, set `hw.keyboard=yes` so the Mac's arrow keys and Enter
  act as the D-pad, and `hw.gpu.mode=host` for smooth rendering.
- `avdmanager` prints `Could not load devices from .../devices.xml`. It's harmless and the AVD is still created.

## Daily use
```bash
emulator -avd uzzu_tv31 -no-boot-anim &
./gradlew installDebug
adb shell am start -n com.bmiller.uzzu/.MainActivity --ez uzzu.fixture true
```
On a fresh install, fixture mode still opens on S0. Save any placeholder such as
`https://example.invalid/fixture.m3u`: `FixtureInterceptor` answers the playlist fetch whatever the URL
is. For the real playlist, use the debug-file route in [Secret handling](/decisions/secret-handling.md).
The emulator window and its screenshots are covered by the same never-screenshot-the-URL rule as the device.

## What it can't stand in for
- **Fire OS.** Amazon no longer ships a Fire TV emulator. Remote, launcher and Fire OS quirks need the stick.
- **Performance.** The Mac is far faster than the device floor. Timing goes on the stick with the
  `bench` build ([Performance](/guides/performance.md)).
- **Focus.** The emulator is closer to the device than Robolectric, but focus paths that matter still
  need a device check ([gotcha](/gotchas/compose-focus-device-vs-robolectric.md)).
- **Playback.** The emulator uses the Mac's network and software decoders. Check real-stream playback on the device.
