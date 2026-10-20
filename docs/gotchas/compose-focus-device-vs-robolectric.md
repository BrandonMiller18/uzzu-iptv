---
type: Gotcha
title: Compose 2D focus search differs between Robolectric and Fire TV
description: A D-pad move that works in Robolectric UI tests can land elsewhere on the device, so important paths need explicit focusProperties.
resource: app/src/main/java/com/bmiller/uzzu/ui/home/HomeScreen.kt
tags: [ui, testing, device]
generated: {by: claude/opus-5.5, at: 2026-10-20T23:00:00Z}
---
**Symptom**: on the Fire TV, Up from the S1 Live Games tile skipped the "Update available" bar
and landed on Refresh in the header. A Robolectric test of the same move passed and could not
reproduce the bug.

**Cause**: Compose's geometric (2D) focus search picks the next target from layout bounds. The
result differs between the Robolectric JVM environment and the real device, so tests do not prove
on-device traversal for borderline layouts.

**Fix**: for paths that matter, set explicit links with `Modifier.focusProperties { up = ... }`
(S1: tiles → Install update; Install → Refresh, Later → Settings; default search when the bar is
hidden or downloading). Verify D-pad paths on the device; see also
[text-field focus in Robolectric](/gotchas/robolectric-textfield-focus.md).
