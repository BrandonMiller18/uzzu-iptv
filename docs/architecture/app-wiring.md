---
type: Component
title: App wiring and playlist data flow
description: Manual DI through AppContainer and how the playlist goes from URL to the in-memory model.
resource: app/src/main/java/com/bmiller/uzzu/AppContainer.kt
tags: [architecture, data]
generated: {by: claude/opus-5.5, at: 2026-10-20T06:30:00Z}
---
- **Manual DI**: `UzzuApp` builds one `AppContainer` in `onCreate`; activities reach it via
  `(application as UzzuApp).container`. No Hilt/Koin: one module, a handful of singletons.
- **One OkHttpClient** is shared by the playlist fetch, Coil (`UzzuApp` is the
  `SingletonImageLoader.Factory`) and Media3 (`OkHttpDataSource`). Timeouts/interceptors go there only.
- **Settings** wraps Preferences DataStore: the playlist URL (a secret) plus fetch/update
  timestamps. Tests inject an in-memory `DataStore` (see [gotcha](/gotchas/datastore-windows-tests.md)).
- **PlaylistRepository** flow: saved URL → OkHttp GET → body must start with `#EXTM3U` →
  `M3uParser` → `PlaylistClassifier` (league + channel sub-group) → `Playlist` model held in a
  `StateFlow<PlaylistState>` for the process lifetime. The UI reads `ScheduleRepository.model`,
  which adds ESPN start time/status on top ([schedule pipeline](/architecture/schedule-pipeline.md)).
  Nothing parsed is persisted; every app launch calls `refresh()` before showing data.
- **Invariants**: a failed refresh keeps the previous `Loaded` model visible; error messages pass
  through `SecretMasker` before reaching state, DataStore or UI; a playlist with no Live-Games and
  no Live-Channels entries is a failure.
- Bundled assets (teams.json, channel_groups.json) are parsed lazily on first use, inside the
  playlist fetch's IO dispatcher, not on the main thread at startup.
- First-run `saveAndLoad` validates by fetching and parsing *before* saving the URL.
- M1 navigation is a plain `Screen` state machine in `MainActivity` (Setup / List / Player);
  the real S1–S7 screens arrive in M3.
