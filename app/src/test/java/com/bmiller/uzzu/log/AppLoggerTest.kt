package com.bmiller.uzzu.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.concurrent.thread

class AppLoggerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `writes masked lines to logs app log`() {
        val dir = File(tmp.root, "logs")
        val logcat = mutableListOf<String>()
        val logger = AppLogger(dir, logcat = { _, _, msg -> logcat += msg })
        logger.i("Player", "tune https://uzzu.tv/hls/10000007220/g4678.m3u8 for someone@example.com")
        val text = File(dir, "app.log").readText()
        assertTrue(text.contains("I/Player: tune https://uzzu.tv/hls/******7220/g4678.m3u8 for ***@***"))
        assertFalse(text.contains("10000007220"))
        assertFalse(logcat.single().contains("10000007220"))
    }

    @Test
    fun `throwable messages are masked too`() {
        val logger = AppLogger(tmp.root)
        logger.e("Net", "failed", RuntimeException("GET https://uzzu.tv/hls/10000007220/1.m3u8"))
        val text = logger.file.readText()
        assertTrue(text.contains("RuntimeException: GET https://uzzu.tv/hls/******7220/1.m3u8"))
    }

    @Test
    fun `rotates at max size and keeps three files`() {
        val logger = AppLogger(tmp.root, maxBytes = 1_000)
        repeat(200) { logger.i("T", "line $it " + "x".repeat(50)) }
        val names = tmp.root.list()!!.sorted()
        assertEquals(listOf("app.log", "app.log.1", "app.log.2"), names)
        names.forEach { assertTrue(File(tmp.root, it).length() <= 1_000) }
        assertTrue(logger.file.readText().contains("line 199 "))
    }

    @Test
    fun `concurrent writes do not lose or interleave lines`() {
        val logger = AppLogger(tmp.root)
        val threads = (0 until 8).map { t -> thread { repeat(250) { logger.i("T$t", "msg $it") } } }
        threads.forEach { it.join() }
        val lines = logger.file.readLines()
        assertEquals(2_000, lines.size)
        assertTrue(lines.all { Regex("""^\S+ \S+ I/T\d: msg \d+$""").matches(it) })
    }
}
