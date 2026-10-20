---
type: Flow
title: Stream retry policy and the S6 error report
description: State machine that decides when a failing stream is retried, refreshed or handed to S6, and what S6 shows.
resource: app/src/main/java/com/bmiller/uzzu/player/RetryPolicy.kt
tags: [player, errors]
generated: {by: claude/opus-5.5, at: 2026-10-20T18:00:00Z}
---
There is one policy per tune session, and `start(url)` resets the budget. The clock and scheduler are
injected, so `RetryPolicyTest` runs in virtual time.

1. **Error** (a player error or a stall): retry the same URL after **2 s**, then after **5 s**.
2. **Refresh once**: the error is HTTP 401/403/404/410 on a `fastly.net` host (expired
   `expiry~hash` token), or the playlist is older than 10 min. The policy first re-fetches the
   playlist, re-resolves the same key + side and loads the new URL at once. One refresh per session.
   As built, the stale-playlist branch fires on *any* error, not only on 4xx (QA M45-03, open).
3. **Stall**: 15 s in BUFFERING without a rendered frame counts as an error.
4. **Healthy reset**: after **30 s** of playback, the next error starts with a fresh budget. A
   stream that runs for hours and hiccups now and then never reaches S6 because of old failures.
5. **Budget spent**: `exhausted` → `ErrorReport` → S6. If the refresh outcome is `ItemGone`, S6 says
   "This item is no longer in the playlist".

Late callbacks (refresh results, backoff runnables) carry a session counter. They are dropped once
a new tune starts or the policy is cancelled.

**Media3's internal 4xx retries are disabled** (`NoRetryOn4xx` in `PlayerController`). By default,
ExoPlayer retries a failed load several times before it reports the error. That hid a 403 for
seconds, then spent budget retrying a URL whose token had expired, and only a playlist refresh
fixes that. Errors other than 4xx keep Media3's default retry.

**S6 diagnostics** (`ErrorReport`, PLAN "Error screen contents"):
- ITEM, STREAM (host + masked path of the failing URL)
- HTTP_STATUS ("None" when there is no status; not shown in the failing color)
- PLAYER_ERR (Media3 `errorCodeName`, or `STALL`)
- ATTEMPTS (count + wall-clock seconds), PLAYLIST (fetch age + last refresh result), NETWORK

Every value goes through `SecretMasker`, which masks the account id and the Fastly `expiry~hash`
segment ([secret handling](/decisions/secret-handling.md)). S6 screenshots are safe to share.
