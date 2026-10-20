---
type: Guide
title: Live Channels sub-group rules
description: How to add a channel rule to channel_groups.json and the ordering traps to avoid.
resource: app/src/main/assets/channel_groups.json
tags: [channels, classification]
generated: {by: claude/opus-5.5, at: 2026-10-20T06:30:00Z}
---
Rules are tried top to bottom; **first match wins**. Match is `exact`, `prefix` or `contains`,
case-insensitive on the trimmed display name. No match → Other.

**Adding a channel**
1. Prefer extending an existing rule's `values`. Use `exact` for short names that are prefixes of
   other channels (CBS, NBC, FOX, SNY, YES), `prefix` for families (FanDuel Sports …).
2. Check every rule *above* yours can't already claim the name, and that yours doesn't claim
   names meant for a rule below.
3. Add the name to `ChannelGroupsTest` ("order-sensitive names" if it depends on ordering),
   rebuild. The asset is bundled; there is no runtime update.

**Ordering traps (each is covered by a test)**
- Fox News / Fox Business sit in the first rule so National's FOX / Fox Sports rules, or any
  future broader "Fox" prefix, can never take them.
- NBC SNY is `exact` in Regional; NBC in National is `exact` too. Making NBC a prefix would pull
  NBC SNY and every NBC Sports regional into National.
- MSG is a prefix (catches MSG+). Never shorten it to "MS": MSNBC and MS NOW are News.
- International (Sky Sports …) precedes National, so "Sky Sports Golf" stays International
  rather than matching a Golf rule.
