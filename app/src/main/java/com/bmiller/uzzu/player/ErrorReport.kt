package com.bmiller.uzzu.player

import com.bmiller.uzzu.data.RefreshRecord
import com.bmiller.uzzu.log.SecretMasker
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** One `LABEL  value` line of the S6 diagnostics block. [failing] values render in the Error color. */
data class DiagLine(val label: String, val value: String, val failing: Boolean = false)

/** S6 contents (PLAN "Error screen (S6) contents"). Every URL in here is already masked. */
data class ErrorReport(
    val summary: String,
    val httpStatus: Int?,
    val errorCodeName: String,
    /** Host + masked path, e.g. `uzzu.tv /hls/(masked)7220/g4678.m3u8`. */
    val maskedStream: String,
    val lines: List<DiagLine>,
) {
    companion object {
        fun build(
            item: Resolved,
            failingUrl: String,
            result: Exhausted,
            playlistFetchedAt: Long?,
            lastRefresh: RefreshRecord?,
            network: String,
            nowMillis: Long,
            zone: ZoneId = ZoneId.systemDefault(),
        ): ErrorReport {
            val err = result.lastError
            val stream = maskedStream(failingUrl)
            val attempts = result.attempts
            val lines = listOf(
                DiagLine("ITEM", item.label),
                DiagLine("STREAM", stream),
                DiagLine("HTTP_STATUS", err.httpStatus?.toString() ?: "None", failing = err.httpStatus != null),
                DiagLine("PLAYER_ERR", err.errorCodeName, failing = true),
                DiagLine("ATTEMPTS", "${attempts.size} · " + attempts.joinToString(", ") { formatSeconds(it.atMillis, zone) }),
                DiagLine("PLAYLIST", playlistLine(playlistFetchedAt, lastRefresh, nowMillis, zone)),
                DiagLine("NETWORK", network),
            )
            return ErrorReport(summary(err, result), err.httpStatus, err.errorCodeName, stream, lines)
        }

        fun summary(err: StreamError, result: Exhausted): String {
            val after = when {
                result.refreshOutcome == RefreshOutcome.ItemGone -> return "This item is no longer in the playlist"
                result.refreshOutcome is RefreshOutcome.Failed -> " (playlist refresh failed)"
                result.refreshed -> " after playlist refresh"
                else -> ""
            }
            val base = when {
                err.errorCodeName == StreamError.STALL -> "Stream stalled: no video for 15 s"
                err.httpStatus != null -> "Stream returned ${err.httpStatus}"
                "CONNECTION_FAILED" in err.errorCodeName -> "Could not connect to the stream server"
                "TIMEOUT" in err.errorCodeName -> "The stream server did not respond in time"
                "PARSING" in err.errorCodeName -> "The stream data could not be read"
                "DECOD" in err.errorCodeName -> "This device could not decode the stream"
                else -> "Playback failed"
            }
            return base + after
        }

        /** Host + path, account id and tokens masked; no query string. */
        fun maskedStream(url: String): String = runCatching {
            val u = URI(url)
            "${u.host} ${SecretMasker.mask(u.rawPath)}"
        }.getOrDefault(SecretMasker.mask(url))

        private fun playlistLine(fetchedAt: Long?, last: RefreshRecord?, now: Long, zone: ZoneId): String {
            val age = fetchedAt?.let { "Age ${((now - it) / 60_000).coerceAtLeast(0)} min" } ?: "Not loaded"
            val refresh = last?.let { "Last refresh ${formatMinutes(it.atMillis, zone)}, ${if (it.ok) "OK" else "failed: ${it.message}"}" } ?: "No refresh yet"
            return "$age · $refresh"
        }

        private val seconds = DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM)
        private val minutes = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

        private fun formatSeconds(millis: Long, zone: ZoneId) = fmt(seconds, millis, zone)
        private fun formatMinutes(millis: Long, zone: ZoneId) = fmt(minutes, millis, zone)

        private fun fmt(f: DateTimeFormatter, millis: Long, zone: ZoneId) =
            Instant.ofEpochMilli(millis).atZone(zone).format(f).replace(' ', ' ').replace(' ', ' ')
    }
}
