package com.bmiller.uzzu.ui.player

import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.bmiller.uzzu.player.PlayerController
import com.bmiller.uzzu.ui.theme.UzzuColors

/** S5 (M1 version): full-screen video, no controller UI. Back returns to the list. */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(controller: PlayerController, onBack: () -> Unit) {
    val player by controller.player.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val focus = remember { FocusRequester() }

    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { focus.requestFocus() }

    Box(Modifier.fillMaxSize().background(UzzuColors.Background).focusRequester(focus).focusable()) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    keepScreenOn = true
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { it.player = player },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
