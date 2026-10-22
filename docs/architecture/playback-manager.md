---
type: Component
title: PlaybackManager, channel keys and the merge rule
description: Who owns a tune session, how items are resolved against a refreshing playlist, and why channels need a synthetic key.
resource: app/src/main/java/com/bmiller/uzzu/player/PlaybackManager.kt
tags: [player, architecture]
generated: {by: claude/opus-5.5, at: 2026-10-20T23:00:00Z}
---
**Responsibilities** (main thread only, behind the `PlayerControls` interface the UI sees):
tune, CH up/down, H/A flip, pause, the one-slot last channel, the [retry policy](/architecture/retry-policy.md),
S6 state (`PlaybackState.error`), the silent playlist refresh when O1 opens (only if > 10 min old)
and the tune-banner counter. It drives the single ExoPlayer through `PlayerController`
([lifecycle](/architecture/playback-lifecycle.md)). The UI never touches ExoPlayer.

**Merge rule (PLAN)**: a playlist refresh never interrupts a working stream. Nothing is re-tuned
when the playlist changes. Every tune (and every S6 Retry) resolves the `PlayContext` against the
*current* playlist by **key + feed side**, so a new URL (e.g. a fresh Fastly token) is picked up
on the next tune. CH up/down re-reads the group's keys from the current playlist too. If the
playing item has left the group, it steps from its old position in the snapshot. Picking the
already-playing item from another O1 tab only swaps the CH up/down group (`setGroup`), no reload.

**Channel.key** (`id`, `id#2`, `id#3` … in playlist order, set in `M3uParser`): `Channel.id` is
tvg-id or the name, and the real playlist repeats ids. With duplicate ids, play contexts, CH
up/down and O1 rows could not tell channels apart, and duplicate lazy-list keys crash Compose.
The suffix follows playlist order, so the key stays stable across refreshes as long as the
provider keeps its order. Games key by the normalized `away|home` pair instead.

**Last-channel slot**: exactly one previous `PlayContext`, including its side. A tune that changes
the item or the side moves the current item into the slot. An H/A flip counts as a change (QA M45-12).
The slot is section-agnostic (game ↔ channel works); the way across sections is O1's section
switch, which tunes the other section's item with its own `PlayContext`. CH up/down always uses the
playing item's own group. `stop()` **clears the slot when you leave the player**. So after
re-entering from a list, Back returns to that list, not to a channel from an earlier session.

If the slot's item can no longer be resolved, `toggleLast` clears the slot and returns false, so
Back falls through to the list instead of doing nothing.

**Lifecycle**: `onStop` cancels the retry policy (the item is kept) and flushes the log; `onStart`
re-resolves the item against the current playlist and reloads it with a fresh budget (S6 stays
up if it was shown). See [lifecycle](/architecture/playback-lifecycle.md).
