---
type: Component
title: UI navigation, state seam and focus restore
description: How screens are routed, how they get state and trigger effects, and how focus and tab survive a trip to the player.
resource: app/src/main/java/com/bmiller/uzzu/ui/AppContent.kt
tags: [ui, architecture]
generated: {by: claude/opus-5.5, at: 2026-10-20T12:00:00Z}
---
- **Navigator** is a hand-rolled back stack of `Route`s (no Navigation-Compose). Graph:
  S0 → S1 → S2/S3/S7; S2 → S4 picker → player; S3 → player. `pop()` returns false at the root and
  `AppContent` then calls `actions.exit()` (Back on S1 exits). A successful first-run save
  `reset`s the stack to Home so Back cannot return to Setup.
- **AppState / AppActions** is the only seam between screens and the app. `MainActivity` builds
  them from the repositories; UI tests pass an immutable `AppState` and a fake `AppActions` that
  records calls (plays, saved URLs, polling handles). Screens never touch `AppContainer`.
- **Polling**: S2 calls `actions.startPolling()` on entry and closes the returned `AutoCloseable`
  on exit, so ESPN polling runs only while S2 is visible ([schedule pipeline](/architecture/schedule-pipeline.md)).
- **ListMemory** (one per list screen, held in `AppContent` above the routes) keeps the selected
  tab and last focused item key while the screen is off-stack. `focusKey` is deliberately not
  Compose state: it is written on every focus move and must not recompose. On return the key is
  used only if the item still exists in the (possibly refreshed) list, else S2 falls back to the
  first non-Final card.
- **FocusGrid** is the shared 2-column lazy grid: stable item keys, focuses the initial key after
  first frame, scrolls to top (without stealing focus) on tab change, and pads by ~12 dp so the
  1.05 focus scale is not clipped by the grid.
- **PlayContext** travels with every `play()`: section, the tab (group) played from, that tab's
  ordered item keys, current key, and last feed side. CH up/down (M4) cycles within those keys and
  re-resolves them against a refreshed playlist by id, so it never needs the UI.
- **UpdateState** slots (S1 bar, S7 row) are fed by `UpdateChecker`; M3 ships
  `PlaceholderUpdateChecker` (never finds an update; a forced check reports "not available yet").
  M5 swaps in the real GitHub-release checker in `AppContainer` without UI changes.
