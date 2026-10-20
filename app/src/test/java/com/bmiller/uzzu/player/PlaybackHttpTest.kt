package com.bmiller.uzzu.player

import android.os.Looper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.bmiller.uzzu.data.PlaylistRepository
import com.bmiller.uzzu.data.Settings
import com.bmiller.uzzu.log.AppLogger
import com.bmiller.uzzu.playlist.FeedSide
import com.bmiller.uzzu.schedule.Fixtures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Dns
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList

/**
 * End to end over HTTP: the real ExoPlayer ([PlayerController]), PlaylistRepository and
 * [PlaybackManager] against a MockWebServer. A fake DNS maps `*.fastly.net` to the server so the
 * away feed sits on a "Fastly" host.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaybackHttpTest {
    @get:Rule val tmp = TemporaryFolder()

    private val server = MockWebServer()
    private val requests = CopyOnWriteArrayList<String>()
    @Volatile private var awayToken = "1792476833~old"
    private lateinit var logger: AppLogger
    private lateinit var controller: PlayerController
    private lateinit var manager: PlaybackManager
    private lateinit var repo: PlaylistRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val home get() = "http://127.0.0.1:${server.port}"
    private val fastly get() = "http://tran03.global.ssl.fastly.net:${server.port}"

    private fun playlistText() = """
        #EXTM3U
        #EXTINF:0 tvg-id="" tvg-logo="" group-title="Live-Games",Anaheim Ducks @ New York Rangers-H
        $home/hls/10000007220/g4678.m3u8
        #EXTINF:0 tvg-id="" tvg-logo="" group-title="Live-Games",Anaheim Ducks @ New York Rangers-A
        $fastly/$awayToken/perdias/444734-away/chunks.m3u8
        #EXTINF:0 tvg-id="ESPN" tvg-logo="" group-title="Live-Channels",ESPN
        $home/hls/10000007220/404.m3u8
    """.trimIndent() + "\n"

    // A live media playlist with no segments yet: loads fine, the player keeps buffering.
    private val mediaPlaylist = "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:6\n#EXT-X-MEDIA-SEQUENCE:1\n"

    @Before fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                requests += "${request.url.host} $path"
                return when {
                    path == "/get.php" -> MockResponse.Builder().body(playlistText()).build()
                    path.contains("1792476833~old") -> MockResponse.Builder().code(403).build()
                    path.contains("away/chunks.m3u8") -> MockResponse.Builder().body(mediaPlaylist).build()
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
        val ok = OkHttpClient.Builder()
            .dns { host -> if (host.endsWith("fastly.net")) listOf(InetAddress.getByName("127.0.0.1")) else Dns.SYSTEM.lookup(host) }
            .build()
        logger = AppLogger(tmp.newFolder("logs"))
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) { tmp.newFile("s.preferences_pb").also { it.delete() } }
        repo = PlaylistRepository(ok, Settings(store), logger, classify = Fixtures.classifier::classify)
        runBlocking { repo.saveAndLoad("$home/get.php?username=someone%40example.com&password=x") }
        val app = RuntimeEnvironment.getApplication()
        controller = PlayerController(app, ok, logger)
        controller.start()
        manager = PlaybackManager(controller, repo, repo.state, { "Wi-Fi · Connected" }, logger, scope, MainScheduler())
    }

    @After fun tearDown() {
        controller.release()
        scope.cancel()
        server.close()
    }

    private fun runMainUntil(timeoutMs: Long = 30_000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            check(System.currentTimeMillis() < end) { "timed out; requests=$requests state=${manager.state.value}" }
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            Thread.sleep(10)
        }
    }

    @Test fun `forced 404 ends on S6 with HTTP 404, the Media3 code name and a masked URL`() {
        val ch = repo.current!!.channels.single()
        manager.tune(PlayContext(Section.CHANNELS, "All", listOf(ch.key), ch.key))
        runMainUntil { manager.state.value.error != null }
        val report = manager.state.value.error!!
        assertEquals(404, report.httpStatus)
        assertEquals("ERROR_CODE_IO_BAD_HTTP_STATUS", report.errorCodeName)
        assertEquals("127.0.0.1 /hls/******7220/404.m3u8", report.maskedStream)
        assertEquals("Stream returned 404", report.summary)
        assertTrue(report.lines.single { it.label == "ATTEMPTS" }.value.startsWith("3 · "))
        assertEquals(3, requests.count { it.endsWith("/404.m3u8") })
        logger.flush()
        val log = logger.file.readText()
        assertFalse("10000007220" in log)
        assertFalse("someone" in log)
        assertTrue("ERROR_CODE_IO_BAD_HTTP_STATUS http=404" in log)
    }

    @Test fun `expired away URL - 403 on Fastly, playlist re-fetch gives a working URL, recovers without user action`() {
        val game = repo.current!!.games.single()
        manager.tune(PlayContext(Section.GAMES, "All", listOf(game.id), game.id, FeedSide.AWAY))
        awayToken = "1799999999~new" // the server's next playlist has a fresh token
        runMainUntil { requests.any { "1799999999~new" in it } }
        // The new media playlist loads and the session stays healthy (no S6).
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        Thread.sleep(300)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertNull(manager.state.value.error)
        assertEquals(2, requests.count { it.endsWith("/get.php") })
        assertEquals(1, requests.count { "1792476833~old" in it })
        assertTrue(controller.player.value!!.playerError == null)
        logger.flush()
        val log = logger.file.readText()
        assertTrue("re-fetching playlist" in log)
        assertTrue("retry with refreshed URL (new)" in log)
        assertFalse("1792476833~old" in log)
    }
}
