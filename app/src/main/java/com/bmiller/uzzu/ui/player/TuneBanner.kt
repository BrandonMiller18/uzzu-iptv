package com.bmiller.uzzu.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.bmiller.uzzu.player.Resolved
import com.bmiller.uzzu.ui.components.LogoSlot
import com.bmiller.uzzu.ui.components.MonoTag
import com.bmiller.uzzu.ui.components.UzzuIcons
import com.bmiller.uzzu.ui.theme.Dimens
import com.bmiller.uzzu.ui.theme.UzzuColors
import com.bmiller.uzzu.ui.theme.UzzuShapes
import com.bmiller.uzzu.ui.theme.UzzuType

/**
 * S5 tune banner (Player.dc.html): Scrim fill, banner cut, 3 dp Active bar on the left, logo
 * slots (two for games, one for channels), the mono `// TUNED · …` line and the title.
 */
@Composable
fun TuneBanner(item: Resolved, modifier: Modifier = Modifier) {
    Row(
        modifier
            .testTag("banner")
            .widthIn(max = 620.dp)
            .height(IntrinsicSize.Min)
            .background(UzzuColors.Scrim, UzzuShapes.Banner)
            .padding(end = 20.dp, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(Dimens.S5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(3.dp).fillMaxHeight().background(UzzuColors.Active))
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.S3)) {
            for (logo in item.logos) LogoSlot(logo, if (item.isChannel) UzzuIcons.Tv else UzzuIcons.Shield)
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            MonoTag(item.bannerLine)
            Text(item.title.uppercase(), style = UzzuType.Title, color = UzzuColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Not designed: a static paused marker in the banner's styling. */
@Composable
fun PausedTag(modifier: Modifier = Modifier) {
    Row(
        modifier.testTag("paused").background(UzzuColors.Scrim, UzzuShapes.Banner).padding(end = 14.dp).height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(Dimens.S5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(3.dp).fillMaxHeight().background(UzzuColors.Active))
        MonoTag("// PAUSED · PLAY RESUMES LIVE", Modifier.padding(vertical = 8.dp))
    }
}
