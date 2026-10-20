---
type: Gotcha
title: Git Bash rewrites adb device paths
description: MSYS path conversion turns /data/... or /sdcard/... arguments into Windows paths.
generated: {by: claude/opus-5.5, at: 2026-10-20T00:00:00Z}
---
**Symptom**: `adb push x /data/local/tmp/x` or `adb shell ls /sdcard` fails, or targets something like
`C:/Program Files/Git/data/local/tmp/x`.

**Cause**: Git Bash (MSYS) converts arguments that look like POSIX absolute paths into Windows paths
before it runs a native `.exe` such as `adb.exe`.

**Fix / avoid**: prefix the command with `MSYS_NO_PATHCONV=1` (for example
`MSYS_NO_PATHCONV=1 adb push x /data/local/tmp/x`), or run it from PowerShell.
