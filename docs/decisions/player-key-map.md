---
type: Decision
title: Player key map, long-press Back and the key-handler focus structure
description: Why CH and D-pad keys map the way they do, how long-press Back is detected on Fire TV, and the focus shape that keeps Back working.
resource: app/src/main/java/com/bmiller/uzzu/ui/player/PlayerScreen.kt
status: stable
tags: [player, input, focus]
generated: {by: claude/opus-5.5, at: 2026-10-20T18:00:00Z}
---
**CH+ = next, D-pad Up = previous** (CH- = previous, Down = next), within the current group, wrapping.
Lists run top to bottom, so Up means the item above. CH+ follows TV convention (next number).

**Back** (S5 with no overlay or S6): toggle to the last channel. If the slot is empty, return to the
list it came from. The slot is cleared on leaving the player ([PlaybackManager](/architecture/playback-manager.md)).

**Long-press Back** goes to S1. It is detected as the first `ACTION_DOWN` with `repeatCount >= 1`,
not via `isLongPress`/`onKeyLongPress`. The Fire TV remote's sequence, which the tests replay:
1. DOWN, repeat=0
2. DOWN, repeat=1, `FLAG_LONG_PRESS`
3. UP, `FLAG_CANCELED_LONG_PRESS`

The app exits to the menu on the first repeat. The short-press action fires only on an UP that
was neither long nor canceled. The trailing UP reaches S1 untracked, so it does not exit the app.
Every Back event is logged (`BACK action= repeat= flags=`), which lets you check other remotes.

**Focus-structure trap**: the key handler sits on a **non-focusable** parent Box. S5's focus target
is a focusable *sibling* of O1/S6, not their ancestor. With the handler on a focusable ancestor,
Compose consumed Back as a focus "exit" before the overlay's or error screen's `BackHandler` ran,
so Back did nothing in O1 and S6. Keep this shape when you change the player tree.
