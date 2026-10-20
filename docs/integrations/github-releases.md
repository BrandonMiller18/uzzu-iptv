---
type: Integration
title: GitHub Releases in-app updater
description: How the app finds, downloads and installs a newer signed APK from GitHub Releases, and the Fire OS install traps.
resource: app/src/main/java/com/bmiller/uzzu/update/GitHubUpdateChecker.kt
tags: [updates, release]
generated: {by: claude/opus-5.5, at: 2026-10-20T18:00:00Z}
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
  - Download the `.apk` asset to `cacheDir/updates`. The size is checked against the asset; the
    `.sha256` is not verified yet (QA M45-09).
  - Open it with `ACTION_VIEW` on a `FileProvider` content URI with a read grant. That works on
    Fire OS 7 and on API 29+.
  - The package installer verifies the signature.
- **Fire OS**: if Uzzu lacks "Install unknown apps", the app first opens
  `ACTION_MANAGE_UNKNOWN_APP_SOURCES` for its own package. The user allows it once.
- **Signature**: an update installs only over an APK signed with the same key. A debug build
  (debug key) cannot take a release APK, so uninstall first, which wipes the saved URL. Debug builds
  still get offers in S7, because their version defaults to 0.1.0.
- How releases are produced: [Releasing](/guides/releasing.md).
