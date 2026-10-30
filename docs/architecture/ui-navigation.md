---
type: Component
title: UI navigation, state seam and focus restore
description: How screens are routed, how they get state and trigger effects, and how focus and tab survive a trip to the player.
resource: app/src/main/java/com/bmiller/uzzu/ui/AppContent.kt
tags: [ui, architecture]
generated: {by: claude/opus-5.5, at: 2026-10-20T23:00:00Z}
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
- **Focus on model updates**: S2/S3 watch whether focus is still inside the screen. When the
  focused tab or card vanishes (schedule poll, refresh), focus goes to the selected tab (if the
  tab row had it), the remembered item, or the first non-Final card / first row. Otherwise D-pad
  focus is silently lost.
- **Failure states**: a failed load shows `// NO DATA`, the masked reason and a focused Retry (not
  LOADING forever); a failed manual refresh with data on screen shows `SYNC FAILED · LAST OK <time>`.
- **FocusGrid** is the shared 2-column lazy grid: stable item keys, focuses the initial key after
  first frame, scrolls to top (without stealing focus) on tab change, and pads by ~12 dp so the
  1.05 focus scale is not clipped by the grid.
- **PlayContext** travels with every `play()`: section, the tab (group) played from, that tab's
  ordered item keys, current key, and last feed side. CH up/down cycles within those keys and
  re-resolves them against a refreshed playlist by id, so it never needs the UI.
- **O1 list and group pickers** (HANDOFF O1, 2026-10-29): `OverlayHost` holds an `OverlayView`
  (section + tab), starting on the playing item's section and group. The panel is one column: list
  picker (Live Games | Live Channels), group row, items, Exit. Select on a segment calls
  `switchSection`: the other section's initial tab (the playing group if the item is in it, else
  the first tab); an empty section is a no-op. Select on the group row swaps the items for the group
  list (`overlayGroups`: non-empty tabs + counts), which is modal and closed by Back. Every focus move
  that matters (Up from the first item, Up/Down between header rows, Left/Right in the picker, the
  group list's no-wrap Up/Down) is handled explicitly in the panel's key preview; Left/Right do
  nothing elsewhere. Focus stays on the segment after a list change and returns to the group row
  after a group change; the items scroll to the top. Focus moves to the group row *before* the
  group list leaves composition. A tune from either section builds its `PlayContext` from the
  listed section + tab. Player Back after leaving still returns to the route the player was opened
  from. Still needs a Fire TV check of the explicit focus paths.
- **UpdateState** slots (S1 bar, S7 row) are fed by `UpdateChecker`; `AppContainer` wires the
  [GitHub checker](/integrations/github-releases.md). UI tests use fakes. After Later/Install the
  S1 bar moves focus to the Live Games tile *before* the pressed button leaves composition, or D-pad focus is lost.
  While the bar shows its buttons, the S1 tiles have explicit `focusProperties { up = ... }` links
  to Install (Install → Refresh, Later → Settings): on the Fire TV, Up skipped the bar
  ([gotcha](/gotchas/compose-focus-device-vs-robolectric.md)).
