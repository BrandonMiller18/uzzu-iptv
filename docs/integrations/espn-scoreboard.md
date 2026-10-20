---
type: Integration
title: ESPN scoreboard (start time and status)
description: The undocumented ESPN API behind ScheduleSource, its risk, and how the fixtures were made.
resource: app/src/main/java/com/bmiller/uzzu/schedule/EspnScheduleSource.kt
tags: [schedule, external]
generated: {by: claude/opus-5.5, at: 2026-10-20T06:30:00Z}
---
- **What**: `site.api.espn.com/apis/site/v2/sports/{sport}/{league}/scoreboard?dates=yyyyMMdd`;
  NCAAF adds `groups=80` (all FBS, not just Top 25). No auth, no key, no documented quota.
- **Risk**: public but undocumented; can change or rate-limit without notice. Everything degrades
  to Ungrouped + playlist order rather than failing, and a failed league/day keeps its last values.
- **Swap point**: `ScheduleSource` (per-league `fetch`, throws on failure). To replace a league,
  implement it (e.g. MLB Stats API, NHL api-web.nhle.com) and wire it in `AppContainer`; the
  matcher and repository don't care where events come from.
- **No scores**: the app never shows scores, so the DTOs have no score fields at all
  (NoScoreTest guards this). Don't add them "for later".
- **Status mapping**: pre → Scheduled, in → Live(shortDetail), post → Final, except
  STATUS_POSTPONED / CANCELED / SUSPENDED, which ESPN reports as "post" but map to **Unknown**:
  calling them Final would claim a game was played, and Scheduled would show a stale time.
- **Fixtures** (`app/src/test/resources/fixtures/espn/`): real recordings from 2026-10-20, except
  the `*_in.json` files, which are **synthetic** (one event's status hand-edited to "in"; no live
  game existed to record). Replace them with real in-progress captures when possible; see the
  README there.
