package com.bmiller.uzzu.ui.setup

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text as M3Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.bmiller.uzzu.ui.theme.UzzuColors
import com.bmiller.uzzu.ui.theme.UzzuShapes
import com.bmiller.uzzu.ui.theme.UzzuType
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

private const val DOTS = "••••"
private val KEEP_QUERY = setOf("type", "output")
private val digitRun = Regex("""\d{4,}""")

/**
 * Display form of the playlist URL for S7: host, last path segment and query names only. Middle
 * path segments, query values (except `type`/`output`), userinfo, emails and digit runs are masked.
 */
fun maskPlaylistUrlForDisplay(url: String?): String {
    val u = url?.trim()?.toHttpUrlOrNull() ?: return if (url.isNullOrBlank()) "NOT SET" else DOTS
    val segs = u.pathSegments
    val path = segs.mapIndexed { i, s -> if (i == segs.lastIndex && '@' !in s && "%40" !in s) digitRun.replace(s, DOTS) else DOTS }.joinToString("/")
    val query = u.queryParameterNames.joinToString("&") { n ->
        val v = u.queryParameter(n)
        if (n.lowercase() in KEEP_QUERY && v != null) "$n=${digitRun.replace(v, DOTS)}" else "$n=$DOTS"
    }
    val host = digitRun.replace(u.host, DOTS)
    return buildString {
        append(host)
        if (path.isNotEmpty()) append('/').append(path)
        if (query.isNotEmpty()) append('?').append(query)
    }
}

/** The playlist URL text field shared by S0 and the S7 edit dialog (tv-material3 has no text field). */
@Composable
fun PlaylistUrlField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    enabled: Boolean,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        singleLine = true,
        label = { M3Text("PLAYLIST URL", style = UzzuType.Label) },
        placeholder = { M3Text(placeholder, style = UzzuType.MonoPlain, color = UzzuColors.TextSecondary, maxLines = 1) },
        textStyle = UzzuType.Mono.copy(color = UzzuColors.TextPrimary),
        shape = UzzuShapes.SettingsRow,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
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
}
