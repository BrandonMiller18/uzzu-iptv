---
type: Gotcha
title: D-pad cannot leave a text field in Robolectric tests
description: Robolectric key events have no InputDevice, so a focused text field keeps D-pad focus in JVM UI tests.
resource: app/src/test/java/com/bmiller/uzzu/ui/ScreensBehaviorTest.kt
tags: [testing, ui]
generated: {by: claude/opus-5.5, at: 2026-10-20T12:00:00Z}
---
**Symptom**: in a Compose UI test, sending DPAD_DOWN from the S0/S7 URL field never reaches the
Save button; focus stays in the field. On the Fire TV the same keys work.

**Cause**: Robolectric-synthesised key events have no `InputDevice`, so the text field does not
move focus on for D-pad keys.

**Fix**: in tests, reach buttons past a text field with
`performSemanticsAction(SemanticsActions.OnClick)` instead of D-pad keys. Verify real D-pad
traversal out of text fields on the device.
