---
type: Decision
title: Version from Gradle properties, signing from env or signing.properties
description: How version numbers and release signing are supplied without committing secrets.
resource: app/build.gradle.kts
status: stable
tags: [build, release]
generated: {by: claude/opus-5.5, at: 2026-10-20T00:00:00Z}
---
**Version**: `-PversionName=1.2.3`. A leading `v` is stripped, so a git tag can be passed as-is.
`-PversionCode=N` is optional; otherwise it is derived as `major*10000 + minor*100 + patch`.
Default is `0.1.0`. The in-app updater (M5) compares the GitHub release `tag_name` with `VERSION_NAME`.

**Signing**: each value comes from an env var first, then from a gitignored root `signing.properties`:
`UZZU_KEYSTORE` / `storeFile`, `UZZU_STORE_PASSWORD` / `storePassword`,
`UZZU_KEY_ALIAS` / `keyAlias` (defaults to `uzzu`), `UZZU_KEY_PASSWORD` / `keyPassword`.
If no keystore file resolves, `assembleRelease` still succeeds and produces an unsigned APK.
In CI the keystore comes from GitHub Actions secrets.

**Why**: one build script locally and in CI, no secrets in the repo, and a missing key never breaks
debug or CI builds. Never rotate the keystore: Android refuses updates signed with a different key.
