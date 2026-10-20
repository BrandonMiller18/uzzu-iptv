package com.bmiller.uzzu.update

import com.bmiller.uzzu.data.Settings
import com.bmiller.uzzu.log.AppLogger
import com.bmiller.uzzu.log.SecretMasker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * Checks GitHub's latest release (PLAN "In-app updates") and installs its APK.
 *
 * - [check] (force = false) runs at most once per [THROTTLE_MS], tracked in [Settings.lastUpdateCheckAt];
 *   when [autoCheck] is false (debug builds) only forced checks from S7 run.
 * - 404 (no release published yet) means up to date. Failures are logged masked and never throw.
 * - [install] downloads to [updatesDir] (a FileProvider cache path), verifies the size, then hands the
 *   file to [installer], asking for "Install unknown apps" first when it is not granted.
 */
class GitHubUpdateChecker(
    private val http: OkHttpClient,
    private val settings: Settings,
    private val logger: AppLogger,
    private val currentVersion: String,
    private val updatesDir: File,
    private val installer: ApkInstaller,
    private val scope: CoroutineScope,
    private val latestUrl: String = LATEST_URL,
    private val autoCheck: Boolean = true,
    private val clock: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : UpdateChecker {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    override val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** The release behind [UpdateState.Available]. */
    @Volatile private var pending: Pair<String, GitHubRelease.Asset>? = null
    private var download: Job? = null

    override suspend fun check(force: Boolean) {
        if (download?.isActive == true) return
        if (!force) {
            if (!autoCheck) return
            val last = settings.lastUpdateCheckAt.first()
            if (last != null && clock() - last in 0 until THROTTLE_MS) return
        }
        val now = clock()
        settings.setLastUpdateCheckAt(now)
        _state.value = UpdateState.Checking
        _state.value = try {
            evaluate(fetchLatest(), now)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w(TAG, "check failed: ${SecretMasker.mask(e.message ?: e.javaClass.simpleName)}")
            UpdateState.Failed("Update check failed: ${SecretMasker.mask(e.message ?: e.javaClass.simpleName)}")
        }
    }

    private fun evaluate(release: GitHubRelease?, now: Long): UpdateState {
        val tag = release?.tagName
        if (release == null || tag == null || release.draft || release.prerelease || !Version.isNewer(tag, currentVersion)) {
            logger.i(TAG, "up to date ($currentVersion, latest ${tag ?: "none"})")
            pending = null
            cleanCache(keep = null)
            return UpdateState.UpToDate(now)
        }
        val apk = release.apk ?: run {
            logger.w(TAG, "release $tag has no .apk asset")
            return UpdateState.Failed("Release $tag has no APK")
        }
        val version = tag.removePrefix("v")
        logger.i(TAG, "update available: $version (current $currentVersion, ${apk.size} bytes)")
        pending = version to apk
        cleanCache(keep = apkFile(version))
        return UpdateState.Available(version, currentVersion)
    }

    /** Null when the repo has no published release (404). */
    private suspend fun fetchLatest(): GitHubRelease? = withContext(io) {
        val request = Request.Builder()
            .url(latestUrl)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()
        http.newCall(request).execute().use { r ->
            when {
                r.code == 404 -> null
                !r.isSuccessful -> throw IOException("HTTP ${r.code}")
                else -> GitHubRelease.parse(r.body.string())
            }
        }
    }

    override fun install() {
        val (version, asset) = pending ?: return
        if (download?.isActive == true) return
        download = scope.launch {
            try {
                val apk = withContext(io) { downloadApk(version, asset) }
                setAvailable { copy(progress = null) }
                if (!installer.canInstall() && installer.openInstallPermission()) {
                    logger.i(TAG, "asking for install-unknown-apps permission")
                    setAvailable { copy(note = "Allow Uzzu to install apps, then select Install update again.") }
                    return@launch
                }
                logger.i(TAG, "launching installer for ${apk.name}")
                installer.install(apk)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = SecretMasker.mask(e.message ?: e.javaClass.simpleName)
                logger.w(TAG, "install failed: $msg")
                setAvailable { copy(progress = null, error = "Update failed: $msg") }
            }
        }
    }

    private fun downloadApk(version: String, asset: GitHubRelease.Asset): File {
        val target = apkFile(version)
        if (target.isFile && target.length() == asset.size) return target
        updatesDir.mkdirs()
        val part = File(updatesDir, target.name + ".part")
        setAvailable { copy(progress = progressText(0, asset.size), note = null, error = null) }
        val request = Request.Builder().url(asset.downloadUrl).header("Accept", "application/octet-stream").build()
        http.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw IOException("download HTTP ${r.code}")
            r.body.byteStream().use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    var lastShown = -1L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        total += n
                        // Refresh the bar every ~512 KB.
                        if (total / PROGRESS_STEP != lastShown) {
                            lastShown = total / PROGRESS_STEP
                            setAvailable { copy(progress = progressText(total, asset.size)) }
                        }
                    }
                }
            }
        }
        if (part.length() != asset.size) {
            val got = part.length()
            part.delete()
            throw IOException("size mismatch ($got of ${asset.size} bytes)")
        }
        target.delete()
        if (!part.renameTo(target)) throw IOException("could not save the APK")
        logger.i(TAG, "downloaded ${target.name} (${target.length()} bytes)")
        return target
    }

    override fun dismiss() = setAvailable { copy(dismissed = true) }

    private inline fun setAvailable(crossinline f: UpdateState.Available.() -> UpdateState.Available) =
        _state.update { if (it is UpdateState.Available) it.f() else it }

    private fun apkFile(version: String) = File(updatesDir, "uzzu-iptv-$version.apk")

    /** Deletes old/partial APKs (everything but [keep]). */
    private fun cleanCache(keep: File?) {
        updatesDir.listFiles()?.forEach { if (it != keep && it.delete()) logger.i(TAG, "removed ${it.name}") }
    }

    companion object {
        const val LATEST_URL = "https://api.github.com/repos/BrandonMiller18/uzzu-iptv/releases/latest"
        const val THROTTLE_MS = 24L * 60 * 60 * 1000
        private const val PROGRESS_STEP = 512L * 1024
        private const val TAG = "Update"

        fun progressText(done: Long, total: Long): String {
            val pct = if (total > 0) (done * 100 / total).toInt() else 0
            return String.format(Locale.US, "DOWNLOADING %.1f / %.1f MB · %d%%", done / 1e6, total / 1e6, pct)
        }
    }
}
