---
type: Project
title: Uzzu IPTV Player
description: Single-user, sideloaded Android TV / Fire TV app that plays one uzzu.tv M3U playlist (live games with Home/Away feeds, live channels).
generated: {by: claude/opus-5.5, at: 2026-10-20T18:00:00Z}
---
Kotlin, Compose for TV, Media3 ExoPlayer (HLS only), single `:app` module, package `com.bmiller.uzzu`.
minSdk 28 (Fire TV Stick HD, Fire OS 7), compile/targetSdk 36, ABIs armeabi-v7a + arm64-v8a only.

Not in scope: multiple users or playlists, EPG, DVR, Play Store distribution. Releases are signed
APKs on GitHub Releases and the app updates itself (M5).

**Status**: M1 done (first-run URL entry, fetch + parse, plain debug list, playback). M2 done
(dictionary league classification, ESPN schedule + 60 s polling, channel sub-groups). M3 done
(S0–S7 UI per HANDOFF, back-stack navigation, fixture mode, bench build). M4 done (S5 tune banner,
O1 overlay, S6 error, retry policy, CH up/down, last channel). M5 done (tag-driven signed releases,
in-app updater, S1 update bar). Milestones M1–M5 live in `PLAN.md`.

## Run / test
- JDK 17 is required; set `JAVA_HOME` explicitly if the system `java` is older.
- CI and local gate: `./gradlew testDebugUnitTest lintDebug assembleDebug`.
- JVM screenshots (Roborazzi + Robolectric): `./gradlew recordRoborazziDebug` writes PNGs to
  `app/build/outputs/roborazzi/`. Tests run at 960x540 dp xhdpi, so a PNG is 1920x1080 px and
  maps ~1:1 onto the design canvas px in `design-handoff/design/*.dc.html`; compare side by side.
- Perf numbers: `./gradlew assembleBench` only ([Performance](/guides/performance.md)).
- The only playlist in the repo is the scrubbed fixture `app/src/test/resources/fixtures/sample.m3u`.
- On a device: see [Fire TV testing](/guides/fire-tv-testing.md). End-user install steps are in `README.md`.

## Map
- [App wiring](/architecture/app-wiring.md), [Playback lifecycle](/architecture/playback-lifecycle.md)
- [PlaybackManager](/architecture/playback-manager.md), [Retry policy + S6](/architecture/retry-policy.md), [Player key map](/decisions/player-key-map.md)
- [Releasing](/guides/releasing.md), [GitHub updater](/integrations/github-releases.md)
- [Schedule pipeline](/architecture/schedule-pipeline.md), [League dictionary](/decisions/league-dictionary.md), [ESPN](/integrations/espn-scoreboard.md)
- [UI navigation](/architecture/ui-navigation.md), [tv-material3 styling](/decisions/tv-material3-styling.md)
- [Secret handling](/decisions/secret-handling.md), [Spec precedence](/decisions/spec-precedence.md)
