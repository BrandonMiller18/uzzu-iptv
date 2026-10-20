package com.bmiller.uzzu.ui.error

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.bmiller.uzzu.player.ErrorReport
import com.bmiller.uzzu.ui.components.InitialFocus
import com.bmiller.uzzu.ui.components.Rule
import com.bmiller.uzzu.ui.components.UzzuButton
import com.bmiller.uzzu.ui.components.UzzuIcons
import com.bmiller.uzzu.ui.theme.Dimens
import com.bmiller.uzzu.ui.theme.UzzuColors
import com.bmiller.uzzu.ui.theme.UzzuShapes
import com.bmiller.uzzu.ui.theme.UzzuType

/**
 * S6 Error (Error.dc.html): `ERR // SIGNAL LOST`, title, plain summary, the mono diagnostics block
 * (failing values in Error), Retry (initial focus, re-runs the full retry policy) and Back (to the
 * list the player came from).
 */
@Composable
fun ErrorScreen(report: ErrorReport, onRetry: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onBack)
    val retryFocus = remember { FocusRequester() }
    InitialFocus(retryFocus, report)

    Column(
        modifier.fillMaxSize().background(UzzuColors.Background).padding(horizontal = Dimens.SafeH, vertical = Dimens.SafeV).testTag("error"),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.S3)) {
            Text("ERR // SIGNAL LOST", style = UzzuType.Mono.copy(fontWeight = FontWeight.Bold), color = UzzuColors.Error)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(UzzuIcons.Warning, contentDescription = null, tint = UzzuColors.Error, modifier = Modifier.size(24.dp))
                Text("CAN'T PLAY THIS STREAM", style = UzzuType.Display, color = UzzuColors.TextPrimary)
            }
            Text(report.summary, style = UzzuType.Summary, color = UzzuColors.TextPrimary, modifier = Modifier.testTag("error:summary"))
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(UzzuColors.Surface, UzzuShapes.Panel)
                .border(BorderStroke(Dimens.RestBorder, UzzuColors.Outline), UzzuShapes.Panel),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("DIAGNOSTICS", style = UzzuType.Mono, color = UzzuColors.TextSecondary)
                Text("${report.lines.size} FIELDS", style = UzzuType.Mono, color = UzzuColors.TextSecondary)
            }
            Rule()
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 14.dp)) {
                for (line in report.lines) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.S6)) {
                        Text(line.label, style = UzzuType.Diagnostic, color = UzzuColors.TextSecondary, modifier = Modifier.width(130.dp))
                        Text(
                            line.value,
                            style = UzzuType.Diagnostic,
                            color = if (line.failing) UzzuColors.Error else UzzuColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("diag:${line.label}"),
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.S5)) {
            UzzuButton("Retry", onRetry, Modifier.focusRequester(retryFocus).testTag("error:retry"), height = 32.dp, horizontalPadding = 24.dp)
            UzzuButton("Back", onBack, Modifier.testTag("error:back"), height = 32.dp, horizontalPadding = 24.dp)
        }
    }
}
