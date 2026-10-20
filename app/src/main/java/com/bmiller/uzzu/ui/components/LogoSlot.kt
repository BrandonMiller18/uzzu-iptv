package com.bmiller.uzzu.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Icon
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import com.bmiller.uzzu.ui.theme.Dimens
import com.bmiller.uzzu.ui.theme.UzzuColors

/**
 * HANDOFF rule 12: fixed square slot (32 or 48 dp), 1 dp Outline border, Background fill. The
 * placeholder icon (team = shield, channel = TV) in TextSecondary shows until the logo has loaded,
 * and stays if it fails. Coil decodes at the slot's pixel size and caches in memory + on disk.
 */
@Composable
fun LogoSlot(
    url: String?,
    placeholder: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = Dimens.LogoSmall,
) {
    Box(
        modifier.size(size).background(UzzuColors.Background).border(Dimens.RestBorder, UzzuColors.Outline),
        contentAlignment = Alignment.Center,
    ) {
        var loaded by remember(url) { mutableStateOf(false) }
        if (!loaded) {
            Icon(placeholder, contentDescription = null, tint = UzzuColors.TextSecondary, modifier = Modifier.size(size / 2))
        }
        if (!url.isNullOrBlank()) {
            val context = LocalContext.current
            val px = with(LocalDensity.current) { size.roundToPx() }
            val request = remember(url, px) { ImageRequest.Builder(context).data(url).size(px).build() }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onState = { loaded = it is AsyncImagePainter.State.Success },
                modifier = Modifier.size(size).padding(Dimens.S1),
            )
        }
    }
}
