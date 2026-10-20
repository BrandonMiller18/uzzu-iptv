---
okf_version: "0.2"
---
# Uzzu IPTV docs

- [Overview](/overview.md) — what the app is, scope, milestone status

## Architecture
- [App wiring](/architecture/app-wiring.md) — AppContainer manual DI; playlist fetch → parse → in-memory model
- [Schedule pipeline](/architecture/schedule-pipeline.md) — classify → ESPN match → sort; polling only while visible, after launch fetch
- [Playback lifecycle](/architecture/playback-lifecycle.md) — one ExoPlayer, created onStart, released onStop

## Decisions
- [Spec precedence](/decisions/spec-precedence.md) — PLAN.md wins on behavior, HANDOFF.md on presentation
- [Secret handling](/decisions/secret-handling.md) — playlist URL never in repo/logs; SecretMasker; debug run-as ingestion
- [Versioning and signing](/decisions/versioning-signing.md) — -PversionName/-PversionCode; UZZU_* env or signing.properties
- [League dictionary](/decisions/league-dictionary.md) — offline teams.json, full names only, both teams agree; NCAAF best effort; regenerating
- [Pinned libraries for SDK 36](/decisions/pinned-sdk36-libraries.md) — newest AndroidX/Coil/OkHttp need compileSdk 37

## Integrations
- [ESPN scoreboard](/integrations/espn-scoreboard.md) — undocumented API, ScheduleSource swap point, status mapping, synthetic fixtures

## Guides
- [Channel rules](/guides/channel-rules.md) — adding a channel_groups.json rule; first-match ordering traps
- [Fire TV testing](/guides/fire-tv-testing.md) — adb over Wi-Fi, keyevents, screenshots, logs

## Gotchas
- [Git Bash mangles adb paths](/gotchas/git-bash-adb-paths.md) — set MSYS_NO_PATHCONV=1
- [File DataStore fails in Windows JVM tests](/gotchas/datastore-windows-tests.md) — use in-memory DataStore
