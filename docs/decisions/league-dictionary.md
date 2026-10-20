---
type: Decision
title: League comes from a bundled team dictionary
description: Why a game's league is derived offline from full team names in teams.json, and how NCAAF is handled without one.
resource: app/src/main/java/com/bmiller/uzzu/schedule/TeamDictionary.kt
status: stable
tags: [schedule, classification]
generated: {by: claude/opus-5.5, at: 2026-10-20T06:30:00Z}
---
**Why**: the playlist carries no league tag, and logo ids (t1–t32 NFL, t33+ MLB) break in the
sample (t125, t519), so they are ignored. The league must come from team names. An offline
dictionary classifies instantly on every launch, even when ESPN is down.

**Rules**
- Full names only: display name, "location nickname", manual aliases. Nickname, short name and
  abbreviation are excluded because they collide across leagues (Giants, Cardinals, Rangers,
  Kings, Panthers, Jets).
- Both sides must resolve to the same single league; otherwise the game is UNGROUPED.
- Names are compared after `normalizeName` (accents folded, so "Montréal" = "Montreal";
  apostrophes dropped; punctuation → space). Quirks such as "Saint Louis" or "Utah Hockey Club"
  are aliases in teams.json, not normalization code.

**NCAAF (best effort, PLAN)**: no dictionary (too many teams, inconsistent naming). Any game the
dictionary leaves UNGROUPED is tried against the NCAAF scoreboard; a match sets league NCAAF.
Ambiguous schools (Miami FL/OH, USC): if the pair matches events with more than one distinct
matchup, nothing is matched and a warning is logged. Misses stay Ungrouped by design.

**Regenerating teams.json** (`python tools/generate_teams.py` from the repo root, stdlib only)
- When: a team is renamed or relocated, or a playlist spelling fails to classify (add it to
  `MANUAL_ALIASES` first). Never add a bare nickname that exists in another league.
- The script hits ESPN's teams endpoints at dev time only; the output is committed and never
  fetched at runtime. It warns when an alias target no longer exists. Output is written with LF
  endings on every OS, so a Windows run does not produce a whole-file diff.
- Then run the unit tests (LeagueClassifierTest uses the real asset).
