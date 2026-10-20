package com.bmiller.uzzu.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.bmiller.uzzu.ui.components.InitialFocus
import com.bmiller.uzzu.ui.components.KeyHints
import com.bmiller.uzzu.ui.components.LoadingTag
import com.bmiller.uzzu.ui.components.ScreenHeader
import com.bmiller.uzzu.ui.components.UzzuButton
import com.bmiller.uzzu.ui.theme.Dimens
import com.bmiller.uzzu.ui.theme.UzzuColors
import com.bmiller.uzzu.ui.theme.UzzuType
import kotlinx.coroutines.launch

/** Settings-row width shared by S0 and S7 (1200 px canvas). */
val FormWidth = 600.dp

/**
 * URL entry used by S0 and the S7 edit dialog: explanation, text field, Save (and Cancel), and a
 * LOADING / error line. Syntax is checked first; [onSave] then fetches + parses and returns null on
 * success or a (masked) error message.
 */
@Composable
fun PlaylistUrlForm(
    initialUrl: String,
    onSave: suspend (String) -> String?,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
) {
    var url by rememberSaveable { mutableStateOf(initialUrl) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val fieldFocus = remember { FocusRequester() }

    fun submit() {
        if (busy) return
        error = validatePlaylistUrlSyntax(url)
        if (error != null) return
        busy = true
        scope.launch {
            error = onSave(url.trim())
            busy = false
        }
    }

    InitialFocus(fieldFocus)

    Column(modifier.width(FormWidth), verticalArrangement = Arrangement.spacedBy(Dimens.S5)) {
        Text(
            "Enter your uzzu.tv M3U playlist URL. It is stored on this device only.",
            style = UzzuType.Body,
            color = UzzuColors.TextSecondary,
        )
        PlaylistUrlField(
            value = url,
            onValueChange = { url = it; error = null },
            onDone = ::submit,
            enabled = !busy,
            isError = error != null,
            modifier = Modifier.fillMaxWidth().focusRequester(fieldFocus).testTag("url:field"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.S5)) {
            UzzuButton(if (busy) "Checking" else "Save", ::submit, Modifier.testTag("url:save"), enabled = !busy)
            if (onCancel != null) UzzuButton("Cancel", onCancel, Modifier.testTag("url:cancel"), enabled = !busy)
        }
        when {
            busy -> LoadingTag()
            error != null -> Text(error!!, style = UzzuType.Body, color = UzzuColors.Error, modifier = Modifier.testTag("url:error"))
        }
    }
}

/**
 * S0 first-run setup: one URL field and Save, styled like S7 (HANDOFF: "First-run setup reuses
 * this styling").
 */
@Composable
fun SetupScreen(initialUrl: String = "", onSave: suspend (String) -> String?) {
    Column(
        Modifier.fillMaxSize().background(UzzuColors.Background).padding(horizontal = Dimens.SafeH, vertical = Dimens.SafeV),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        ScreenHeader("00 // SETUP", "Playlist")
        PlaylistUrlForm(initialUrl, onSave)
        Spacer(Modifier.weight(1f))
        KeyHints("OK" to "Edit or save", "BACK" to "Exit")
    }
}
