---
type: Guide
title: Cutting a release
description: Tag-driven release workflow that publishes the signed APK the in-app updater installs.
resource: .github/workflows/release.yml
tags: [release, ci]
generated: {by: claude/opus-5.5, at: 2026-10-20T23:00:00Z}
---
1. Pick `vX.Y.Z`. Minor and patch must be below 100 (see versionCode). Tag `main`, then push the tag:
   `git tag v1.0.0 && git push origin v1.0.0`.
2. `release.yml`:
   - checks the tag format and runs the unit tests
   - decodes the keystore into `$RUNNER_TEMP`, then runs `assembleRelease -PversionName=$TAG`
   - verifies the signature with `apksigner` (an unsigned APK fails the job)
   - creates GitHub Release `vX.Y.Z` with `uzzu-iptv-X.Y.Z.apk`, `.apk.sha256`, `uzzu-iptv-X.Y.Z-mapping.txt`
     (R8 mapping, kept with the release so stack traces can always be de-obfuscated) and generated notes
   - deletes the keystore under `if: always()`

   Pushing the same tag again re-uploads the assets (`--clobber`).
3. Installed apps see the release within 24 h, or at once through S7 ([updater](/integrations/github-releases.md)).

**Secrets** (repo settings → Actions): `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`,
`RELEASE_KEY_PASSWORD`, `RELEASE_KEY_ALIAS`. The workflow maps them to the `UZZU_*` env vars that
`app/build.gradle.kts` reads ([versioning and signing](/decisions/versioning-signing.md)).
Set them from Git Bash, not PowerShell 5.1 ([gotcha](/gotchas/gh-secret-powershell.md)).
CI (`ci.yml`) also builds an unsigned release APK and fails if debug-hook strings reach its dex or
fixture assets reach the APK.

**versionCode** = `major*10000 + minor*100 + patch` (1.2.3 → 10203), derived from the tag. Minor or
patch ≥ 100 would collide, so `release.yml` and the Gradle script reject such versions.
**Prerelease** tags (`v1.1.0-rc1`, or any tag with `-`) are published as GitHub prereleases, and the
updater ignores them. An rc shares the versionCode of its final release; this is a known, accepted limit.

**Keystore backup**: the only copies are the GitHub secret and an offline backup that the owner keeps
outside the repo. If the key is lost, every user has to uninstall before they can update.

**History**: v0.9.0 and v1.0.0 published by `release.yml` and verified end to end on the Fire TV
([updater](/integrations/github-releases.md)); v1.0.1 (S1 focus fix) followed.
