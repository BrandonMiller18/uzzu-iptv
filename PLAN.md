---
title: Uzzu IPTV Player Plan
description: Initial implementation plan for the application. The plan has already been implemented. This file is retained in the repo for historical purposes only.
status: complete
---
# **Uzzu IPTV Player — Implementation Plan**

Oct 19, 2026 · @Brandon

## **Overview**

Build a single-user, sideloadable Android TV app that plays one uzzu.tv M3U playlist. The app groups live games by league with start times and game status, lists live channels in auto sub-groups, and allows fast in-player switching through an overlay. The user is the only user and is technical.

**Goals**

* Open to a selection screen: Live Games or Live Channels.  
* Live games grouped by league (MLB, NHL, NBA, NFL, NCAAF), with an Ungrouped fallback.  
* Each game shows its start time and live period/inning/quarter. No scores anywhere.  
* Home/Away feed choice every time a game is opened.  
* An OK-press overlay switches channel or group while the stream keeps playing behind it.  
* CH up/down within the current group. Back toggles to the last channel.  
* Error screen with diagnostic detail and a Retry button.

**Non-goals for v1**

* Favorites, search, voice, EPG/guide grid, multiview, scores.  
* Replays (MLB-Replays, NFL-Replays groups are ignored).  
* League configuration UI (leagues are hardcoded).  
* Quality, audio-track, or caption selectors; resume-last-channel on launch.  
* Multiple playlists, profiles, parental controls, any backend server.

## **Platforms and stack**

One APK targets both Fire OS and Google TV. The primary device is a Fire TV Stick HD (1st gen) on Fire OS 7.7.1.7, which is Android 9 (API 28\) and is the performance floor.

| Area | Choice |
| :---- | :---- |
| Language | Kotlin |
| UI | Jetpack Compose for TV (androidx.tv:tv-material) |
| Player | AndroidX Media3 ExoPlayer \+ media3-exoplayer-hls |
| HTTP | OkHttp (shared client for playlist, ESPN, Media3 via OkHttpDataSource) |
| JSON | kotlinx.serialization |
| Images | Coil, with memory \+ disk cache, logos downsampled to display size |
| Persistence | DataStore (playlist URL, last-fetch timestamp); in-memory model for everything else |
| DI | Manual / lightweight (Hilt is optional, not required) |
| Build | Gradle KTS, minSdk 28, targetSdk current stable, ABI armeabi-v7a \+ arm64-v8a |

**Manifest requirements**

* android.intent.category.LEANBACK\_LAUNCHER on the main activity, plus a 320x180 android:banner.  
* uses-feature android.software.leanback required=true; android.hardware.touchscreen required=false.  
* INTERNET, REQUEST\_INSTALL\_PACKAGES (for in-app updates).  
* Cleartext traffic is not required: every URL in the sample is HTTPS.

**No database.** The playlist is a few hundred entries. Parse it fresh on launch and hold the model in memory. Room is not needed.

## **Playlist data model and parsing**

The playlist is a plain \#EXTM3U file with 4 group-title values. Only two are used. No EPG header, no user-agent, and every \#EXTINF duration is 0.

| group-title | Approx. entries | Used | Notes |
| :---- | :---- | :---- | :---- |
| Live-Games | 12 (varies daily) | Yes | Mixed leagues, no league tag, two feeds per game |
| Live-Channels | \~96 | Yes | Flat list, URLs /{account}/{n}.m3u8 |
| MLB-Replays | \~150 | No | Ignored in v1 |
| NFL-Replays | \~100 | No | Ignored in v1 |

Any unknown group-title is ignored and logged.

**Parser rules**

1. Read line by line. Pair each \#EXTINF with the next non-comment, non-blank line as its URL.  
2. Parse attributes with a quoted-attribute regex: tvg-id, tvg-logo, group-title. The display name is the text after the last top-level comma.  
3. Tolerate CRLF, BOM, blank lines, missing attributes, and duplicate entries.  
4. A malformed entry is skipped and logged. It never fails the whole parse.

**Live-Games naming pattern**

{Away Team} @ {Home Team}-{H|A}

* Example: Anaheim Ducks @ New York Rangers-H.  
* \-H is the home broadcast, \-A is the away broadcast.  
* Regex: ^(.+?) @ (.+?)-(H|A)\$. A name that fails the regex becomes a single-feed game in Ungrouped, with its raw name as the title.  
* Collapse the two feeds into one Game keyed by the normalized away|home pair.  
* tvg-logo on each feed is that broadcasting team's logo. Use the home feed's logo for the home team and the away feed's for the away team.

**Model**

Game(id, awayName, homeName, league: League, homeFeed: Feed?, awayFeed: Feed?,  
     startTime: Instant?, status: GameStatus, playlistIndex: Int)  
Feed(url, logoUrl, side: HOME|AWAY)  
GameStatus \= Unknown | Scheduled | Live(periodLabel) | Final  
Channel(id, name, logoUrl, url, subGroup: ChannelGroup, playlistIndex: Int)  
League \= MLB | NHL | NBA | NFL | NCAAF | UNGROUPED  
**URL observations that matter**

* Home feeds and channels: https\://uzzu.tv/hls/{account}/g{n}.m3u8 or /{n}.m3u8. These look stable.  
* Away feeds: https\://tran03.global.ssl.fastly.net/{expiry}\~{hash}/perdias/{id}-away/chunks.m3u8. The {expiry} value (1792476833 in the sample) is a Unix timestamp near the download date, so away URLs expire. See the refresh rules under Playback.  
* Every URL contains the account ID. Treat the playlist as a secret (see Repo hygiene).

## **League classification and schedule matching**

League comes from team names, because the playlist carries no league tag. A bundled dictionary classifies the game, and ESPN's scoreboard supplies the start time and status. Anything that fails both lands in Ungrouped.

**Step 1: normalize names**

Lowercase, strip punctuation, collapse whitespace, and fix known quirks before lookup:

* St.Louis → st louis  
* Athletics (no city) → matches the athletics alias  
* Keep full names. Nicknames alone collide across leagues: Giants, Cardinals, Rangers, Kings, Panthers, Jets.

**Step 2: dictionary lookup (offline, bundled)**

* Ship teams.json with every NFL, MLB, NHL, and NBA team: full display name, city, nickname, aliases.  
* Generate it at build time from ESPN's teams endpoints. It is not fetched at runtime.  
* Rule: a game's league is set only when away and home both resolve to the same league.  
* Logo IDs (t1–t32 NFL, t33\+ MLB) are not used. The sample breaks that pattern with t125 and t519.

**Step 3: ESPN scoreboard match (runtime)**

* Endpoint: https\://site.api.espn.com/apis/site/v2/sports/{sport}/{league}/scoreboard?dates={yyyyMMdd}.  
* Paths: baseball/mlb, hockey/nhl, basketball/nba, football/nfl, football/college-football.  
* For NCAAF add groups=80 (all FBS) so non-Top-25 games are returned.  
* Fetch today and yesterday (device local date) for each league in parallel. Yesterday catches late games that run past midnight.  
* Match on the normalized away|home pair against each event's competitor displayName, shortDisplayName, and location.  
* On a match, set startTime from event.date. Set status from status.type.state: pre → Scheduled, in → Live with status.type.shortDetail ("Top 5th", "2nd 8:14", "Q3 4:12"), post → Final.

**NCAAF (best effort)**

* NCAAF has no bundled dictionary because the team set is too large and naming is inconsistent.  
* Any game not classified by Step 2 is tried against the NCAAF scoreboard in Step 3\. A match sets the league to NCAAF.  
* Ambiguous schools (Miami FL vs Miami OH, USC, etc.) match only when both teams agree with a single event.

**Ungrouped fallback**

A game that fails every step goes to the Ungrouped league, keeps its playlist order, and shows no time.

**Ended games**

Status Final does not move the game. Sort order stays start time, then playlist order.

**Polling**

* Re-fetch ESPN every 60 s while a games list or the overlay is visible. Stop when hidden.  
* A failed ESPN call keeps the last known values and never blocks the UI.

**Risk:** these ESPN endpoints are public but undocumented, and can change without notice. Wrap them behind a ScheduleSource interface so the MLB Stats API (statsapi.mlb.com) or NHL's api-web.nhle.com can be swapped in per league.

## **Live Channels sub-groups**

The \~96 channels are split into 6 fixed sub-groups using a bundled channel\_groups.json rule file. Rules are ordered and the first match wins. Matching uses case-insensitive substring or exact name against the channel's display name. Order within a sub-group is playlist order.

| Sub-group | Rule (examples from the current playlist) |
| :---- | :---- |
| League Networks | NFL Network, Red Zone, Red Zone2, NBA TV, NHL Network, MLB Network |
| National Sports | ESPN, ESPN 2, ESPNU, Fox Sports 1, Fox Sports 2, TBS, USA Network, CBS, TruTV East, Golf Channel, Tennis Channel, TVG Network, beIN USA |
| College Sports | BTN Network, ACCN, SEC Network, PAC 12 Network |
| Regional Sports | FanDuel Sports Network\*, NBC Sports\*, SportsNet\*, Marquee, Root Sports, Yes Network, MASN, NESN\*, Space City Home, AT\&T Pittsburgh, Altitude, MSG\*, Monumental, NBC SNY |
| International | TSN \*, Star Sports\*, Sky Sports\*, Willow, SuperSport, Astro Cricket, Premier Sports |
| News and Entertainment | Fox News, Fox Business, CNN News, CNBC, MSNBC, Weather Channel, HGTV, History, Discovery\*, Animal Planet, Nickelodeon, Cartoon Network, A\&E |

A channel matching no rule goes to **Other**. Empty sub-groups are hidden. The rules file is a plain bundled asset, so new channels need only a JSON edit and rebuild.

## **Screens and navigation**

The app has 8 screens and 1 overlay. Every screen must be fully usable with the Fire TV remote's D-pad, Select, Back, Play/Pause, and Menu keys.

![screen navigation · 8 screens, 1 overlay][image1]

screen navigation · 8 screens, 1 overlay

Games route through the feed picker; channels play directly. The overlay and error screen both hang off the player.

| \# | Screen | Purpose | Functional requirements |
| :---- | :---- | :---- | :---- |
| S0 | First-run setup | Enter the playlist URL once | Validate and save the URL to DataStore; show validation errors |
| S1 | Selection (home) | Choose Live Games or Live Channels | Live-game count, refresh, settings entry, last-refreshed time, update prompt |
| S2 | Games list | Browse games | League tabs: All first, then MLB, NHL, NBA, NFL, NCAAF, Ungrouped; zero-game tabs hidden |
| S3 | Channels list | Browse channels | Sub-group tabs: All first; empty tabs hidden |
| S4 | Feed picker | Choose Home or Away feed | Opens from S2 or the overlay; skipped when only one feed exists |
| S5 | Player | Full-screen playback | Channel banner on tune |
| O1 | Player overlay | Switch without stopping playback | Live Games / Live Channels picker, group row (opens a group list), item list, H/A toggle on the playing game, "Exit to menu" item |
| S6 | Error | Show failure and diagnostics | See Error screen contents below; Retry, Back |
| S7 | Settings | Maintenance | Playlist URL (edit), Refresh now, Check for updates, app version, log path |

**Game data rules**

* Start time is formatted in device local time ("7:10 PM"). Live games show the ESPN live label ("Top 5th", "Q3 4:12") and are marked live.  
* Final games are marked Final and stay in their sort position.  
* No scores, ever. The UI must not render any score field even if the API returns one.

**Remote key map**

| Context | Key | Action |
| :---- | :---- | :---- |
| Lists | D-pad | Move focus |
| Lists | Select | Open game (feed picker) or play channel |
| Lists | Back | Up one level; on S1, exit app |
| Player | Select | Open overlay O1 |
| Player | D-pad up / down | Previous / next item in the current group (wraps) |
| Player | Back | Toggle to the last channel; if none, return to the list it came from |
| Player | Long-press Back | Exit to S1 (if the remote reports it) |
| Player | Play/Pause | Pause / resume (live edge on resume) |
| Overlay | D-pad up / down | Move focus through list picker, group row, items and Exit |
| Overlay | D-pad left / right | Move between Live Games and Live Channels in the list picker only |
| Overlay | Select | List segment: show that list. Group row: open the group list. Item: tune it (feed picker for games) |
| Overlay | Back | Close the group list if open, else close overlay |

**Navigation rules**

* "Current group" \= the tab the user played from (e.g. NHL, or Regional Sports). CH up/down never leaves it.  
* Games in the CH up/down cycle keep the same feed side that was last chosen, falling back to the available one. The feed picker is not shown on CH up/down.  
* The "last channel" slot holds exactly one previous item, including its feed side.  
* Opening the overlay focuses the currently playing item.

## **Playback, refresh, and errors**

Away-feed URLs carry an expiry token, so the playlist must be fresh before every tune. The player re-fetches the playlist once on an auth-type failure before it shows an error.

**Playlist refresh**

* On every app launch, before S1 renders its counts. Show cached-in-memory data only within the same process.  
* Manual: Refresh button on S1 and in Settings.  
* Silent: when the overlay opens and the playlist is older than 10 minutes.  
* Merge rule after a refresh: if the playing item still exists (same normalized key), keep playing and swap its URL only on the next tune. Never interrupt a working stream because of a refresh.

**Player setup**

* One ExoPlayer instance for the app lifetime, attached to a PlayerView/SurfaceView. Reuse it on every tune: setMediaItem \+ prepare, no re-creation.  
* HlsMediaSource via OkHttpDataSource. Default user agent (none required by uzzu).  
* Start at the live edge. Use Media3 defaults for live offset; do not tune buffers in v1.  
* Keep the screen on while playing (FLAG\_KEEP\_SCREEN\_ON).  
* Release the player in onStop; rebuild in onStart.

**Retry policy**

1. On PlaybackException, retry the same URL up to 2 times with 2 s and 5 s backoff.  
2. If the error is HTTP 401/403/404/410 on a fastly.net host, or on any URL after a playlist older than 10 minutes: re-fetch the playlist, resolve the same game/side, and retry once with the new URL.  
3. If the stream stalls (no rendered frame for 15 s while STATE\_BUFFERING), treat it as an error.  
4. After the attempts are exhausted, show S6.

**Error screen (S6) contents**

* Plain-language summary line ("Stream returned 403 after playlist refresh").  
* Item name and feed side.  
* Stream host and URL path, with the account ID masked (/hls/\*\*\*\*\*\*7220/g4678.m3u8).  
* HTTP status (if any) and Media3 errorCodeName (e.g. ERROR\_CODE\_IO\_BAD\_HTTP\_STATUS).  
* Attempts made, with timestamps.  
* Playlist age and last refresh result.  
* Network state (connected / transport type).  
* Buttons: Retry (re-runs the full policy), Back.

**Logging**

* Every error, retry, refresh, and ESPN failure is appended to files/logs/app.log in the app's internal storage.  
* Rotate at 1 MB, keep 3 files.  
* The log path is shown in Settings. Pull it with adb shell run-as \<pkg\> cat files/logs/app.log.  
* Mask the account ID in logs the same way as on screen.

## **Design handoff**

Visual design (layout, theme, typography, color tokens, focus styling, component states, animation, logo sizing) comes from the Claude Design handoff. Implement each screen from its handoff frame using androidx.tv.material3 components. Where this plan and the handoff disagree on presentation, the handoff wins; where they disagree on behavior, this plan wins.

## **Build, distribution, and repo hygiene**

The repo is public on GitHub. Releases ship as signed APKs on GitHub Releases, and the app checks for updates itself.

**Repo hygiene (public repo)**

* The playlist URL is entered at runtime and stored in DataStore. It never appears in source, tests, CI, or logs in clear text.  
* The sample index.m3u is converted to test/fixtures/sample.m3u with the account ID, email, and Fastly hashes replaced by fakes. The original file is never committed.  
* .gitignore covers \*.m3u, \*.m3u8, local.properties, keystores, and signing.properties.  
* Release keystore and passwords live only in GitHub Actions secrets.

**CI (GitHub Actions)**

* On push: build, unit tests (parser, name normalization, classification, ESPN matching against recorded JSON fixtures), lint.  
* On tag v\*: build a signed release APK and attach it to a GitHub Release named after the tag.

**In-app updates**

* On launch (at most once per 24 h) and from Settings, call https\://api.github.com/repos/{owner}/{repo}/releases/latest.  
* If tag\_name is newer than BuildConfig.VERSION\_NAME, show an "Update available" prompt on S1.  
* On accept, download the .apk asset to the app cache and launch the installer with a FileProvider URI.  
* First run on Fire OS asks the user to allow "Install unknown apps" for this app. That prompt is expected.

**First install**

* Fire TV: enable Developer Options → Apps from Unknown Sources (or install-unknown-apps for Downloader), then install via the Downloader app using the GitHub release URL, or adb connect \<ip\> \+ adb install.  
* Google TV: same APK. Allow unknown sources for the installer app.

**Google TV verification (deadline, not a v1 blocker)**

Google requires apps installed on certified Android devices to be registered to a verified developer, starting September 30, 2026 in Brazil, Indonesia, Singapore, and Thailand, with global rollout in 2027\. A free limited-distribution account covers up to 20 devices without a government ID. Register the package name and signing key there before the US rollout. Fire TV ships without the Play Store and is expected to be unaffected. Sources: [9to5Google](https://9to5google.com/2026/03/30/android-developer-verifier-app/), [AlternativeTo](https://alternativeto.net/news/2026/3/google-details-new-24-hour-process-to-sideload-android-apps-from-unverified-developers), [AFTVnews](https://www.aftvnews.com/?p=45313).

## **Milestones and acceptance criteria**

The work runs in 5 milestones, in order. Each one ends with an APK installed on the Fire TV Stick HD.

1. **M1 Skeleton and playback.** Project setup, manifest, first-run URL entry, playlist fetch, parser, a plain debug list of every Live-Games and Live-Channels entry, single ExoPlayer playback.  
   * Done when: a home feed, an away feed, and a channel each play on the Fire TV Stick HD.  
   * Done when: parser unit tests pass on the scrubbed fixture, including H/A pairing and malformed lines.  
2. **M2 Classification and schedule.** teams.json generator, normalization, league classification, ESPN ScheduleSource, polling, channel sub-group rules.  
   * Done when: every game in the fixture lands in the correct league.  
   * Done when: recorded ESPN JSON fixtures yield the correct start time and Scheduled/Live/Final state.  
   * Done when: an unknown matchup lands in Ungrouped.  
3. **M3 Screens.** S1, S2, S3, S4, S7 built in Compose for TV from the Claude Design handoff.  
   * Done when: every list is reachable by D-pad alone, with the initial focus and Back target from the handoff and key map.  
   * Done when: zero-game league tabs are hidden.  
   * Done when: no score is rendered anywhere.  
4. **M4 Player experience.** Overlay O1, CH up/down within group, last-channel on Back, long-press Back, feed toggle, retry policy, error screen S6, logging.  
   * Done when: switching channel from the overlay starts the new stream in under 3 s on a home-network connection.  
   * Done when: an expired away-feed URL recovers through a playlist re-fetch without user action.  
   * Done when: a forced 404 shows S6 with HTTP status, error code name, and a masked URL.  
5. **M5 Release pipeline.** CI build and test, signed release on tag, in-app update check and install.  
   * Done when: tagging v1.0.0 produces a release, and the app installed at v0.9 offers and installs v1.0.0.  
   * Done when: the same APK installs and plays on the Google TV device.

**Performance budget (Fire TV Stick HD)**

* Cold start to S1 with counts: under 4 s on home Wi-Fi.  
* List scrolling holds focus movement without visible lag. Test with the full \~96-channel list.  
* Memory: no growth across 50 consecutive channel switches (check with adb shell dumpsys meminfo).

## **Risks and open items**

The biggest risk is the undocumented ESPN API. Everything that depends on it degrades to Ungrouped plus playlist order instead of failing.

| Risk | Impact | Mitigation |
| :---- | :---- | :---- |
| ESPN endpoints change or rate-limit | No start times or status | ScheduleSource interface; per-league fallback to MLB Stats API / NHL API; 60 s polling only while visible |
| uzzu changes the \-H / \-A naming or group titles | Games not paired or not shown | Regex miss → single-feed Ungrouped game; unknown groups logged |
| Fastly token lifetime shorter than expected | Away feeds fail mid-session | Re-fetch-and-retry on 4xx; silent refresh on overlay open |
| NCAAF names don't match ESPN | NCAAF games in Ungrouped | Accepted best effort; log misses to tune aliases later |
| Compose for TV performance on the Stick HD | Laggy focus movement Avoid blur, shadows, and nested carousels in implementation; test on device from M1 |
| Fire TV remote doesn't report long-press Back | No quick exit from player | "Exit to menu" in the overlay is the guaranteed path |
| Google TV developer verification reaches the US | Sideload blocked on Google TV | Register a free limited-distribution account before the rollout |

**Open items**

* ☐ Capture a playlist snapshot on a day with NBA and NCAAF games listed, scrub it, and add it as a second fixture before M2 ends.  
* ☐ Confirm the long-press Back keycode on the Fire TV Stick HD remote during M4.  
* ☐ Record ESPN scoreboard responses for each league (pre, in, post states) as test fixtures.

[image1]: <data:image/png;base64,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>