package com.bmiller.uzzu.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.bmiller.uzzu.ui.components.InitialFocus
import com.bmiller.uzzu.ui.components.KeyHints
import com.bmiller.uzzu.ui.components.Rule
import com.bmiller.uzzu.ui.components.ScreenHeader
import com.bmiller.uzzu.ui.components.UzzuCard
import com.bmiller.uzzu.ui.setup.FormWidth
import com.bmiller.uzzu.ui.setup.PlaylistUrlForm
import com.bmiller.uzzu.ui.theme.Dimens
import com.bmiller.uzzu.ui.theme.UzzuColors
import com.bmiller.uzzu.ui.theme.UzzuShapes
import com.bmiller.uzzu.ui.theme.UzzuType

/** 28 px / 700 / width 80 row titles (settings rows, feed choices). */
private val RowTitle = UzzuType.Label.copy(fontSize = UzzuType.BodyLarge.fontSize, lineHeight = UzzuType.BodyLarge.lineHeight)

/**
 * S7 Settings (Settings.dc.html): Playlist URL (masked; Select opens the edit dialog), Refresh now,
 * Check for updates, then read-only app version and log path. Initial focus: Playlist URL. Back → S1.
 */
@Composable
fun SettingsScreen(
    maskedUrl: String,
    currentUrl: String?,
    syncLine: String,
    updateLine: String,
    appVersion: String,
    logPath: String,
    onSaveUrl: suspend (String) -> String?,
    onRefresh: () -> Unit,
    onCheckUpdates: () -> Unit,
    onBack: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    val urlFocus = remember { FocusRequester() }
    BackHandler(onBack = onBack)
    if (!editing) InitialFocus(urlFocus)

    Column(
        Modifier.fillMaxSize().background(UzzuColors.Background).padding(horizontal = Dimens.SafeH, vertical = Dimens.SafeV),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        ScreenHeader("03 // SYSTEM", "Settings")
        Column(Modifier.width(FormWidth), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            SettingsRow("Playlist URL", maskedUrl, { editing = true }, Modifier.focusRequester(urlFocus).testTag("settings:url"), trailing = "[ EDIT ]")
            SettingsRow("Refresh now", syncLine, onRefresh, Modifier.testTag("settings:refresh"))
            SettingsRow("Check for updates", updateLine, onCheckUpdates, Modifier.testTag("settings:updates"))
            Column(Modifier.padding(top = 6.dp)) {
                Rule()
                Column(Modifier.padding(start = 17.dp, top = 6.dp), verticalArrangement = Arrangement.spacedBy(Dimens.S4)) {
                    InfoLine("APP_VERSION", appVersion)
                    InfoLine("LOG_FILE", logPath)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        KeyHints("OK" to "Edit or run", "BACK" to "Home")
    }

    if (editing) {
        Dialog(onDismissRequest = { editing = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            UrlEditDialogContent(currentUrl.orEmpty(), onSave = { url -> onSaveUrl(url).also { if (it == null) editing = false } }, onCancel = { editing = false })
        }
    }
}

/** URL edit dialog body (Dialog + tv Surface, Scrim backdrop). */
@Composable
fun UrlEditDialogContent(initialUrl: String, onSave: suspend (String) -> String?, onCancel: () -> Unit) {
    Box(Modifier.fillMaxSize().background(UzzuColors.Scrim), contentAlignment = Alignment.Center) {
        Surface(
            shape = UzzuShapes.Dialog,
            colors = SurfaceDefaults.colors(containerColor = UzzuColors.Surface, contentColor = UzzuColors.TextPrimary),
            border = Border(BorderStroke(Dimens.RestBorder, UzzuColors.Outline), shape = UzzuShapes.Dialog),
        ) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(Dimens.S6)) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.S2)) {
                    Text("// EDIT SIGNAL SOURCE", style = UzzuType.Mono, color = UzzuColors.TextSecondary)
                    Text("PLAYLIST URL", style = UzzuType.Title, color = UzzuColors.TextPrimary)
                }
                PlaylistUrlForm(initialUrl, onSave, onCancel = onCancel)
            }
        }
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier, trailing: String? = null) {
    UzzuCard(onClick, UzzuShapes.SettingsRow, modifier.width(FormWidth).height(56.dp)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.S5)) {
            Column(Modifier.weight(1f)) {
                Text(title.uppercase(), style = RowTitle, color = UzzuColors.TextPrimary)
                Text(subtitle, style = UzzuType.Mono, color = UzzuColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (trailing != null) Text(trailing, style = UzzuType.Mono.copy(fontWeight = FontWeight.Bold), color = UzzuColors.TextPrimary)
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    val style = UzzuType.Mono.copy(lineHeight = UzzuType.MonoLarge.lineHeight)
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.S6)) {
        Text(label, style = style, color = UzzuColors.TextSecondary, modifier = Modifier.width(120.dp))
        Text(value, style = style, color = UzzuColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
