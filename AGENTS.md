# Uzzu IPTV

Sideloaded Android TV / Fire TV app for one uzzu.tv M3U playlist. Kotlin, Compose for TV, Media3,
single `:app` module, package `com.bmiller.uzzu`. v1 (milestones M1–M5) is shipped; work now is fixes
and small features. Start with `docs/index.md` and read only the pages your task touches.

## Commands
- JDK 17 is required. Set `JAVA_HOME` if the system `java` is older.
- Gate before you call a change done: `./gradlew testDebugUnitTest lintDebug assembleDebug`
- Single test: `./gradlew testDebugUnitTest --tests 'com.bmiller.uzzu.playlist.M3uParserTest'`
- Screenshots: `./gradlew verifyRoborazziDebug` checks against goldens; `recordRoborazziDebug`
  re-records them after an intended visual change. The goldens were recorded on Windows, so the
  compare only holds on that OS. CI renders but does not compare.

## Specs
- `PLAN.md` wins on behavior. `design-handoff/HANDOFF.md` wins on presentation. Read the relevant
  HANDOFF section before any UI change.
- Canvas px in `design-handoff/design/*.dc.html` ÷ 2 = dp/sp. Tokens live in `ui/theme`; use them,
  don't hardcode values.
- Hard rules: yellow only for focus and LIVE, pink only as an active-state fill, no gradients,
  shadows or blur (also a performance rule for the Fire TV Stick HD, the device floor).
- No scores anywhere in the UI.
- After a UI change, list every deviation from the spec and why.

## Rules that are easy to break
- **The playlist URL is a secret.** Never commit, log, print or screenshot it. Everything logged or
  shown goes through `SecretMasker`. The only playlist allowed in the repo is the scrubbed
  `app/src/test/resources/fixtures/sample.m3u`.
- Debug-only hooks (fixture mode, forced 404, `suppress_errors`) must stay behind `BuildConfig.DEBUG`.
  CI fails if their strings reach the release dex.
- compile/targetSdk is 36. Libraries are pinned to the last versions that build against it. Don't
  bump them (see `docs/decisions/pinned-sdk36-libraries.md`).
- D-pad focus that passes in Robolectric can still be wrong on the device. Use explicit
  `focusProperties` for paths that matter, and say when a focus change still needs a device check.

## Docs
`docs/` holds the architecture, decisions, guides and gotchas. When you change behavior covered
there, update that page and add a dated line to `docs/log.md`. Add a gotcha page when you hit
something non-obvious.
