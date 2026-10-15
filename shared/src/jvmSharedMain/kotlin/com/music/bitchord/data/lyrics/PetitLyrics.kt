package com.music.bitchord.data.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Base64

/**
 * プチリリ (PetitLyrics): a Japanese catalogue, licensed, with word timing on
 * much of it — the strongest of these for J-pop and anime, where the others
 * are thinnest.
 *
 * One form POST asks for the best match by title and artist, at the finest
 * sync the service has ([TYPE_WORD]). The answer is XML with the lyric itself
 * base64'd inside it; at word sync that lyric is another XML document of lines
 * and their timed words. Whatever finer sync a song lacks, the service answers
 * with what it has, so the same parse reads line timing or plain text too.
 */
object PetitLyrics {

    private const val API = "https://p1.petitlyrics.com/api/GetPetitLyricsData.php"
    private const val CLIENT_ID = "p1110417"
    private const val TYPE_WORD = "3"

    suspend fun lyrics(
        title: String,
        artist: String,
        durationMs: Long,
        album: String? = null,
    ): List<LyricLine>? = withContext(Dispatchers.IO) {
        val fields = buildMap {
            put("key_title", title)
            put("key_artist", artist)
            if (!album.isNullOrBlank()) put("key_album", album)
            if (durationMs > 0) put("key_duration", durationMs.toString())
            put("lyricsType", TYPE_WORD)
            put("maxCount", "1")
            put("terminalType", "10")
            put("clientAppId", CLIENT_ID)
        }
        val response = lyricsPostForm(API, fields) ?: return@withContext null
        val data = tag(response, "lyricsData")?.trim()?.takeIf { it.isNotEmpty() }
            ?: return@withContext null
        val document = runCatching {
            String(Base64.getMimeDecoder().decode(data), Charsets.UTF_8)
        }.getOrNull() ?: return@withContext null
        parse(document).takeIf { lines -> lines.any { it.text.isNotBlank() } }
    }

    /** A word-sync document, or failing that the line-synced or plain text one. */
    fun parse(document: String): List<LyricLine> {
        if (!document.contains("<line", ignoreCase = true)) {
            return LrcLib.parseLrc(document).ifEmpty {
                document.lineSequence().map(String::trim).filter(String::isNotEmpty)
                    .map { LyricLine(0L, it) }.toList()
            }
        }
        val lines = LINE.findAll(document).mapNotNull { match ->
            val body = match.groupValues[1]
            val runs = WORD.findAll(body).mapNotNull { word ->
                val inner = word.groupValues[1]
                val start = tag(inner, "starttime")?.trim()?.toLongOrNull() ?: return@mapNotNull null
                val end = tag(inner, "endtime")?.trim()?.toLongOrNull() ?: start
                val text = tag(inner, "wordstring")?.let(::unescape) ?: return@mapNotNull null
                if (start < 0) return@mapNotNull null
                TimedRun(start, maxOf(start, end), text)
            }.filter { it.raw.isNotEmpty() }.toList()
            val text = (tag(body, "linestring")?.let(::unescape) ?: runs.joinToString("") { it.raw }).trim()
            when {
                runs.isNotEmpty() -> {
                    val words = wordsFromRuns(runs)
                    if (words.isEmpty()) return@mapNotNull null
                    LyricLine(timeMs = words.first().startMs, text = text, words = words)
                }
                text.isEmpty() -> null
                else -> {
                    // A line with no timed words of its own — some songs are
                    // only timed per line. Its stamp, if it has one, is the
                    // line's; otherwise there is nothing to sync it to.
                    val start = tag(body, "starttime")?.trim()?.toLongOrNull() ?: 0L
                    LyricLine(start, text)
                }
            }
        }.toList()
        val synced = lines.any { it.timeMs > 0 }
        return if (synced) lines.sortedBy { it.timeMs }.withInstrumentalGaps() else lines
    }

    private fun tag(xml: String, name: String): String? =
        Regex("""<$name>(.*?)</$name>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .find(xml)?.groupValues?.get(1)
            ?.let { it.removePrefix("<![CDATA[").removeSuffix("]]>") }

    private fun unescape(text: String) = EnhancedLrc.decodeEntities(text)

    private val LINE = Regex("""<line>(.*?)</line>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val WORD = Regex("""<word>(.*?)</word>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
}
