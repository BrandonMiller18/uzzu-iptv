---
type: Decision
title: Pin libraries to versions that compile against SDK 36
description: The newest Compose, Coil, OkHttp, core and lifecycle releases require compileSdk 37; the spec pins 36.
resource: gradle/libs.versions.toml
status: stable
tags: [build, dependencies]
generated: {by: claude/opus-5.5, at: 2026-10-20T00:00:00Z}
---
**Context**: The spec fixes compile/targetSdk at 36. The newest releases of the Compose BOM, Coil,
OkHttp, androidx core and lifecycle require compileSdk 37.

**Decision**: Pin each to the latest version that builds with SDK 36 (see the version catalog).
Lint's `GradleDependency`, `NewerVersionAvailable`, `AndroidGradlePluginVersion` and `OldTargetApi`
checks are disabled so `abortOnError` does not fail on these deliberate pins.

**Rejected**: moving to SDK 37. It contradicts the spec and nothing in the app needs it.

**Revisit**: when the spec moves to SDK 37, bump everything together and re-enable those lint checks.
