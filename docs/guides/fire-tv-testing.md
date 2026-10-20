---
type: Guide
title: Testing on a Fire TV over adb
description: Workflow for installing, driving, screenshotting and reading logs on a real Fire TV.
tags: [testing, device]
generated: {by: claude/opus-5.5, at: 2026-10-20T00:00:00Z}
---
Reference device: Fire TV Stick HD (AFTSS), Fire OS 7 / API 28, armeabi-v7a, 1920x1080 at 320 dpi
(960x540 dp). Treat it as the performance floor.

- Connect over Wi-Fi: `adb connect <device-ip>:5555` (accept the prompt on the TV the first time).
- The stick sleeps after about 20 min: send `adb shell input keyevent 224` (WAKEUP) before a session.
- Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
- D-pad: `adb shell input keyevent <code>`. 19/20/21/22 = up/down/left/right, 23 = select,
  4 = back, 85 = play/pause, 166/167 = channel up/down.
- Screenshot: `adb exec-out screencap -p > shot.png` (`exec-out`, not `shell`, keeps the PNG bytes intact).
- Logs: `adb logcat -d -s Uzzu/Player` (tags are `Uzzu/<area>`), or the app's own log file with
  `adb shell run-as com.bmiller.uzzu cat files/logs/app.log` (`run-as` needs a debug build).
- Getting the playlist URL onto a debug build: see [Secret handling](/decisions/secret-handling.md).
- The live playlist only lists games in the evening. Exercise game UI with the fixture
  (unit tests / MockWebServer) and use channels for on-device playback checks.
- In Git Bash, prefix adb commands that take device paths with `MSYS_NO_PATHCONV=1`
  ([gotcha](/gotchas/git-bash-adb-paths.md)).
