---
okf_version: "0.2"
---
# Uzzu IPTV docs

- [Overview](/overview.md) — what the app is, scope, milestone status

## Architecture
- [App wiring](/architecture/app-wiring.md) — AppContainer manual DI; playlist fetch → parse → in-memory model
- [Schedule pipeline](/architecture/schedule-pipeline.md) — classify → ESPN match → sort; polling only while visible, after launch fetch
- [Playback lifecycle](/architecture/playback-lifecycle.md) — one ExoPlayer, created onStart, released onStop
- [PlaybackManager](/architecture/playback-manager.md) — session owner; merge rule (resolve by key+side at tune); Channel.key id#n; last-channel slot
- [Retry policy and S6](/architecture/retry-policy.md) — 2 s/5 s backoff, one refresh, 15 s stall, 30 s healthy reset, Media3 4xx retry off; S6 fields
- [UI navigation](/architecture/ui-navigation.md) — Navigator back stack, AppState/AppActions test seam, ListMemory/FocusGrid, PlayContext, UpdateChecker slot

## Decisions
- [Spec precedence](/decisions/spec-precedence.md) — PLAN.md wins on behavior, HANDOFF.md on presentation
- [Secret handling](/decisions/secret-handling.md) — playlist URL never in repo/logs; SecretMasker; debug run-as ingestion; S7 display masking
- [tv-material3 styling](/decisions/tv-material3-styling.md) — HANDOFF hard rules; Card/Tab focus treatment via modifiers; where rules are enforced
- [Versioning and signing](/decisions/versioning-signing.md) — -PversionName/-PversionCode; UZZU_* env or signing.properties
- [Player key map](/decisions/player-key-map.md) — CH+/Up mapping, long-press Back = repeatCount≥1, non-focusable key-handler parent
- [League dictionary](/decisions/league-dictionary.md) — offline teams.json, full names only, both teams agree; NCAAF best effort; regenerating
- [Pinned libraries for SDK 36](/decisions/pinned-sdk36-libraries.md) — newest AndroidX/Coil/OkHttp need compileSdk 37

## Integrations
- [ESPN scoreboard](/integrations/espn-scoreboard.md) — undocumented API, ScheduleSource swap point, status mapping, synthetic fixtures
- [GitHub Releases updater](/integrations/github-releases.md) — releases/latest, 24 h throttle, FileProvider install, unknown-apps prompt, signature mismatch

## Guides
- [Channel rules](/guides/channel-rules.md) — adding a channel_groups.json rule; first-match ordering traps
- [Fire TV testing](/guides/fire-tv-testing.md) — adb over Wi-Fi, keyevents, screenshots, logs, fixture mode, debug hooks (forced 404, suppress S6)
- [Releasing](/guides/releasing.md) — tag vX.Y.Z → signed APK + .sha256 on GitHub Releases; secrets; versionCode; prereleases
- [Performance](/guides/performance.md) — bench build type, measured cold start and channel switch, open playlist-fetch budget issue

## Gotchas
- [Git Bash mangles adb paths](/gotchas/git-bash-adb-paths.md) — set MSYS_NO_PATHCONV=1
- [File DataStore fails in Windows JVM tests](/gotchas/datastore-windows-tests.md) — use in-memory DataStore
- [D-pad can't leave a text field in Robolectric](/gotchas/robolectric-textfield-focus.md) — click via semantics in tests
- [CI: setup-android and gradlew mode](/gotchas/ci-android-setup.md) — use runner SDK; `git update-index --chmod=+x gradlew`
