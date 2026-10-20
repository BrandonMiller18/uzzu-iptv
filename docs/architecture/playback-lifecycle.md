---
type: Flow
title: Single ExoPlayer lifecycle
description: One ExoPlayer per foreground session, reused for every tune.
resource: app/src/main/java/com/bmiller/uzzu/player/PlayerController.kt
tags: [player]
generated: {by: claude/opus-5.5, at: 2026-10-20T23:00:00Z}
---
- `PlayerController` (in AppContainer) owns the only ExoPlayer. `MainActivity.onStart` calls
  `start()` (create) and `onStop` calls `stop()` (release). Never build a second player.
- Tuning is `setMediaItem` + `prepare` on the same instance: fast switching and a single decoder
  on the Stick HD. HLS only, via `HlsMediaSource` over the shared OkHttp client.
- The current item survives `stop()`. `start()` does **not** re-prepare on its own:
  PlaybackManager re-resolves the item against the current playlist on `onStart` (fresh URL,
  fresh retry budget), so a token that expired while in the background is not replayed. `onStop`
  also cancels the retry policy. `clear()` (Back to list) forgets the item but keeps the player.
- `PlayerController` is a thin adapter: tunes, errors and retries are decided in
  [PlaybackManager](/architecture/playback-manager.md) and the [retry policy](/architecture/retry-policy.md).
- The UI observes `player: StateFlow<ExoPlayer?>` because the instance changes across onStop/onStart.
- Logged: tune, BUFFERING/READY, first frame with elapsed ms, the first HLS loads per tune (duration,
  size) to break down switch time, errors with HTTP code. Only host +
  path is logged and the logger masks the account id ([Secret handling](/decisions/secret-handling.md)).
- `AppLogger`: masking and logcat stay on the caller's thread; file appends and rotation run on
  one background writer thread (batched), so main-thread player callbacks never wait on flash I/O
  and line order is kept. `flush()` waits for the queue; `onStop` and tests that read `app.log` call it.
