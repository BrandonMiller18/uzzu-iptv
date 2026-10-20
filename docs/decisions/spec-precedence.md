---
type: Decision
title: PLAN.md wins on behavior, HANDOFF.md wins on presentation
description: How to resolve conflicts between the product plan and the design handoff.
status: stable
generated: {by: claude/opus-5.5, at: 2026-10-20T00:00:00Z}
---
**Context**: Two specs: `PLAN.md` (product behavior, milestones) and `design-handoff/HANDOFF.md`
(Claude Design UI handoff), plus the rules in `AGENTS.md`.

**Decision**: Where they disagree on presentation (layout, tokens, typography, focus styling,
animation, logo sizing) the handoff wins. Where they disagree on behavior (data, playback,
navigation semantics) PLAN.md wins. This is stated in PLAN.md's visual design section.

**Working rules** (from AGENTS.md): read HANDOFF.md fully before UI work; canvas px in
`design-handoff/design/*.dc.html` divided by 2 gives dp/sp; yellow only for focus and LIVE, pink
only as an active-state fill; no gradients, shadows or blur (also a performance rule for the
Stick HD). Record every deviation per screen.
