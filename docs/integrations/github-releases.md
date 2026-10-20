---
type: Integration
title: GitHub Releases in-app updater
description: How the app finds, downloads and installs a newer signed APK from GitHub Releases, and the Fire OS install traps.
resource: app/src/main/java/com/bmiller/uzzu/update/GitHubUpdateChecker.kt
tags: [updates, release]
generated: {by: claude/opus-5.5, at: 2026-10-20T23:00:00Z}
---
- **Source**: unauthenticated `GET api.github.com/repos/<owner>/uzzu-iptv/releases/latest`. The repo
  is public, so no token is needed. `releases/latest` never returns drafts or prereleases, and the
  checker skips them anyway. A 404 means no release yet, so the app counts as up to date.
- **Compare**: semver `tag_name` vs `BuildConfig.VERSION_NAME` (`Version`). An unparseable tag
  never offers an update.
- **When**:
  - On launch, at most once per **24 h**. Failures also write the stamp, so a dead network does not
    retry on every launch.
  - S7 "Check for updates" forces a check.
  - **Debug builds skip the launch check** (S7 only).
  - "Later" hides the bar for that release.
- **Install**:
  - Download the `.apk` asset to `cacheDir/updates`, checking size and, when the release has an
    `<apk>.sha256`, the SHA-256 (hashed while streaming). A mismatch deletes the file and shows
    "Update failed: checksum mismatch".
  - The installer is launched only while the app is in the foreground. A download that finishes
    in the background waits for the user to select Install update again.
  - Open it with `ACTION_VIEW` on a `FileProvider` content URI with a read grant. That works on
    Fire OS 7 and on API 29+.
  - The package installer verifies the signature.
- **Fire OS** (verified on the Stick HD, v0.9.0 → v1.0.0): if Uzzu lacks "Install unknown apps",
  the first Install opens Developer Options › Install unknown apps; enable it for Uzzu only. The
  second Install launches the system installer. Its INSTALL button is reached with Down (then
  Right) on the remote; over adb a tap was needed. The update kept the app's data.
- **Signature**: an update installs only over an APK signed with the same key. A debug build
  (debug key) cannot take a release APK, so uninstall first, which wipes the saved URL. Debug builds
  still get offers in S7, because their version defaults to 0.1.0.
- How releases are produced: [Releasing](/guides/releasing.md).
