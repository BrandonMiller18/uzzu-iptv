package com.bmiller.uzzu.update

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.bmiller.uzzu.data.Settings
import com.bmiller.uzzu.log.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class GitHubUpdateCheckerTest {
    @get:Rule val tmp = TemporaryFolder()

    private val server = MockWebServer()
    private val settings = Settings(MemoryPreferences())
    private lateinit var logger: AppLogger
    private lateinit var updatesDir: File
    private val installer = FakeInstaller()
    private var now = 1_000_000_000L

    private val fixture = requireNotNull(javaClass.classLoader!!.getResource("update/releases_latest.json")).readText()

    @Before fun setUp() {
        server.start()
        logger = AppLogger(File(tmp.root, "logs"))
        updatesDir = File(tmp.root, "cache/updates")
    }

    @After fun tearDown() = server.shutdown()

    private var foreground = true

    private fun checker(current: String = "0.9.0", autoCheck: Boolean = true, installable: Boolean = true) = GitHubUpdateChecker(
        http = OkHttpClient(),
        settings = settings,
        logger = logger,
        currentVersion = current,
        updatesDir = updatesDir,
        installer = installer,
        scope = CoroutineScope(UnconfinedTestDispatcher()),
        latestUrl = server.url("/repos/BrandonMiller18/uzzu-iptv/releases/latest").toString(),
        autoCheck = autoCheck,
        clock = { now },
        io = UnconfinedTestDispatcher(),
        isForeground = { foreground },
        installable = installable,
    )

    /** Release with the APK and its `.sha256` asset (served by the mock server). */
    private fun releaseWithChecksum(tag: String, apkSize: Long): String {
        val apkName = "uzzu-iptv-${tag.removePrefix("v")}.apk"
        return """
            {"tag_name":"$tag","draft":false,"prerelease":false,"assets":[
              {"name":"$apkName","size":$apkSize,"browser_download_url":"${server.url("/download/$apkName")}"},
              {"name":"$apkName.sha256","size":90,"browser_download_url":"${server.url("/download/$apkName.sha256")}"}]}
        """.trimIndent()
    }

    private fun sha256(bytes: ByteArray) =
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { String.format(java.util.Locale.ROOT, "%02x", it) }

    private fun release(tag: String, apkSize: Long, apkName: String = "uzzu-iptv-${tag.removePrefix("v")}.apk") = """
        {"tag_name":"$tag","name":"$tag","draft":false,"prerelease":false,"assets":[
          {"name":"$apkName","size":$apkSize,"content_type":"application/vnd.android.package-archive",
           "browser_download_url":"${server.url("/download/$apkName")}"}]}
    """.trimIndent()

    // ---- parsing ----

    @Test fun `parses tag and the apk asset from a real releases-latest response`() {
        val r = GitHubRelease.parse(fixture)
        assertEquals("v1.0.0", r.tagName)
        assertEquals(2, r.assets.size)
        val apk = r.apk!!
        assertEquals("uzzu-iptv-1.0.0.apk", apk.name)
        assertEquals(9_876_543L, apk.size)
        assertEquals("https://github.com/BrandonMiller18/uzzu-iptv/releases/download/v1.0.0/uzzu-iptv-1.0.0.apk", apk.downloadUrl)
    }

    // ---- check ----

    @Test fun `newer tag yields Available and sends the GitHub accept header`() = runTest {
        server.enqueue(MockResponse().setBody(fixture))
        val c = checker("0.9.0")
        c.check(force = false)
        assertEquals(UpdateState.Available("1.0.0", "0.9.0"), c.state.value)
        val req = server.takeRequest()
        assertEquals("/repos/BrandonMiller18/uzzu-iptv/releases/latest", req.path)
        assertEquals("application/vnd.github+json", req.getHeader("Accept"))
        assertEquals(now, settings.lastUpdateCheckAt.first())
    }

    @Test fun `same version is up to date`() = runTest {
        server.enqueue(MockResponse().setBody(fixture))
        val c = checker("1.0.0")
        c.check(force = true)
        assertEquals(UpdateState.UpToDate(now), c.state.value)
    }

    @Test fun `404 (no release yet) is up to date, not an error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"message":"Not Found"}"""))
        val c = checker()
        c.check(force = true)
        assertEquals(UpdateState.UpToDate(now), c.state.value)
    }

    @Test fun `newer release without an apk asset is reported, never offered`() = runTest {
        server.enqueue(MockResponse().setBody("""{"tag_name":"v1.0.0","assets":[{"name":"notes.txt","size":3,"browser_download_url":"https://x/notes.txt"}]}"""))
        val c = checker()
        c.check(force = true)
        assertEquals(UpdateState.Failed("Release v1.0.0 has no APK"), c.state.value)
        c.install()
        assertEquals(0, installer.installs.size)
    }

    @Test fun `server and network errors become Failed, are logged masked and never throw`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        val c = checker()
        c.check(force = true)
        assertEquals(UpdateState.Failed("Update check failed: HTTP 503"), c.state.value)
        server.enqueue(MockResponse().setBody("not json"))
        c.check(force = true)
        assertTrue((c.state.value as UpdateState.Failed).message.startsWith("Update check failed"))
        logger.flush()
        assertTrue(logger.file.readText().contains("check failed: HTTP 503"))
    }

    @Test fun `launch check runs at most once per 24 h, forced checks always run`() = runTest {
        val c = checker()
        server.enqueue(MockResponse().setResponseCode(404))
        c.check(force = false)
        assertEquals(1, server.requestCount)

        now += GitHubUpdateChecker.THROTTLE_MS - 1
        c.check(force = false)
        assertEquals(1, server.requestCount)

        server.enqueue(MockResponse().setResponseCode(404))
        c.check(force = true)
        assertEquals(2, server.requestCount)

        // The forced check restarted the window.
        now += GitHubUpdateChecker.THROTTLE_MS - 1
        c.check(force = false)
        assertEquals(2, server.requestCount)
        now += 1
        server.enqueue(MockResponse().setResponseCode(404))
        c.check(force = false)
        assertEquals(3, server.requestCount)
    }

    @Test fun `debug builds skip the launch check but honor Settings`() = runTest {
        val c = checker(autoCheck = false)
        c.check(force = false)
        assertEquals(0, server.requestCount)
        assertEquals(UpdateState.Idle, c.state.value)
        server.enqueue(MockResponse().setResponseCode(404))
        c.check(force = true)
        assertEquals(1, server.requestCount)
    }

    @Test fun `Later hides the bar but a forced check offers it again`() = runTest {
        server.enqueue(MockResponse().setBody(fixture))
        server.enqueue(MockResponse().setBody(fixture))
        val c = checker()
        c.check(force = false)
        c.dismiss()
        assertTrue((c.state.value as UpdateState.Available).dismissed)
        c.check(force = true)
        assertFalse((c.state.value as UpdateState.Available).dismissed)
    }

    // ---- install ----

    @Test fun `install downloads, verifies the size and launches the installer`() = runTest {
        val apk = ByteArray(700_000) { (it % 251).toByte() }
        server.enqueue(MockResponse().setBody(release("v1.0.0", apk.size.toLong())))
        server.enqueue(MockResponse().setBody(Buffer().write(apk)))
        File(updatesDir.apply { mkdirs() }, "uzzu-iptv-0.8.0.apk").writeText("old")
        val c = checker()
        c.check(force = false)
        assertFalse("old APKs are cleaned", File(updatesDir, "uzzu-iptv-0.8.0.apk").exists())

        c.install()
        val file = installer.installs.single()
        assertEquals(File(updatesDir, "uzzu-iptv-1.0.0.apk"), file)
        assertTrue(file.readBytes().contentEquals(apk))
        assertEquals(listOf(file.name), updatesDir.list()!!.toList())
        val s = c.state.value as UpdateState.Available
        assertNull(s.progress)
        assertNull(s.error)

        // A second Install reuses the verified download.
        c.install()
        assertEquals(2, installer.installs.size)
        assertEquals(2, server.requestCount)
    }

    @Test fun `size mismatch fails the install and deletes the partial file`() = runTest {
        server.enqueue(MockResponse().setBody(release("v1.0.0", 5_000)))
        server.enqueue(MockResponse().setBody("short"))
        val c = checker()
        c.check(force = false)
        c.install()
        assertTrue(installer.installs.isEmpty())
        val s = c.state.value as UpdateState.Available
        assertTrue(s.error!!, s.error!!.startsWith("Update failed: size mismatch"))
        assertNull(s.progress)
        assertTrue(updatesDir.list()!!.isEmpty())
    }

    @Test fun `without install permission it opens the unknown-sources screen and waits`() = runTest {
        installer.allowed = false
        val apk = ByteArray(10) { 1 }
        server.enqueue(MockResponse().setBody(release("v1.0.0", apk.size.toLong())))
        server.enqueue(MockResponse().setBody(Buffer().write(apk)))
        val c = checker()
        c.check(force = false)
        c.install()
        assertEquals(1, installer.permissionPrompts)
        assertTrue(installer.installs.isEmpty())
        assertTrue((c.state.value as UpdateState.Available).note!!.contains("Install update again"))

        // User allowed it in Settings and pressed Install again: no re-download.
        installer.allowed = true
        c.install()
        assertEquals(1, installer.installs.size)
        assertEquals(2, server.requestCount)
    }

    @Test fun `install verifies the published SHA-256`() = runTest {
        val apk = ByteArray(300_000) { (it % 13).toByte() }
        server.enqueue(MockResponse().setBody(releaseWithChecksum("v1.0.0", apk.size.toLong())))
        server.enqueue(MockResponse().setBody("${sha256(apk)}  uzzu-iptv-1.0.0.apk\n"))
        server.enqueue(MockResponse().setBody(Buffer().write(apk)))
        val c = checker()
        c.check(force = false)
        c.install()
        assertEquals(1, installer.installs.size)
        assertNull((c.state.value as UpdateState.Available).error)
        logger.flush()
        assertTrue(logger.file.readText().contains("SHA-256 verified"))
    }

    @Test fun `checksum mismatch fails the install, deletes the file and shows the error`() = runTest {
        val apk = ByteArray(300_000) { (it % 13).toByte() }
        val corrupted = apk.copyOf().also { it[1234] = 99 }
        server.enqueue(MockResponse().setBody(releaseWithChecksum("v1.0.0", apk.size.toLong())))
        server.enqueue(MockResponse().setBody("${sha256(apk)}  uzzu-iptv-1.0.0.apk\n"))
        server.enqueue(MockResponse().setBody(Buffer().write(corrupted)))
        val c = checker()
        c.check(force = false)
        c.install()
        assertTrue(installer.installs.isEmpty())
        assertEquals("Update failed: checksum mismatch", (c.state.value as UpdateState.Available).error)
        assertTrue(updatesDir.list()!!.isEmpty())
    }

    @Test fun `a download finishing in the background waits for Install instead of launching the installer`() = runTest {
        val apk = ByteArray(10) { 1 }
        server.enqueue(MockResponse().setBody(release("v1.0.0", apk.size.toLong())))
        server.enqueue(MockResponse().setBody(Buffer().write(apk)))
        val c = checker()
        c.check(force = false)
        foreground = false
        c.install()
        assertTrue(installer.installs.isEmpty())
        assertEquals("Download complete. Select Install update.", (c.state.value as UpdateState.Available).note)
        foreground = true
        c.install()
        assertEquals(1, installer.installs.size)
        assertEquals(2, server.requestCount)
    }

    @Test fun `debug builds note that the release APK can't install over them`() = runTest {
        server.enqueue(MockResponse().setBody(fixture))
        val c = checker(installable = false)
        c.check(force = true)
        assertTrue((c.state.value as UpdateState.Available).note!!.contains("Uninstall this build first"))
    }

    @Test fun `progress text is mono-friendly`() {
        assertEquals("DOWNLOADING 4.9 / 9.9 MB · 50%", GitHubUpdateChecker.progressText(4_938_272, 9_876_543))
    }
}

private class FakeInstaller : ApkInstaller {
    var allowed = true
    var permissionPrompts = 0
    val installs = mutableListOf<File>()
    override fun canInstall() = allowed
    override fun openInstallPermission(): Boolean { permissionPrompts++; return true }
    override fun install(apk: File) { installs += apk }
}

/** File-backed DataStore cannot overwrite on Windows JVMs (rename fails); tests use memory. */
private class MemoryPreferences : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}
