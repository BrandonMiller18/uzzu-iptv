package com.bmiller.uzzu.ui.player

import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.bmiller.uzzu.data.PlaylistState
import com.bmiller.uzzu.player.PlayerController
import com.bmiller.uzzu.player.PlayerControls
import com.bmiller.uzzu.player.Section
import com.bmiller.uzzu.player.sameItem
import com.bmiller.uzzu.playlist.Game
import com.bmiller.uzzu.ui.PlayerExit
import com.bmiller.uzzu.ui.error.ErrorScreen
import com.bmiller.uzzu.ui.feed.FeedPicker
import com.bmiller.uzzu.ui.feed.needsFeedPicker
import com.bmiller.uzzu.ui.feed.singleFeedSide
import com.bmiller.uzzu.ui.theme.Dimens
import com.bmiller.uzzu.ui.theme.UzzuColors
import kotlinx.coroutines.delay

/**
 * S5 player + tune banner, O1 overlay and S6 error, over one video surface that never re-lays out.
 *
 * Keys (S5, overlay and S6 hidden): Select → overlay; D-pad Up / CH− → previous, D-pad Down / CH+ →
 * next in the current group (wrapping); Back → last channel, else [PlayerExit.toList]; long-press
 * Back (any auto-repeat of Back, i.e. held ≥ the key-repeat timeout) → [PlayerExit.toMenu];
 * Play/Pause → pause / resume at the live edge. Back events are passed to [logKey] so the remote's
 * behaviour can be confirmed from app.log.
 */
@Composable
fun PlayerScreen(
    controls: PlayerControls,
    model: PlaylistState,
    exit: PlayerExit,
    startPolling: () -> AutoCloseable,
    video: @Composable () -> Unit,
    logKey: (String) -> Unit = {},
) {
    val state by controls.state.collectAsStateWithLifecycle()
    val playlist = (model as? PlaylistState.Loaded)?.playlist
    val now = state.now
    val error = state.error
    var overlay by remember { mutableStateOf(false) }
    var bannerVisible by remember { mutableStateOf(false) }
    var backLongPressed by remember { mutableStateOf(false) }
    // Select opens the overlay on key up, and only for a press that started here (not the list's Select).
    var selectArmed by remember { mutableStateOf(false) }
    val rootFocus = remember { FocusRequester() }

    KeepScreenOn()

    LaunchedEffect(state.tuneCount) {
        if (now == null) return@LaunchedEffect
        bannerVisible = true
        delay(Dimens.TuneBannerMillis.toLong())
        bannerVisible = false
    }
    LaunchedEffect(overlay, error == null) {
        if (!overlay && error == null) {
            withFrameNanos { }
            runCatching { rootFocus.requestFocus() }
        }
    }
    if (error != null && overlay) overlay = false

    fun onKey(e: KeyEvent): Boolean {
        val down = e.action == KeyEvent.ACTION_DOWN
        val first = down && e.repeatCount == 0
        when (e.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> if (first) controls.togglePause()
            KeyEvent.KEYCODE_MEDIA_PLAY -> if (first && state.paused) controls.togglePause()
            KeyEvent.KEYCODE_MEDIA_PAUSE -> if (first && !state.paused) controls.togglePause()
            else -> if (overlay || error != null) return false else when (e.keyCode) {
                KeyEvent.KEYCODE_BACK -> {
                    logKey("BACK action=${if (down) "DOWN" else "UP"} repeat=${e.repeatCount} flags=0x${Integer.toHexString(e.flags)} longPress=${e.isLongPress} canceled=${e.isCanceled}")
                    when {
                        first -> backLongPressed = false
                        down -> if (!backLongPressed) {
                            backLongPressed = true
                            logKey("BACK long press detected → exit to menu")
                            exit.toMenu()
                        }
                        else -> {
                            val wasLong = backLongPressed
                            backLongPressed = false
                            if (!wasLong && !e.isCanceled && !controls.toggleLast()) exit.toList()
                        }
                    }
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER ->
                    if (first) {
                        selectArmed = true
                    } else if (!down && selectArmed && now != null) {
                        selectArmed = false
                        overlay = true
                        controls.overlayOpened()
                    }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_DOWN -> if (first) controls.step(-1)
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_UP -> if (first) controls.step(1)
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> Unit
                else -> return false
            }
        }
        return true
    }

    // The key handler sits on a non-focusable parent (it sees keys for any focused child), and S5's
    // focus target is a sibling of O1/S6: a focusable ancestor would swallow Back as a focus "exit"
    // before the overlay's / error screen's BackHandler.
    Box(
        Modifier
            .fillMaxSize()
            .background(UzzuColors.Background)
            .onPreviewKeyEvent { onKey(it.nativeKeyEvent) },
    ) {
        video()
        Box(Modifier.fillMaxSize().testTag("player").focusRequester(rootFocus).focusable())

        val showBanner = bannerVisible && !overlay && error == null && now != null
        AnimatedVisibility(
            visible = showBanner,
            enter = fadeIn(tween(Dimens.FadeMillis)),
            exit = fadeOut(tween(Dimens.FadeMillis)),
            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = Dimens.SafeH, vertical = Dimens.SafeV),
        ) {
            now?.let { TuneBanner(it.resolved) }
        }
        if (state.paused && !overlay && !showBanner && error == null) {
            PausedTag(Modifier.align(Alignment.BottomStart).padding(horizontal = Dimens.SafeH, vertical = Dimens.SafeV))
        }

        AnimatedVisibility(
            visible = overlay && now != null && playlist != null,
            enter = fadeIn(tween(Dimens.FadeMillis)),
            exit = fadeOut(tween(Dimens.FadeMillis)),
        ) {
            if (now != null && playlist != null) {
                OverlayHost(
                    controls = controls,
                    playlist = playlist,
                    playing = now.context,
                    startPolling = startPolling,
                    onExit = exit.toMenu,
                    onClose = { overlay = false },
                )
            }
        }

        if (error != null) ErrorScreen(error, onRetry = controls::retry, onBack = exit.toList)
    }
}

/** O1 state holder: tab, rows, polling while visible, FeedPicker over the overlay. */
@Composable
private fun OverlayHost(
    controls: PlayerControls,
    playlist: com.bmiller.uzzu.playlist.Playlist,
    playing: com.bmiller.uzzu.player.PlayContext,
    startPolling: () -> AutoCloseable,
    onExit: () -> Unit,
    onClose: () -> Unit,
) {
    DisposableEffect(Unit) {
        val polling = startPolling()
        onDispose { polling.close() }
    }
    val section = playing.section
    val tabs = remember(playlist, section) { overlayTabs(playlist, section) }
    var tab by remember { mutableStateOf(initialOverlayTab(tabs, playing) ?: "") }
    if (tab !in tabs && tabs.isNotEmpty()) tab = tabs.first()
    val rows = remember(playlist, tab, playing) { overlayRows(playlist, section, tab, playing) }
    var picking by remember { mutableStateOf<Game?>(null) }

    fun tune(ctx: com.bmiller.uzzu.player.PlayContext) {
        onClose()
        when {
            !ctx.sameItem(playing) -> controls.tune(ctx)
            ctx.group != playing.group -> controls.setGroup(ctx) // same stream: no reload
        }
    }

    PlayerOverlay(
        section = section,
        tabs = tabs,
        tab = tab,
        rows = rows,
        onTab = { tab = it },
        onSelectItem = { item ->
            if (section == Section.CHANNELS) {
                tune(overlayTuneContext(section, tab, rows, item.id, null))
            } else {
                val game = playlist.games.firstOrNull { it.id == item.id } ?: return@PlayerOverlay
                if (game.needsFeedPicker()) picking = game else tune(overlayTuneContext(section, tab, rows, game.id, game.singleFeedSide()))
            }
        },
        onFlipSide = controls::flipSide,
        onExit = onExit,
        onClose = onClose,
    )

    picking?.let { game ->
        FeedPicker(
            game = game,
            onPick = { side ->
                picking = null
                tune(overlayTuneContext(section, tab, rows, game.id, side))
            },
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun KeepScreenOn() {
    val activity = LocalActivity.current
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

/** The video: one PlayerView bound to the single ExoPlayer, no controller UI. */
@OptIn(UnstableApi::class)
@Composable
fun VideoSurface(controller: PlayerController) {
    val player by controller.player.collectAsStateWithLifecycle()
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                keepScreenOn = true
                isFocusable = false
                setShutterBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        update = { it.player = player },
        onRelease = { it.player = null },
        modifier = Modifier.fillMaxSize(),
    )
}
