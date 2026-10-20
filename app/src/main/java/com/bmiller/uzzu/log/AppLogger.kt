package com.bmiller.uzzu.log

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

/**
 * Appends masked log lines to `files/logs/app.log`, rotating at [maxBytes] and keeping [keepFiles]
 * files in total (app.log, app.log.1, app.log.2). Thread-safe. Every message passes through
 * [SecretMasker] before it reaches the file or logcat.
 *
 * Masking, timestamping and logcat run on the caller's thread; the file append runs on [writer], a
 * single background thread by default, so player callbacks and key handlers on the main thread never
 * wait for flash I/O. One writer thread keeps lines in call order. [flush] waits for queued lines
 * (onStop, tests).
 */
class AppLogger(
    val dir: File,
    private val maxBytes: Long = 1_024L * 1_024L,
    private val keepFiles: Int = 3,
    private val logcat: ((level: Char, tag: String, msg: String) -> Unit)? = null,
    private val now: () -> LocalDateTime = LocalDateTime::now,
    private val writer: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "uzzu-log").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }
    },
) {
    val file: File get() = File(dir, FILE_NAME)
    private val timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    fun i(tag: String, msg: String) = log('I', tag, msg, null)
    fun w(tag: String, msg: String, t: Throwable? = null) = log('W', tag, msg, t)
    fun e(tag: String, msg: String, t: Throwable? = null) = log('E', tag, msg, t)

    fun log(level: Char, tag: String, msg: String, t: Throwable?) {
        val full = if (t == null) msg else "$msg | ${t.javaClass.simpleName}: ${t.message}"
        val masked = SecretMasker.mask(full)
        logcat?.invoke(level, tag, masked)
        val line = "${now().format(timeFormat)} $level/$tag: $masked\n"
        try {
            writer.execute { append(line) }
        } catch (_: RejectedExecutionException) {
            // Shut down: drop the line rather than block or crash.
        }
    }

    /** Blocks until every line logged so far is on disk (at most [timeoutMillis]). */
    fun flush(timeoutMillis: Long = 2_000) {
        runCatching { writer.submit { }.get(timeoutMillis, TimeUnit.MILLISECONDS) }
    }

    private fun append(line: String) {
        try {
            dir.mkdirs()
            val f = file
            if (f.length() + line.length > maxBytes) rotate()
            f.appendText(line)
        } catch (_: Exception) {
            // Logging must never crash the app.
        }
    }

    private fun rotate() {
        File(dir, "$FILE_NAME.${keepFiles - 1}").delete()
        for (i in keepFiles - 2 downTo 1) {
            val src = File(dir, "$FILE_NAME.$i")
            if (src.exists()) src.renameTo(File(dir, "$FILE_NAME.${i + 1}"))
        }
        file.renameTo(File(dir, "$FILE_NAME.1"))
    }

    companion object {
        const val FILE_NAME = "app.log"
    }
}
