package com.bmiller.uzzu.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import com.bmiller.uzzu.ui.theme.Dimens
import com.bmiller.uzzu.ui.theme.UzzuColors
import com.bmiller.uzzu.ui.theme.UzzuShapes
import com.bmiller.uzzu.ui.theme.UzzuType

/**
 * tv-material3 Card with the Uzzu focus treatment: Surface fill in every state, 1 dp Outline at
 * rest, 3 dp Accent on focus (same [shape]), focus scale 1.05, no glow.
 */
@Composable
fun UzzuCard(
    onClick: () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
    container: Color = UzzuColors.Surface,
    content: @Composable () -> Unit,
) {
    val focused = Border(BorderStroke(Dimens.FocusBorder, UzzuColors.Accent), shape = shape)
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = CardDefaults.shape(shape = shape),
        colors = CardDefaults.colors(
            containerColor = container,
            contentColor = UzzuColors.TextPrimary,
            focusedContainerColor = container,
            focusedContentColor = UzzuColors.TextPrimary,
            pressedContainerColor = container,
            pressedContentColor = UzzuColors.TextPrimary,
        ),
        border = CardDefaults.border(
            border = Border(BorderStroke(Dimens.RestBorder, UzzuColors.Outline), shape = shape),
            focusedBorder = focused,
            pressedBorder = focused,
        ),
        scale = CardDefaults.scale(focusedScale = Dimens.FocusScale),
        interactionSource = interactionSource,
    ) { content() }
}

/** What a game card shows. Built from a [com.bmiller.uzzu.playlist.Game] by the games screen. Never a score. */
@Immutable
data class GameCardData(
    val state: GameCardState,
    /** Away team name, or the full title for [GameCardState.Ungrouped]. */
    val away: String,
    val home: String,
    val awayLogo: String?,
    val homeLogo: String?,
    /** League tag (`NHL`); empty for Ungrouped. */
    val league: String,
    /** Start time (`7:15 PM`), live period (`2ND 8:14`) or `FINAL`; empty when unknown. */
    val detail: String,
)

enum class GameCardState { Scheduled, Live, Final, Ungrouped }

/**
 * HANDOFF "Game card layout" (420×94 dp): two team rows (32 dp logo slot, 10 dp gap, name; the home
 * name is prefixed with a mono `@ `), a 100 dp right column with league tag above the time/period,
 * and the LIVE chip flush in the top-right notch. Final applies 0.4 alpha to the content only, so
 * a focused Final card keeps its full-strength border. Ungrouped is one title row with a TV icon.
 */
@Composable
fun GameCard(data: GameCardData, onClick: () -> Unit, modifier: Modifier = Modifier) {
    UzzuCard(onClick, UzzuShapes.Card, modifier.size(Dimens.GameCardWidth, Dimens.GameCardHeight)) {
        Box(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, end = 16.dp)
                    .then(if (data.state == GameCardState.Final) Modifier.alpha(UzzuColors.FinalContentAlpha) else Modifier),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.S5),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.S3)) {
                    if (data.state == GameCardState.Ungrouped) {
                        TitleRow(UzzuIcons.Tv, data.awayLogo) { Name(data.away) }
                    } else {
                        TitleRow(UzzuIcons.Shield, data.awayLogo) { Name(data.away) }
                        TitleRow(UzzuIcons.Shield, data.homeLogo) { HomeName(data.home) }
                    }
                }
                Column(
                    Modifier.width(100.dp).fillMaxHeight().padding(vertical = 13.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Bottom),
                    horizontalAlignment = Alignment.End,
                ) {
                    if (data.league.isNotEmpty()) Text(data.league, style = UzzuType.Mono, color = UzzuColors.TextSecondary, maxLines = 1)
                    if (data.detail.isNotEmpty()) Text(data.detail, style = UzzuType.MonoLarge, color = UzzuColors.TextPrimary, maxLines = 1, softWrap = false)
                }
            }
            if (data.state == GameCardState.Live) LiveChip(Modifier.align(Alignment.TopEnd), inCard = true)
        }
    }
}

@Composable
private fun TitleRow(placeholder: ImageVector, logo: String?, name: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LogoSlot(logo, placeholder)
        name()
    }
}

@Composable
private fun Name(text: String) {
    Text(text.uppercase(), style = UzzuType.BodyLarge, color = UzzuColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun HomeName(text: String) {
    val mono = UzzuType.MonoLarge
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontFamily = mono.fontFamily, fontWeight = mono.fontWeight, color = UzzuColors.TextSecondary, letterSpacing = 0.sp)) { append("@ ") }
            append(text.uppercase())
        },
        style = UzzuType.BodyLarge,
        color = UzzuColors.TextPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * HANDOFF "Channel row" (420×52 dp, Row shape): 2-digit index, 32 dp logo slot, name, optional
 * PLAYING chip.
 */
@Composable
fun ChannelRow(
    index: Int,
    name: String,
    logoUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    playing: Boolean = false,
) {
    UzzuCard(onClick, UzzuShapes.Row, modifier.size(Dimens.GameCardWidth, Dimens.ChannelRowHeight)) {
        Row(
            Modifier.fillMaxSize().padding(start = 10.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "%02d".format(index),
                style = UzzuType.Mono,
                color = UzzuColors.TextSecondary,
                modifier = Modifier.widthIn(min = 20.dp),
                maxLines = 1,
                softWrap = false,
            )
            LogoSlot(logoUrl, UzzuIcons.Tv)
            Text(
                name.uppercase(),
                style = UzzuType.BodyLarge,
                color = UzzuColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (playing) PlayingChip()
        }
    }
}
