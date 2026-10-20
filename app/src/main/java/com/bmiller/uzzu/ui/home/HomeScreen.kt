package com.bmiller.uzzu.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.bmiller.uzzu.ui.components.InitialFocus
import com.bmiller.uzzu.ui.components.KeyHints
import com.bmiller.uzzu.ui.components.LiveChip
import com.bmiller.uzzu.ui.components.LoadingTag
import com.bmiller.uzzu.ui.components.MonoTag
import com.bmiller.uzzu.ui.SyncStatus
import com.bmiller.uzzu.ui.components.ScreenHeader
import com.bmiller.uzzu.ui.components.SyncTag
import com.bmiller.uzzu.ui.components.UzzuButton
import com.bmiller.uzzu.ui.components.UzzuCard
import com.bmiller.uzzu.ui.components.UzzuIcons
import com.bmiller.uzzu.ui.theme.Dimens
import com.bmiller.uzzu.ui.theme.UzzuColors
import com.bmiller.uzzu.ui.theme.UzzuShapes
import com.bmiller.uzzu.ui.theme.UzzuType
import com.bmiller.uzzu.update.UpdateState

/** Counts for the S1 tiles; null while the launch refresh is still running. */
@Immutable
data class HomeCounts(val liveNow: Int, val gamesToday: Int, val channels: Int, val groups: Int)

/** S1 focus targets, so returning from a list lands on the tile the user left from. */
enum class HomeFocus { GAMES, CHANNELS, REFRESH, SETTINGS }

/**
 * S1 Selection (Main.dc.html): header with LAST SYNC, Refresh and Settings; the optional update bar;
 * the Live Games and Live Channels tiles. Initial focus: Live Games tile. Back exits the app.
 */
@Composable
fun HomeScreen(
    counts: HomeCounts?,
    sync: SyncStatus,
    error: String?,
    update: UpdateState,
    onGames: () -> Unit,
    onChannels: () -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    onInstallUpdate: () -> Unit,
    onDismissUpdate: () -> Unit,
    onExit: () -> Unit,
    initialFocus: HomeFocus = HomeFocus.GAMES,
) {
    val requesters = remember { HomeFocus.entries.associateWith { FocusRequester() } }
    BackHandler(onBack = onExit)
    InitialFocus(requesters.getValue(initialFocus))

    Column(
        Modifier.fillMaxSize().background(UzzuColors.Background).padding(horizontal = Dimens.SafeH, vertical = Dimens.SafeV),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader(tag = "// IPTV TERMINAL", title = "Uzzu", titleStyle = UzzuType.Display.copy(letterSpacing = UzzuType.Display.letterSpacing * 2.5f)) {
            SyncTag(sync, Modifier.testTag("sync"))
            UzzuButton("Refresh", onRefresh, Modifier.focusRequester(requesters.getValue(HomeFocus.REFRESH)).testTag("refresh"), icon = UzzuIcons.Refresh)
            UzzuButton("Settings", onSettings, Modifier.focusRequester(requesters.getValue(HomeFocus.SETTINGS)).testTag("settings"), icon = UzzuIcons.Settings)
        }

        if (update is UpdateState.Available && !update.dismissed) {
            // Both buttons leave the bar (Later hides it, Install swaps them for progress): move focus to
            // the Live Games tile first, or focus is lost with the removed button and the D-pad goes dead.
            val games = requesters.getValue(HomeFocus.GAMES)
            UpdateBar(
                update,
                onInstall = { games.requestFocus(); onInstallUpdate() },
                onLater = { games.requestFocus(); onDismissUpdate() },
            )
        }
        if (error != null) {
            Text(error, style = UzzuType.Body, color = UzzuColors.Error, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }

        Row(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.S8, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tile(
                tag = "01 // GAMES",
                icon = UzzuIcons.Games,
                title = "Live Games",
                onClick = onGames,
                modifier = Modifier.focusRequester(requesters.getValue(HomeFocus.GAMES)).testTag("tile:games"),
            ) {
                if (counts == null) {
                    if (error != null) MonoTag("NO DATA") else LoadingTag()
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.S4)) {
                        if (counts.liveNow > 0) LiveChip()
                        Text("${counts.liveNow} live now".uppercase(), style = UzzuType.BodyLarge, color = UzzuColors.TextPrimary)
                    }
                    MonoTag("${counts.gamesToday} ${if (counts.gamesToday == 1) "game" else "games"} today")
                }
            }
            Tile(
                tag = "02 // CHANNELS",
                icon = UzzuIcons.Tv,
                title = "Live Channels",
                onClick = onChannels,
                modifier = Modifier.focusRequester(requesters.getValue(HomeFocus.CHANNELS)).testTag("tile:channels"),
            ) {
                if (counts == null) {
                    if (error != null) MonoTag("NO DATA") else LoadingTag()
                } else {
                    Text("${counts.channels} ${if (counts.channels == 1) "channel" else "channels"}".uppercase(), style = UzzuType.BodyLarge, color = UzzuColors.TextPrimary)
                    MonoTag("${counts.groups} ${if (counts.groups == 1) "group" else "groups"}")
                }
            }
        }

        KeyHints("OK" to "Open", "BACK" to "Exit app")
    }
}

@Composable
private fun Tile(
    tag: String,
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    details: @Composable () -> Unit,
) {
    UzzuCard(onClick, UzzuShapes.Tile, modifier.size(Dimens.TileWidth, Dimens.TileHeight)) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 27.dp, vertical = 23.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                MonoTag(tag, Modifier.weight(1f))
                Icon(icon, contentDescription = null, tint = UzzuColors.TextPrimary, modifier = Modifier.size(36.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.S4)) {
                Text(title.uppercase(), style = UzzuType.Display, color = UzzuColors.TextPrimary)
                details()
            }
        }
    }
}

/**
 * "Update available" bar (only when an update exists). While the APK downloads, the second line is a
 * mono progress line and the buttons are hidden; a failed attempt shows its reason in Error color.
 */
@Composable
private fun UpdateBar(update: UpdateState.Available, onInstall: () -> Unit, onLater: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .border(BorderStroke(Dimens.RestBorder, UzzuColors.Outline), UzzuShapes.Row)
            .background(UzzuColors.Surface, UzzuShapes.Row)
            .padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.S5),
    ) {
        Box(Modifier.background(UzzuColors.TextPrimary).padding(horizontal = 5.dp, vertical = 1.dp)) {
            Text("UPD", style = UzzuType.MonoPlain.copy(fontWeight = FontWeight.Bold), color = UzzuColors.Background)
        }
        Column(Modifier.weight(1f)) {
            Text("Update available: version ${update.version}".uppercase(), style = UzzuType.BodyLarge, color = UzzuColors.TextPrimary)
            when {
                update.progress != null -> Text(update.progress, style = UzzuType.MonoPlain, color = UzzuColors.TextSecondary, modifier = Modifier.testTag("update:progress"))
                update.error != null -> Text(update.error, style = UzzuType.Body, color = UzzuColors.Error, maxLines = 1, overflow = TextOverflow.Ellipsis)
                update.note != null -> Text(update.note, style = UzzuType.Body, color = UzzuColors.TextSecondary)
                else -> Text("You're on ${update.currentVersion}. Playback stops while it installs.", style = UzzuType.Body, color = UzzuColors.TextSecondary)
            }
        }
        if (update.progress == null) {
            UzzuButton("Install update", onInstall, Modifier.testTag("update:install"), container = UzzuColors.Background)
            UzzuButton("Later", onLater, Modifier.testTag("update:later"), container = UzzuColors.Background)
        }
    }
}
