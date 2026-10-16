package com.music.bitchord.data.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Inflater
import kotlin.math.abs
import kotlin.math.min

/**
 * Lyrics from KuGou's public mobile/lyrics endpoints — a Chinese
 * catalogue, but one that also carries a great many English and Hindi tracks
 * that the other four sources simply don't have.
 *
 * Three unauthenticated calls, chained: search the song to get its audio
 * fingerprint (`hash`), search lyrics candidates against that hash, then
 * download the winning candidate. A search by keyword alone (skipping the
 * hash) is kept as the fallback that catches everything the first two miss.
 *
 * The candidate is asked for KRC first, KuGou's karaoke format, which times
 * every word; a candidate that only has an LRC falls back to whole lines.
 */
object KuGou {

    private const val DURATION_TOLERANCE_SECONDS = 8

    suspend fun lyrics(
        title: String,
        artist: String,
        durationMs: Long,
        album: String? = null,
    ): List<LyricLine>? = withContext(Dispatchers.IO) {
        val keyword = keyword(title, artist, album)
        val seconds = (durationMs / 1000).toInt()

        // Every candidate has to be this song by title: the keyword carries the
        // album, and KuGou answers it with the album's other tracks as readily
        // as with the one asked for.
        fun isThisSong(name: String?, singer: String?) =
            name.isNullOrBlank() || SongMatch.sameTitle(title, name) &&
                (singer.isNullOrBlank() || SongMatch.sharesArtist(artist, singer))
        val candidate = searchSongs(keyword, seconds, ::isThisSong)?.firstNotNullOfOrNull { hash ->
            // Already this song: the hash came from a search that was checked.
            searchLyrics(hash = hash)?.firstOrNull()
        } ?: searchLyrics(keyword = keyword, seconds = seconds)
            ?.firstOrNull { isThisSong(it.song, it.singer) && it.closeTo(seconds) }
            ?: return@withContext null

        download(candidate.id, candidate.accesskey, "krc")
            ?.let(::decodeKrc)
            ?.let(::parseKrc)
            ?.stripCredits()
            ?.takeIf { lines -> lines.any { it.isWordSynced } }
            ?.let { return@withContext it }

        val lrc = download(candidate.id, candidate.accesskey, "lrc")
            ?.let { String(it, Charsets.UTF_8).stripCredits() }
            ?: return@withContext null
        LrcLib.parseLrc(lrc).takeIf { it.isNotEmpty() }
    }

    /**
     * A KRC file as it comes down the wire: "krc1", then the document XORed
     * with a fixed key and deflated. Null for anything that is not one.
     */
    private fun decodeKrc(bytes: ByteArray): String? = runCatching {
        if (bytes.size <= 4 || String(bytes, 0, 4, Charsets.US_ASCII) != "krc1") return null
        val body = ByteArray(bytes.size - 4) { i -> (bytes[i + 4].toInt() xor KRC_KEY[i % KRC_KEY.size]).toByte() }
        val inflater = Inflater()
        inflater.setInput(body)
        val out = ByteArrayOutputStream(body.size * 4)
        val buffer = ByteArray(8 * 1024)
        while (!inflater.finished()) {
            val read = inflater.inflate(buffer)
            if (read == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
            out.write(buffer, 0, read)
        }
        inflater.end()
        out.toString(Charsets.UTF_8.name()).removePrefix("\uFEFF")
    }.getOrNull()

    /**
     * KRC lines are `[start,duration]<offset,duration,0>word…`, the word stamps
     * counted from the line's own start. Rewritten with absolute starts they
     * are the same `(start,duration,0)word` runs QRC and YRC use, and parsed
     * as those.
     */
    fun parseKrc(krc: String): List<LyricLine> {
        val converted = krc.lineSequence().mapNotNull { raw ->
            val line = KRC_LINE.matchEntire(raw.trim()) ?: return@mapNotNull null
            val lineStart = line.groupValues[1].toLong()
            val body = KRC_WORD.replace(line.groupValues[3]) { word ->
                "(${lineStart + word.groupValues[1].toLong()},${word.groupValues[2]},0)"
            }
            "[${line.groupValues[1]},${line.groupValues[2]}]$body"
        }.joinToString("\n")
        return KaraokeLrc.parse(converted)
    }

    private val KRC_LINE = Regex("""^\[(\d+),(\d+)](.*)$""")
    private val KRC_WORD = Regex("""<(\d+),(\d+),\d+>""")
    private val KRC_KEY = intArrayOf(64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45, 206, 210, 110, 105)

    /**
     * Song hashes worth trying, restricted to cuts within
     * [DURATION_TOLERANCE_SECONDS] of the track being played — otherwise the
     * first result for a common title is as likely to be a cover or a remix
     * as the right recording — and ordered closest match first.
     */
    private fun searchSongs(
        keyword: Keyword,
        seconds: Int,
        isThisSong: (String?, String?) -> Boolean,
    ): List<String>? {
        val url = "https://mobileservice.kugou.com/api/v3/search/song".toHttpUrl().newBuilder()
            .addQueryParameter("version", "9108")
            .addQueryParameter("plat", "0")
            .addQueryParameter("pagesize", "8")
            .addQueryParameter("showtype", "0")
            .addQueryParameter("keyword", keyword.query)
            .build()
        val body = lyricsGet(url.toString()) ?: return null
        val response = runCatching { lyricsJson.decodeFromString<SearchSongResponse>(body) }.getOrNull()
        return response?.data?.info.orEmpty()
            .filter { seconds <= 0 || abs(it.duration - seconds) <= DURATION_TOLERANCE_SECONDS }
            .filter { isThisSong(it.songname ?: it.filename?.substringAfter(" - "), it.singername) }
            .sortedBy { abs(it.duration - seconds) }
            .map { it.hash }
    }

    private fun searchLyrics(hash: String? = null, keyword: Keyword? = null, seconds: Int = -1): List<Candidate>? {
        val builder = "https://lyrics.kugou.com/search".toHttpUrl().newBuilder()
            .addQueryParameter("ver", "1")
            .addQueryParameter("man", "yes")
            .addQueryParameter("client", "pc")
        when {
            hash != null -> builder.addQueryParameter("hash", hash)
            keyword != null -> {
                builder.addQueryParameter("keyword", keyword.query)
                if (seconds > 0) builder.addQueryParameter("duration", (seconds * 1000).toString())
            }
            else -> return null
        }
        val body = lyricsGet(builder.build().toString()) ?: return null
        val response = runCatching { lyricsJson.decodeFromString<SearchLyricsResponse>(body) }.getOrNull()
        return response?.candidates
    }

    private fun download(id: String, accessKey: String, format: String): ByteArray? {
        val url = "https://lyrics.kugou.com/download".toHttpUrl().newBuilder()
            .addQueryParameter("fmt", format)
            .addQueryParameter("charset", "utf8")
            .addQueryParameter("client", "pc")
            .addQueryParameter("ver", "1")
            .addQueryParameter("id", id)
            .addQueryParameter("accesskey", accessKey)
            .build()
        val body = lyricsGet(url.toString()) ?: return null
        val response = runCatching { lyricsJson.decodeFromString<DownloadResponse>(body) }.getOrNull()
            ?: return null
        return runCatching { Base64.getDecoder().decode(response.content) }.getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun keyword(title: String, artist: String, album: String?) = Keyword(
        buildString {
            append(title.stripParenthetical())
            append(" - ")
            append(artist.stripParenthetical())
            if (!album.isNullOrBlank()) {
                append(' ')
                append(album)
            }
        },
    )

    private fun String.stripParenthetical(): String =
        replace(Regex("""[(（].*?[)）]"""), "").trim().ifBlank { this }

    /**
     * KuGou's lyric files open and close with uncredited lines — songwriter,
     * composer, arranger — that carry a real timestamp and would otherwise be
     * sung as the first and last lines of the song. Cut the same way the
     * source client does: from either end, up to the first/last line matching
     * "label: value", and only within the first and last 30 lines so a legit
     * lyric that happens to contain a colon deep in the song is left alone.
     */
    fun String.stripCredits(): String {
        val lines = lineSequence().filter { STAMPED.matches(it) }.toList()
        if (lines.isEmpty()) return ""
        val headLimit = min(30, lines.lastIndex)
        val headCut = (headLimit downTo 0).firstOrNull { CREDIT.matches(lines[it]) }?.let { it + 1 } ?: 0
        val body = lines.drop(headCut)
        val tailLimit = min(30, body.lastIndex)
        val tailCut = (0..tailLimit).firstOrNull { CREDIT.matches(body[body.lastIndex - it]) }?.let { it + 1 } ?: 0
        return body.dropLast(tailCut).joinToString("\n")
    }

    private val STAMPED = Regex("""\[\d{2}:\d{2}\.\d{2,3}].*""")
    private val CREDIT = Regex(""".+][^\[]+[:：].+""")

    private class Keyword(val query: String)

    @Serializable
    private data class SearchSongResponse(val data: Data? = null) {
        @Serializable
        data class Data(val info: List<Info> = emptyList())

        @Serializable
        data class Info(
            val hash: String,
            val duration: Int = -1,
            val songname: String? = null,
            val singername: String? = null,
            /** "Artist - Title", where [songname] is missing. */
            val filename: String? = null,
        )
    }

    @Serializable
    private data class SearchLyricsResponse(val candidates: List<Candidate> = emptyList())

    @Serializable
    private data class Candidate(
        val id: String,
        val accesskey: String,
        val song: String? = null,
        val singer: String? = null,
        /** Milliseconds. */
        val duration: Long? = null,
    ) {
        fun closeTo(seconds: Int): Boolean =
            seconds <= 0 || duration == null || duration <= 0 ||
                abs(duration / 1000 - seconds) <= DURATION_TOLERANCE_SECONDS
    }

    @Serializable
    private data class DownloadResponse(val content: String = "")
}
