# Log

## 2026-10-24
- New logo: adaptive launcher icon (`mipmap-anydpi`, black background + foreground per density) and `@mipmap/tv_banner` at every density replace the old PNG icon and xhdpi-only banner. Source art in `design-handoff/brand/`.

## 2026-10-21
- Issue #4: O1 can list and tune the other section (Games/Channels switch, Left past the first tab); Back toggles across sections. Updated HANDOFF, PlaybackManager and UI navigation pages.
- Added the Android TV emulator guide for macOS (JDK 17 + SDK via Homebrew, API 31 arm64 TV AVD, fixture mode with a placeholder URL).

## 2026-10-20
- QA fix rounds + v1.0 acceptance: updated player/retry/lifecycle, UI navigation, styling, secret handling, updater, releasing, overview; added gotchas (device vs Robolectric focus, gh secrets from PS 5.1, release-build URL entry).
- M4/M5: added PlaybackManager, retry policy + S6, player key map, GitHub updater, releasing, CI gotcha; updated lifecycle, navigation, signing, Fire TV testing, performance, overview.
- M3: added UI navigation, tv-material3 styling, performance/bench, Robolectric text-field gotcha; fixture mode, S7 URL masking, wiring and overview updated.
- M2: added schedule pipeline, league dictionary, ESPN integration, channel rules; updated wiring and overview.
- Initial OKF bundle after M1 (skeleton, parser, setup, debug list, single-ExoPlayer playback, CI).
