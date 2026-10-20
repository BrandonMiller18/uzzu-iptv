---
type: Gotcha
title: Entering the playlist URL on a release build over adb
description: Release builds have no run-as or debug URL hook, so the secret URL must be typed via adb input from an on-device file, and the injection can corrupt it.
tags: [device, security, testing]
generated: {by: claude/opus-5.5, at: 2026-10-20T23:00:00Z}
---
**Context**: the debug URL feed ([secret handling](/decisions/secret-handling.md)) needs `run-as`
and `BuildConfig.DEBUG`, so it does not work on a release APK. The URL must never be on a host
command line, in logs or in a screenshot.

**Procedure**:
1. `adb push <url file> /data/local/tmp/u.txt` (Git Bash: `MSYS_NO_PATHCONV=1`).
2. Focus the URL field, press OK to open the editor, then expand the file *on the device* so the
   value never crosses the host command line:
   `adb shell 'input text "$(cat /data/local/tmp/u.txt)"'`.
3. **Verify before saving**: adb text injection once duplicated the last character. Compare the
   field with the file on the device by length / equality without printing either value.
4. Never screenshot while the URL is visible in the field.
5. `adb shell rm /data/local/tmp/u.txt`.
