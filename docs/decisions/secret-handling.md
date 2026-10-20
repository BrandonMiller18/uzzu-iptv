---
type: Decision
title: The playlist URL is a secret and never leaves the device
description: Why and how the account-bearing playlist URL is kept out of source, CI, logs and the UI.
resource: app/src/main/java/com/bmiller/uzzu/log/SecretMasker.kt
status: stable
tags: [security]
generated: {by: claude/opus-5.5, at: 2026-10-20T23:00:00Z}
---
**Context**: The repo is public. The playlist URL carries the user's email and uzzu account id,
every stream URL carries the account id, and Fastly away-feed URLs carry an `expiry~hash` token.

**Decision**:
- The URL is typed on the device at first run and stored only in app-private DataStore.
- Everything logged (logcat and the rotating `files/logs/app.log`) and every error shown in the UI
  goes through `SecretMasker`: URL userinfo, emails (including `%40`), runs of 7+ digits (last 4
  kept), query values and `expiry~hash` path segments are masked. Covered by `SecretMaskerTest`.
- Debug builds only (`BuildConfig.DEBUG`): on launch `MainActivity` adopts
  `files/debug_playlist_url.txt` when no URL is saved, then deletes the file. Developers `adb push`
  the URL file to `/data/local/tmp`, pipe it through `run-as com.bmiller.uzzu` into the app's
  `files/` dir, then delete the tmp copy. The URL never appears on a command line or in logs.
- The test fixture `sample.m3u` is a scrubbed copy of a real playlist: account id, email and Fastly
  hashes replaced with fakes, so parser tests use realistic data without leaking anything. Real
  playlists are never committed; `.gitignore` blocks `*.m3u`, `*.m3u8`, keystores and `signing.properties`.

- S7 shows the URL only through `maskPlaylistUrlForDisplay`: host, last path segment and query
  *names*; middle path segments, query values (except `type`/`output`), userinfo, emails and digit
  runs become dots. The S7 edit dialog never pre-fills the saved URL: the field starts empty, the
  masked value is shown as a `CURRENT:` line, and Save with an empty field cancels. Still, never
  screenshot while a real URL is being *typed* in it. Entering it on a release build (no
  `run-as`): [Fire TV testing](/guides/fire-tv-testing.md).

**Rejected**: a BuildConfig field or CI secret for the URL. It would be baked into public APKs.

If you ever see an unmasked secret in output, fix the masker. Do not copy the value anywhere.
