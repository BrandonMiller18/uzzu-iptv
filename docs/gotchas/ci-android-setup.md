---
type: Gotcha
title: CI breaks on setup-android and on a non-executable gradlew
description: Two GitHub Actions failures seen when building this repo, and their fixes.
resource: .github/workflows/ci.yml
tags: [ci]
generated: {by: claude/opus-5.5, at: 2026-10-20T18:00:00Z}
---
**setup-android**: `android-actions/setup-android@v3` breaks on the runner's current
cmdline-tools. Both workflows use the SDK preinstalled on `ubuntu-latest` (`$ANDROID_HOME`),
accept licenses with `sdkmanager --licenses` and let AGP download missing platforms. Don't re-add it.

**gradlew: Permission denied**: a commit from Windows stores `gradlew` as mode 100644. Fix it once
with `git update-index --chmod=+x gradlew` and commit. Check with `git ls-files -s gradlew` (it should show `100755`).

Also:
- Both workflows pass `--no-configuration-cache`, so the configuration cache never stores signing env values.
- CI builds an unsigned `assembleRelease` with R8 on, which catches shrinker breakage before a tag does.
