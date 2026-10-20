---
type: Guide
title: Measuring performance and the bench build
description: Why perf numbers come from the bench build type, measured Fire TV results, and the open S1 budget issue.
resource: app/build.gradle.kts
tags: [performance, device]
generated: {by: claude/opus-5.5, at: 2026-10-20T12:00:00Z}
---
- **Never measure a debug build.** Debug is unminified and debuggable (ART runs it much slower).
- **`bench` build type** (`./gradlew assembleBench`): `initWith(release)` (R8, shrink, not
  debuggable) but signed with the debug key, so it installs over a debug build without
  uninstalling (keeps DataStore and the saved URL) and needs no release keystore. Library
  modules fall back to their release variant. Use it for every timing; never ship it.
- **Measured on the Fire TV Stick HD (M3)**: cold start debug 6.8 s vs bench 1.1 s.
- **Open issue**: the provider's playlist fetch takes ~8.7 s and is server-side,
  so "cold start to S1 with counts < 4 s" (PLAN budget) cannot be met from the app alone.
  Not fixed in M3.
- Budgets live in `PLAN.md` ("Performance budget"). Device workflow: [Fire TV testing](/guides/fire-tv-testing.md).
