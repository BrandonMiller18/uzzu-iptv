package com.bmiller.uzzu.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.bmiller.uzzu.ui.theme.Dimens
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

/**
 * The playlist URL text field shared by S0 and the S7 edit dialog. tv-material3 has no text field,
 * so it is a foundation BasicTextField styled like the other focusables (HANDOFF rule 4): Surface
 * fill, 1 dp Outline at rest, 3 dp Accent + 1.05 scale on focus, SettingsRow cut shape, theme
 * colors for cursor and selection.
 *
 * TV IME etiquette: D-pad focus lands on a non-editing wrapper ([modifier] goes there), so focus
 * alone never opens the keyboard (on Fire OS it is full screen and hides the form). OK on the
 * wrapper moves focus into the editor and opens the keyboard; moving focus away ends editing.
 */
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
    val wrapperInteraction = remember { MutableInteractionSource() }
    val editorInteraction = remember { MutableInteractionSource() }
    val wrapperFocused by wrapperInteraction.collectIsFocusedAsState()
    val editorFocused by editorInteraction.collectIsFocusedAsState()
    val focused = wrapperFocused || editorFocused
    var editing by remember { mutableStateOf(false) }
    val editor = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Open the keyboard once the editor holds focus and its input session has started.
    LaunchedEffect(editorFocused) {
        if (editorFocused) {
            withFrameNanos { }
            keyboard?.show()
        }
    }
    val border = when {
        focused -> Modifier.border(Dimens.FocusBorder, UzzuColors.Accent, UzzuShapes.SettingsRow)
        isError -> Modifier.border(Dimens.RestBorder, UzzuColors.Error, UzzuShapes.SettingsRow)
        else -> Modifier.border(Dimens.RestBorder, UzzuColors.Outline, UzzuShapes.SettingsRow)
    }

    Column(verticalArrangement = Arrangement.spacedBy(Dimens.S2)) {
        Text("PLAYLIST URL", style = UzzuType.Label, color = if (focused) UzzuColors.TextPrimary else UzzuColors.TextSecondary)
        Box(
            modifier
                .graphicsLayer {
                    val s = if (focused) Dimens.FocusScale else 1f
                    scaleX = s
                    scaleY = s
                }
                .onKeyEvent {
                    val ok = it.key == Key.DirectionCenter || it.key == Key.Enter || it.key == Key.NumPadEnter
                    if (!ok || !enabled) return@onKeyEvent false
                    if (it.type == KeyEventType.KeyUp) {
                        editing = true
                        if (editorFocused) keyboard?.show() else runCatching { editor.requestFocus() }
                    }
                    true
                }
                .focusable(enabled, wrapperInteraction)
                .background(UzzuColors.Surface, UzzuShapes.SettingsRow)
                .then(border),
        ) {
            CompositionLocalProvider(LocalTextSelectionColors provides SelectionColors) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(editor)
                        .focusProperties { canFocus = editing }
                        .onFocusChanged { if (!it.isFocused && editing) editing = false }
                        .testTag("url:input"),
                    enabled = enabled,
                    singleLine = true,
                    textStyle = UzzuType.MonoPlain.copy(color = if (enabled) UzzuColors.TextPrimary else UzzuColors.TextSecondary),
                    cursorBrush = SolidColor(UzzuColors.TextPrimary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onDone = { onDone() }),
                    interactionSource = editorInteraction,
                    decorationBox = { inner ->
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                            if (value.isEmpty()) {
                                Text(placeholder, style = UzzuType.MonoPlain, color = UzzuColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            inner()
                        }
                    },
                )
            }
        }
    }
}

/** Cursor handle and selection highlight in theme colors (not the default M3 purple). */
private val SelectionColors = TextSelectionColors(handleColor = UzzuColors.TextPrimary, backgroundColor = UzzuColors.Outline)
