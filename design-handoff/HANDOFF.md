# Uzzu IPTV Player: design handoff

Build target: Jetpack Compose for TV (`androidx.tv:tv-material3`), Fire TV + Google TV, D-pad only.
This folder is the source of truth for the UI. Read it fully before writing UI code.

## What's in this folder

| Path | What it is |
| --- | --- |
| `HANDOFF.md` | This spec. Rules, screens, focus/back map, component mapping. |
| `theme/Color.kt`, `Type.kt`, `Shape.kt`, `Dimens.kt` | Drop-in theme files. Every token, already converted to dp/sp. Change the package line. |
| `design/*.dc.html` | The design source for each frame (HTML/CSS). Use for exact values when this doc is silent. Values there are **canvas px**. |

## Units: read this first

The design canvas is 1920×1080 px. A 1080p Fire TV / Google TV runs at xhdpi, which is 960×540 dp.
**Every px value in `design/` ÷ 2 = dp (or sp for text).** Example: 24 px body text = 12 sp. The theme files are already converted. Never copy a px number into Compose without halving it.

## Hard rules

1. Background is pure black. Content stays inside the safe area: 48 dp horizontal, 27 dp vertical (`Dimens.SafeH`, `Dimens.SafeV`).
2. **Yellow accent = focus border and LIVE marker only.** Nothing else is yellow.
3. **Pink (`Active`) = "currently on":** selected tab fill, the active Home/Away segment, the PLAYING chip, the tune banner's left bar. It's only ever a fill behind `TextPrimary` text. Never use pink as a text color (it fails contrast).
4. Focus state = scale 1.05 + 3 dp accent border. Unfocused cards and rows have a 1 dp violet `Outline` border. Use the same border shape in both states.
5. No glow, blur, gradients, shadows or parallax. The target is a low-end Fire TV Stick HD.
6. Animation: tv-material3's default focus scale only, plus a 150 ms fade for the player overlay and the tune banner. The tune banner stays up for 3 s.
7. Never show scores. Games show a start time or a live period only.
8. Final (ended) games: apply `alpha(0.4f)` to the card's **content**, not the Card. A focused Final card keeps its full-strength yellow border. The status text "FINAL" uses `TextPrimary` (keeps it at about 3.2:1 contrast at 40%).
9. Corners are cut, not rounded: `CutCornerShape` everywhere (see `Shape.kt`).
10. Minimum text 12 sp. Titles, names, tabs and buttons are UPPERCASE, condensed (Roboto width axis) with letter spacing. Body sentences stay sentence case.
11. Layout is vertical lists, or one horizontal tab row above a grid or list. No nested horizontal carousels.
12. Logos sit in a fixed 32 dp (or 48 dp) square slot: 1 dp `Outline` border, `Background` fill. Until a real logo loads, show the placeholder icon (team = shield, channel = TV) in `TextSecondary`.

## Fonts

One family, Roboto, plus Roboto Mono for times, tags and diagnostics.
- Bundle the **variable** Roboto (axes `wdth` 75–100, `wght` 100–900) and variable Roboto Mono from Google Fonts in `res/font/`. Rename them to `roboto_variable.ttf` and `roboto_mono_variable.ttf`. Fire OS's system font is Amazon Ember, not Roboto, so the app must bundle its own.
- Condensed styles use `FontVariation.width(75f..85f)`. `Type.kt` already does this. Variable fonts need API 26+; Fire OS 7 is API 28.

## Component mapping (tv-material3)

| Design element | Compose | Notes |
| --- | --- | --- |
| Home tiles (S1) | `Card` | 390×220 dp, `Shapes.Tile`, focused border 3 dp accent |
| Game card | `Card` | 420×94 dp, `Shapes.Card`. Layout below. |
| Channel row | `Card` (or `ListItem`) | 420×52 dp, `Shapes.Row` |
| Tab | `Tab` in `TabRow` | Selected = `Active` fill + `TextPrimary`. Unselected = transparent + `TextSecondary`. Focused = selected look + accent border + scale. Replace the default pill indicator with the `Active` fill. |
| Button | `Button` / `OutlinedButton` | `Shapes.Button`, `Surface` fill, 1 dp `Outline` unfocused, 3 dp accent focused |
| Feed picker | `Dialog` + tv `Surface` | `Shapes.Dialog`, `Scrim` backdrop |
| Overlay list item | `ListItem` | 2 lines, `Shapes.Row` |
| Home/Away toggle (overlay) | one `ListItem` | Its own row under the playing game. Select flips Home ↔ Away. Draw two segments inside; the active one gets the `Active` fill. |
| Playlist URL edit (S7) | `Dialog` + `androidx.compose.material3.OutlinedTextField` | tv-material3 has no text field |
| LIVE chip | `Box` | `Accent` fill, `Background` text, 5 dp square dot before the label |
| PLAYING chip | `Box` | `Active` fill, `TextPrimary` text |
| Key hints | `Row` of `Text` | Key name inside a 1 dp `Outline` box, mono |

Use `CardDefaults.scale(focusedScale = 1.05f)`, `CardDefaults.border(border = Border(BorderStroke(1.dp, Outline), shape = ...), focusedBorder = Border(BorderStroke(3.dp, Accent), shape = ...))`, and `CardDefaults.colors(containerColor = Surface, focusedContainerColor = Surface)`. Do the same for buttons and tabs. Check the parameter names against the tv-material3 version in the project.

### Game card layout (420×94 dp)

- Left: two stacked rows, each a 32 dp logo slot, 10 dp gap, team name (`bodyLarge`, 1 line, ellipsis). The home row's name is prefixed with `@ ` in mono `TextSecondary`.
- Right column (100 dp wide, bottom-aligned, 13 dp bottom padding): league tag (`mono`, `TextSecondary`) above the time or period (`mono`, 14 sp, `TextPrimary`).
- LIVE chip pinned to the **top-right corner**. Its top-right cut matches the card's cut, so it sits flush in the notch. Chip shape: `CutCornerShape(topEnd = 11.dp, bottomStart = 7.dp)`, height 22 dp.
- States: Scheduled (time), Live (LIVE chip + period, e.g. `TOP 5TH`, `2ND 8:14`, `Q3 4:12`), Final (`FINAL`, content at 40%), Ungrouped (single title row, TV icon, no league, no time).
- Sort by start time. A Final game stays in its sort position. Ungrouped goes last.

## Screens

Design frame files are in `design/`. Each has an initial focus and a Back target.

| # | Screen | File | Initial focus | Back |
| --- | --- | --- | --- | --- |
| S1 | Selection (home) | `Main.dc.html` | Live Games tile | Exit app |
| S2 | Games list | `Games.dc.html` | First card that isn't Final | S1 |
| S3 | Channels list | `Channels.dc.html` | First channel row | S1 |
| S4 | Feed picker (over S2 or the overlay) | `FeedPicker.dc.html` | Home feed | Close dialog |
| S5 | Player + tune banner | `Player.dc.html` | none (video) | Last channel; if none, the list it came from |
| O1 | Player overlay | `Overlay.dc.html` | The item that's playing | Close overlay |
| S6 | Error | `Error.dc.html` | Retry | The list it came from |
| S7 | Settings | `Settings.dc.html` | Playlist URL | S1 |

Notes per screen:
- **S1**: header (mono tag, title, LAST SYNC time, Refresh and Settings buttons). An "Update available" bar shows only when an update exists. Two tiles: games tile shows a LIVE chip + live count + games today; channels tile shows channel and group counts.
- **S2**: tabs All, MLB, NHL, NBA, NFL, NCAAF, Ungrouped. **Hide tabs with zero games.** A 2-column grid of game cards, 24 dp column gap, 16 dp row gap.
- **S3**: tabs All, League Networks, National Sports, College Sports, Regional Sports, International, News and Entertainment, Other. A 2-column grid of channel rows with a 2-digit index.
- **S5**: tune banner, bottom-left inside the safe area: `Scrim` fill, 3 dp `Active` bar on the left, two logo slots, a mono line (`// TUNED · HOME FEED · NFL`) and the title.
- **O1**: covers the left 40% (384 dp), `OverlayScrim` fill (75% black), 1 dp `Outline` line on its right edge. Content: mono tag, tab row, item list, the Home/Away row under the playing game, and "Exit to menu" pinned to the bottom.
- **S6**: `ERR // SIGNAL LOST` tag + title + plain summary line. Diagnostic block in mono with label/value lines: item + feed, host + masked path, HTTP status, player error code, attempts with timestamps, playlist age + last refresh, network. The failing values (HTTP status, player error) use `Error` color.
- **S7**: focusable rows for Playlist URL (masked), Refresh now, Check for updates. Read-only lines for app version and log file path. First-run setup reuses this styling: a single URL field and a Save button.

## Remote key map

| Context | Key | Action |
| --- | --- | --- |
| Lists | D-pad | Move focus |
| Lists | Select | Game: open feed picker. Channel: play. |
| Lists | Back | Up one level; on S1, exit |
| Player | Select | Open overlay |
| Player | Up / Down | Previous / next item in the current group |
| Player | Back | Toggle to last channel; if none, return to list |
| Overlay | Left / Right | Switch group tab |
| Overlay | Up / Down | Move through items (includes the Home/Away row and Exit) |
| Overlay | Select | Tune the focused item, or flip Home/Away on the toggle row |
| Overlay | Back | Close overlay |

## Not designed (decide in code, keep the same tokens)

Loading states (use a static `TextSecondary` "LOADING" mono tag, no spinner animation beyond the platform default), empty states, the URL edit dialog layout, and the first-run screen.
