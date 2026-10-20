---
type: Flow
title: Single ExoPlayer lifecycle
description: One ExoPlayer per foreground session, reused for every tune.
resource: app/src/main/java/com/bmiller/uzzu/player/PlayerController.kt
tags: [player]
generated: {by: claude/opus-5.5, at: 2026-10-20T18:00:00Z}
---
- `PlayerController` (in AppContainer) owns the only ExoPlayer. `MainActivity.onStart` calls
  `start()` (create) and `onStop` calls `stop()` (release). Never build a second player.
- Tuning is `setMediaItem` + `prepare` on the same instance: fast switching and a single decoder
  on the Stick HD. HLS only, via `HlsMediaSource` over the shared OkHttp client.
- The current `PlayItem` survives `stop()`, and `start()` re-prepares it, so coming back from the
  home screen resumes the stream. `clear()` (Back to list) stops and forgets the item but keeps the player.
- `PlayerController` is a thin adapter: tunes, errors and retries are decided in
  [PlaybackManager](/architecture/playback-manager.md) and the [retry policy](/architecture/retry-policy.md).
- The UI observes `player: StateFlow<ExoPlayer?>` because the instance changes across onStop/onStart.
- Logged: tune, BUFFERING/READY, first frame with elapsed ms, the first HLS loads per tune (duration,
  size) to break down switch time, errors with HTTP code. Only host +
  path is logged and the logger masks the account id ([Secret handling](/decisions/secret-handling.md)).
