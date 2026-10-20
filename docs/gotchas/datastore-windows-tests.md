---
type: Gotcha
title: File-backed DataStore fails in JVM tests on Windows
description: DataStore's atomic rename cannot overwrite an existing file on Windows JVMs.
resource: app/src/test/java/com/bmiller/uzzu/data/PlaylistRepositoryTest.kt
generated: {by: claude/opus-5.5, at: 2026-10-20T00:00:00Z}
---
**Symptom**: unit tests that write settings more than once throw an IOException about renaming the
temp file on Windows, while the same tests pass on Linux CI.

**Cause**: file DataStore writes a temp file and renames it over the old one. On a Windows JVM that
rename fails when the target already exists.

**Fix / avoid**: tests build `Settings` with an in-memory `DataStore<Preferences>` (the
`InMemoryPreferences` class in the test). Do not back test DataStores with a temp file.
