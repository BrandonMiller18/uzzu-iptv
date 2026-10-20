---
type: Decision
title: Styling tv-material3 to the design handoff
description: How tv-material3 Card and Tab are bent to HANDOFF's focus treatment, and where the hard rules are enforced.
resource: app/src/main/java/com/bmiller/uzzu/ui/components
tags: [ui, design]
status: stable
generated: {by: claude/opus-5.5, at: 2026-10-20T23:00:00Z}
---
**Context**: HANDOFF hard rules: black ground; yellow `Accent` only for the focus border and the
LIVE marker; pink `Active` only as a fill behind TextPrimary for "currently on" (selected tab,
active feed, PLAYING, tune bar); focus = 1.05 scale + 3 dp Accent border, rest = 1 dp Outline,
same cut-corner shape; no glow, blur, gradients, shadows or parallax; never show scores; Final
cards fade content (alpha 0.4), not the Card. Design px are canvas px: divide by 2 for dp/sp.

**Decision**:
- All focusable surfaces go through `UzzuCard` / `UzzuButton`, which set tv-material3
  `CardDefaults` border/scale/colors explicitly (container colour identical in every state, so
  focus is signalled only by border + scale, never glow).
- tv `Tab` has no border or scale parameters, so `UzzuTabRow` draws them with modifiers:
  `graphicsLayer` scale, then `border`, then `background(Active)`. The pill indicator is
  disabled. Focusing a tab selects it, so focused implies selected; `focusRestorer` makes entry
  from below land on the selected tab. Tabs are 28 dp (`ControlHeight`); `UzzuTabRow` shrinks the
  tab padding (down to 3 dp) so every tab fits the safe area (S3's eight groups).
- Mono text: `UzzuType.MonoPlain` (0 tracking) for every mono line except `//` section tags and
  league tags. Compose trims line height on one-line `Text`, so fixed row heights (S6
  diagnostics) use a fixed-height, centered box, not `lineHeight`.
- The URL field is a foundation `BasicTextField` styled like the other focusables (no material3
  dependency). D-pad focus lands on a non-editing wrapper; OK enters the editor and opens the
  keyboard. Otherwise Fire OS's full-screen IME popped up on focus and hid the form.
- Borders are drawn over the content (tv Surface and `Modifier.border` paint after content), so
  the 3 dp focus border never changes layout; inner padding must leave room for it.
- Enforcement is by construction: colours only from `UzzuColors` (Accent is referenced only in
  the shared components and the URL field focus border), `GameCardData` has no score field, and
  `ScreensBehaviorTest` asserts no score text on S1/S2/S4. Roborazzi goldens in
  `app/src/test/snapshots` are the visual check (`./gradlew verifyRoborazziDebug`, local only).

**Rejected**: plain Compose foundation for cards/tabs. tv-material3 gives correct D-pad focus,
default focus-scale animation and click semantics for free.
