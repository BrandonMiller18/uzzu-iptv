package com.bmiller.uzzu.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.bmiller.uzzu.log.AppLogger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val server = MockWebServer()
    private lateinit var settings: Settings
    private lateinit var repo: PlaylistRepository
    private lateinit var logger: AppLogger
    private val fixture = requireNotNull(javaClass.classLoader!!.getResource("fixtures/sample.m3u")).readText()

    @Before
    fun setUp() {
        server.start()
        settings = Settings(InMemoryPreferences())
        logger = AppLogger(File(tmp.root, "logs"))
        repo = PlaylistRepository(OkHttpClient(), settings, logger, io = UnconfinedTestDispatcher(), clock = { 1_000L })
    }

    @After
    fun tearDown() = server.shutdown()

    private fun url() = server.url("/hls/10000007220/playlist.m3u").toString()

    @Test
    fun `saveAndLoad fetches, parses, saves URL and records the fetch`() = runTest {
        server.enqueue(MockResponse().setBody(fixture))
        val p = repo.saveAndLoad(url())
        assertEquals(6, p.games.size)
        assertEquals(url(), settings.playlistUrl())
        assertEquals(1_000L, settings.lastFetchAt.first())
        assertTrue(settings.lastFetchResult.first()!!.startsWith("OK 6 games"))
        val state = repo.state.value as PlaylistState.Loaded
        assertEquals(96, state.playlist.channels.size)
        // Logged URL is masked.
        val log = logger.file.readText()
        assertTrue(log.contains("******7220"))
        assertTrue(!log.contains("10000007220"))
    }

    @Test
    fun `refresh failure keeps the previous playlist and records the result`() = runTest {
        server.enqueue(MockResponse().setBody(fixture))
        repo.saveAndLoad(url())
        server.enqueue(MockResponse().setResponseCode(403))
        val r = repo.refresh()
        assertTrue(r.isFailure)
        assertTrue(repo.state.value is PlaylistState.Loaded)
        assertEquals("FAILED Playlist request returned HTTP 403", settings.lastFetchResult.first())
    }

    @Test
    fun `non playlist response is rejected`() = runTest {
        server.enqueue(MockResponse().setBody("<html>login</html>"))
        val r = runCatching { repo.saveAndLoad(url()) }
        assertTrue(r.exceptionOrNull() is PlaylistException)
        assertEquals(null, settings.playlistUrl())
    }

    @Test
    fun `refresh without a saved URL fails`() = runTest {
        assertTrue(repo.refresh().isFailure)
    }
}

/** File-backed DataStore cannot overwrite on Windows JVMs (rename fails); tests use memory. */
private class InMemoryPreferences : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}
