---
type: Guide
title: Cutting a release
description: Tag-driven release workflow that publishes the signed APK the in-app updater installs.
resource: .github/workflows/release.yml
tags: [release, ci]
generated: {by: claude/opus-5.5, at: 2026-10-20T18:00:00Z}
---
1. Pick `vX.Y.Z`. Keep minor and patch below 100 (see versionCode). Tag `main`, then push the tag:
   `git tag v1.0.0 && git push origin v1.0.0`.
2. `release.yml`:
   - checks the tag format and runs the unit tests
   - decodes the keystore into `$RUNNER_TEMP`, then runs `assembleRelease -PversionName=$TAG`
   - verifies the signature with `apksigner` (an unsigned APK fails the job)
   - creates GitHub Release `vX.Y.Z` with `uzzu-iptv-X.Y.Z.apk`, `uzzu-iptv-X.Y.Z.apk.sha256` and generated notes
   - deletes the keystore under `if: always()`

   Pushing the same tag again re-uploads the assets (`--clobber`).
3. Installed apps see the release within 24 h, or at once through S7 ([updater](/integrations/github-releases.md)).

**Secrets** (repo settings → Actions): `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`,
`RELEASE_KEY_PASSWORD`, `RELEASE_KEY_ALIAS`. The workflow maps them to the `UZZU_*` env vars that
`app/build.gradle.kts` reads ([versioning and signing](/decisions/versioning-signing.md)).

**versionCode** = `major*10000 + minor*100 + patch` (1.2.3 → 10203), derived from the tag. With
minor or patch ≥ 100 the codes collide (QA M45-14).
**Prerelease** tags (`v1.1.0-rc1`, or any tag with `-`) are published as GitHub prereleases, and the
updater ignores them. An rc shares the versionCode of its final release; this is a known, accepted limit.

**Keystore backup**: the only copies are the GitHub secret and an offline backup that the owner keeps
outside the repo. If the key is lost, every user has to uninstall before they can update.

**Not done yet**: `mapping.txt` is not archived (QA M45-06). Once the run is gone, release stack
traces cannot be de-obfuscated.
