package com.bmiller.uzzu.ui.feed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.bmiller.uzzu.playlist.FeedSide
import com.bmiller.uzzu.playlist.Game
import com.bmiller.uzzu.ui.components.InitialFocus
import com.bmiller.uzzu.ui.components.KeyHint
import com.bmiller.uzzu.ui.components.MonoTag
import com.bmiller.uzzu.ui.components.UzzuCard
import com.bmiller.uzzu.ui.detailText
import com.bmiller.uzzu.ui.theme.Dimens
import com.bmiller.uzzu.ui.theme.UzzuColors
import com.bmiller.uzzu.ui.theme.UzzuShapes
import com.bmiller.uzzu.ui.theme.UzzuType

/** True when [game] needs the picker (both feeds exist); otherwise play [singleFeedSide] directly. */
fun Game.needsFeedPicker(): Boolean = homeFeed != null && awayFeed != null

fun Game.singleFeedSide(): FeedSide = if (homeFeed != null) FeedSide.HOME else FeedSide.AWAY

/**
 * S4 Feed picker (FeedPicker.dc.html): Dialog + tv Surface over a Scrim. Home then Away; initial
 * focus Home. Back closes. Shared by S2 and (M4) the player overlay.
 */
@Composable
fun FeedPicker(game: Game, onPick: (FeedSide) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        FeedPickerContent(game, onPick)
    }
}

/** The picker without the Dialog window (also used for screenshots). */
@Composable
fun FeedPickerContent(game: Game, onPick: (FeedSide) -> Unit) {
    val homeFocus = remember { FocusRequester() }
    InitialFocus(homeFocus)
    Box(Modifier.fillMaxSize().background(UzzuColors.Scrim), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.width(390.dp),
            shape = UzzuShapes.Dialog,
            colors = SurfaceDefaults.colors(containerColor = UzzuColors.Surface, contentColor = UzzuColors.TextPrimary),
            border = Border(BorderStroke(Dimens.RestBorder, UzzuColors.Outline), shape = UzzuShapes.Dialog),
        ) {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(Dimens.S6)) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.S2)) {
                    val detail = game.detailText()
                    MonoTag(if (detail.isEmpty()) "// SELECT SIGNAL" else "// SELECT SIGNAL · $detail")
                    Text(game.title.uppercase(), style = UzzuType.Title, color = UzzuColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.S4)) {
                    if (game.homeFeed != null) {
                        FeedChoice("A", "Home feed", "${game.homeName} broadcast", { onPick(FeedSide.HOME) }, Modifier.focusRequester(homeFocus).testTag("feed:home"))
                    }
                    if (game.awayFeed != null) {
                        val m = if (game.homeFeed == null) Modifier.focusRequester(homeFocus) else Modifier
                        FeedChoice(if (game.homeFeed != null) "B" else "A", "Away feed", "${game.awayName} broadcast", { onPick(FeedSide.AWAY) }, m.testTag("feed:away"))
                    }
                }
                KeyHint("BACK", "Cancel")
            }
        }
    }
}

@Composable
private fun FeedChoice(letter: String, title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier) {
    UzzuCard(onClick, UzzuShapes.Button, modifier.fillMaxWidth().height(52.dp), container = UzzuColors.Background) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(letter, style = UzzuType.Mono, color = UzzuColors.TextSecondary)
            Column {
                Text(title.uppercase(), style = UzzuType.Label.copy(fontSize = UzzuType.BodyLarge.fontSize, lineHeight = UzzuType.BodyLarge.lineHeight), color = UzzuColors.TextPrimary)
                Text(subtitle, style = UzzuType.Body, color = UzzuColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
