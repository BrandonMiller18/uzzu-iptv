package com.bmiller.uzzu.ui

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import org.robolectric.RobolectricTestRunner
import com.bmiller.uzzu.playlist.M3uParser
import com.bmiller.uzzu.ui.debug.DebugListScreen
import com.bmiller.uzzu.ui.setup.SetupScreen
import com.bmiller.uzzu.ui.theme.UzzuTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * JVM screenshots via Robolectric + Roborazzi at the Fire TV's 960x540 dp xhdpi screen.
 * `./gradlew recordRoborazziDebug` writes PNGs to app/build/outputs/roborazzi/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun setupScreen() {
        compose.setContent { UzzuTheme { SetupScreen(onSave = { null }) } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/s0_setup.png")
    }

    @Test
    fun debugListScreen() {
        val text = requireNotNull(javaClass.classLoader!!.getResource("fixtures/sample.m3u")).readText()
        val parsed = M3uParser().parse(text)
        // No network in tests: drop logo URLs so Coil does not try to fetch them.
        val playlist = parsed.copy(
            games = parsed.games.map { g ->
                g.copy(homeFeed = g.homeFeed?.copy(logoUrl = null), awayFeed = g.awayFeed?.copy(logoUrl = null))
            },
            channels = parsed.channels.map { it.copy(logoUrl = null) },
        )
        compose.setContent {
            UzzuTheme {
                DebugListScreen(
                    playlist = playlist,
                    status = "LAST SYNC 7:10 PM",
                    error = null,
                    listState = rememberLazyListState(),
                    restoreFocus = null,
                    onPlayGame = { _, _ -> },
                    onPlayChannel = {},
                    onRefresh = {},
                    onEditUrl = {},
                )
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/m1_debug_list.png")
    }
}
