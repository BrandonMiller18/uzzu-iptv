---
type: Flow
title: Classification and schedule pipeline
description: How games get league, start time and status, and when ESPN is polled.
resource: app/src/main/java/com/bmiller/uzzu/schedule/ScheduleRepository.kt
tags: [architecture, schedule]
generated: {by: claude/opus-5.5, at: 2026-10-20T06:30:00Z}
---
1. **Offline, at parse time**: `PlaylistRepository` runs `PlaylistClassifier` right after parsing:
   dictionary league per game ([decision](/decisions/league-dictionary.md)), sub-group per
   channel ([channel rules](/guides/channel-rules.md)).
2. **Runtime**: `ScheduleRepository.model` = playlist state combined with cached ESPN events,
   passed through `ScheduleMatcher`. **The UI reads `model`, not `PlaylistRepository.state`.**
   The playlist object is replaced on every poll, so key UI state on ids, not object identity.
3. **Matching**: away→away and home→home against displayName / shortDisplayName / location or
   the dictionary full names of that ESPN team. Today and yesterday are fetched (late games run
   past midnight), so a matchup can appear twice (series, doubleheader): prefer the Live event,
   then the next Scheduled, then the most recent Final.
4. **Sort**: start time, then playlist order; untimed after timed; Ungrouped last. Final never
   moves a game.

**Polling**
- Every 60 s, only while a consumer holds a `startPolling()` handle. Screens open one when
  visible (lifecycle start) and close it on stop, so nothing hits ESPN in the background or
  during playback without the overlay. Handles are ref-counted; reopening within 60 s of the last
  fetch waits out the remainder instead of refetching.
- Polling first waits until the launch playlist fetch leaves Idle/Loading. On the Fire TV Stick
  HD, ten parallel ESPN calls during launch doubled the playlist fetch (2.3 s → 4.5 s) that
  gates the first screen.
- `refresh()` never throws; per league/day failures keep the last values and are logged without
  the URL (the masker would mangle the date).
