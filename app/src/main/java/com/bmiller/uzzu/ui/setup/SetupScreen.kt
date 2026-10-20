package com.bmiller.uzzu.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text as M3Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.bmiller.uzzu.ui.components.KeyHint
import com.bmiller.uzzu.ui.components.MonoTag
import com.bmiller.uzzu.ui.components.UzzuButton
import com.bmiller.uzzu.ui.theme.Dimens
import com.bmiller.uzzu.ui.theme.UzzuColors
import com.bmiller.uzzu.ui.theme.UzzuShapes
import com.bmiller.uzzu.ui.theme.UzzuType
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Returns null when [url] is an acceptable http(s) URL, else a user-facing reason. */
fun validatePlaylistUrlSyntax(url: String): String? {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return "Enter the playlist URL."
    if (!trimmed.startsWith("https://", true) && !trimmed.startsWith("http://", true)) {
        return "The URL must start with https:// or http://."
    }
    if (trimmed.toHttpUrlOrNull() == null) return "That is not a valid URL."
    return null
}

/**
 * S0 first-run setup: one URL field and Save. Styling follows the S7 settings rows
 * (HANDOFF: "First-run setup reuses this styling").
 *
 * [onSave] fetches and parses the URL and returns null on success or an error message.
 */
@Composable
fun SetupScreen(
    initialUrl: String = "",
    onSave: suspend (String) -> String?,
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

    LaunchedEffect(Unit) { fieldFocus.requestFocus() }

    Column(
        Modifier
            .fillMaxSize()
            .background(UzzuColors.Background)
            .padding(horizontal = Dimens.SafeH, vertical = Dimens.SafeV),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            MonoTag("00 // SETUP")
            Text("PLAYLIST", style = UzzuType.Display, color = UzzuColors.TextPrimary)
        }
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(UzzuColors.Outline))

        Column(Modifier.width(600.dp), verticalArrangement = Arrangement.spacedBy(Dimens.S5)) {
            Text(
                "Enter your uzzu.tv M3U playlist URL. It is stored on this device only.",
                style = UzzuType.Body,
                color = UzzuColors.TextSecondary,
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it; error = null },
                modifier = Modifier.fillMaxWidth().focusRequester(fieldFocus),
                enabled = !busy,
                singleLine = true,
                label = { M3Text("PLAYLIST URL", style = UzzuType.Label) },
                placeholder = { M3Text("https://", style = UzzuType.Mono, color = UzzuColors.TextSecondary) },
                textStyle = UzzuType.Mono.copy(color = UzzuColors.TextPrimary),
                shape = UzzuShapes.SettingsRow,
                isError = error != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = UzzuColors.TextPrimary,
                    unfocusedTextColor = UzzuColors.TextPrimary,
                    disabledTextColor = UzzuColors.TextSecondary,
                    errorTextColor = UzzuColors.TextPrimary,
                    focusedContainerColor = UzzuColors.Surface,
                    unfocusedContainerColor = UzzuColors.Surface,
                    disabledContainerColor = UzzuColors.Surface,
                    errorContainerColor = UzzuColors.Surface,
                    cursorColor = UzzuColors.TextPrimary,
                    focusedBorderColor = UzzuColors.Accent,
                    unfocusedBorderColor = UzzuColors.Outline,
                    disabledBorderColor = UzzuColors.Outline,
                    errorBorderColor = UzzuColors.Error,
                    focusedLabelColor = UzzuColors.TextPrimary,
                    unfocusedLabelColor = UzzuColors.TextSecondary,
                    disabledLabelColor = UzzuColors.TextSecondary,
                    errorLabelColor = UzzuColors.Error,
                ),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.S6)) {
                UzzuButton(text = if (busy) "Checking" else "Save", onClick = ::submit, enabled = !busy)
            }
            when {
                busy -> MonoTag("LOADING")
                error != null -> Text(error!!, style = UzzuType.Body, color = UzzuColors.Error)
            }
        }

        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            KeyHint("OK", "Edit or save")
            KeyHint("BACK", "Exit")
        }
    }
}
