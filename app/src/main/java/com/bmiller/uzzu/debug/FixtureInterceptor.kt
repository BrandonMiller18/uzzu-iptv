package com.bmiller.uzzu.debug

import android.content.res.AssetManager
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Request tag that marks the playlist fetch, so fixture mode can answer it (and nothing else but ESPN). */
object PlaylistRequestTag

/**
 * Debug builds only (`adb shell am start -n com.bmiller.uzzu/.MainActivity --ez uzzu.fixture true`):
 * answers the playlist fetch with the scrubbed test playlist and ESPN scoreboard calls with the
 * recorded fixtures, so S2/S4 can be checked on a device when the real playlist has no games.
 * Today maps to the 2026-10-19 recordings (synthetic live variant where one exists), yesterday to
 * 2026-10-20 ("pre" events). The fixtures are debug assets only; release builds never enable this.
 */
class FixtureInterceptor(private val assets: AssetManager, private val enabled: () -> Boolean) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!enabled()) return chain.proceed(request)
        val body = when {
            request.tag(PlaylistRequestTag::class.java) != null -> asset("sample.m3u")
            request.url.host == "site.api.espn.com" -> espn(request.url.pathSegments, request.url.queryParameter("dates"))
            else -> return chain.proceed(request)
        }
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(if (body != null) 200 else 404)
            .message("fixture")
            .body((body ?: "").toResponseBody("application/octet-stream".toMediaType()))
            .build()
    }

    private fun espn(segments: List<String>, dates: String?): String {
        val slug = segments.getOrNull(segments.indexOf("scoreboard") - 1) ?: return EMPTY
        val today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
        return if (dates == today) {
            asset("espn/${slug}_20261019_in.json") ?: asset("espn/${slug}_20261019.json") ?: EMPTY
        } else {
            asset("espn/${slug}_20261020.json") ?: EMPTY
        }
    }

    private fun asset(path: String): String? = runCatching { assets.open(path).bufferedReader().use { it.readText() } }.getOrNull()

    private companion object {
        const val EMPTY = """{"events":[]}"""
    }
}
