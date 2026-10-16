package com.music.bitchord.data.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.math.abs

/**
 * NetEase Cloud Music: a large Chinese catalogue that also carries most of the
 * English one, and whose newer lyrics ("YRC") time every word.
 *
 * Two unauthenticated calls: a song search, and the lyric document for the
 * closest hit. The document holds the YRC alongside an ordinary LRC; the YRC
 * is taken where it exists and the LRC where it does not, so a song NetEase
 * only has line timings for still lights up line by line.
 */
object NetEase {

    private const val SEARCH = "https://music.163.com/api/cloudsearch/pc"
    private const val LYRIC = "https://music.163.com/api/song/lyric/v1"
    private const val DURATION_TOLERANCE_MS = 8_000L

    private val HEADERS = mapOf(
        "Referer" to "https://music.163.com/",
        "Cookie" to "appver=2.9.7; os=pc",
        "Accept" to "application/json",
    )

    suspend fun lyrics(
        title: String,
        artist: String,
        durationMs: Long,
        album: String? = null,
    ): List<LyricLine>? = withContext(Dispatchers.IO) {
        val ids = search(title, artist, durationMs, album)
        // The closest two are worth a document each: the first is often an
        // instrumental or a cut NetEase never got lyrics for.
        ids.take(2).firstNotNullOfOrNull(::lyricsFor)
    }

    private fun search(title: String, artist: String, durationMs: Long, album: String?): List<Long> {
        val url = SEARCH.toHttpUrl().newBuilder()
            .addQueryParameter("s", "$title $artist")
            .addQueryParameter("type", "1")
            .addQueryParameter("limit", "10")
            .addQueryParameter("offset", "0")
            .build()
        val body = lyricsGetWithHeaders(url.toString(), HEADERS) ?: return emptyList()
        val root = runCatching { lyricsJson.parseToJsonElement(body) as? JsonObject }.getOrNull()
            ?: return emptyList()
        val songs = (root["result"] as? JsonObject)?.get("songs") as? JsonArray ?: return emptyList()
        val wantArtist = artist.matchKey()
        val wantTitle = title.matchKey()
        return songs.mapNotNull { it as? JsonObject }
            .mapNotNull { song ->
                val id = song["id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                // `dt`/`ar`/`al` from cloudsearch; the older search shape
                // spells them out, and either may come back.
                val length = (song["dt"] ?: song["duration"])?.jsonPrimitive?.longOrNull ?: 0L
                if (durationMs > 0 && length > 0 && abs(length - durationMs) > DURATION_TOLERANCE_MS) {
                    return@mapNotNull null
                }
                val artists = ((song["ar"] ?: song["artists"]) as? JsonArray).orEmpty()
                    .mapNotNull { (it as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull?.matchKey() }
                val name = song["name"]?.jsonPrimitive?.contentOrNull?.matchKey().orEmpty()
                val albumName = ((song["al"] ?: song["album"]) as? JsonObject)
                    ?.get("name")?.jsonPrimitive?.contentOrNull?.matchKey()
                // The same title, not just the same artist at the same length:
                // another song off the same album passes both of those.
                val rawName = song["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (!SongMatch.sameTitle(title, rawName)) return@mapNotNull null
                var score = 0
                if (artists.any { it.isNotEmpty() && (wantArtist.contains(it) || it.contains(wantArtist)) }) score += 4
                if (name == wantTitle) score += 3 else if (name.isNotEmpty() && wantTitle.contains(name)) score += 1
                if (album != null && albumName != null && albumName == album.matchKey()) score += 1
                // An artist that matches nobody is a cover, not the song.
                if (artists.isNotEmpty() && score < 4) return@mapNotNull null
                Triple(id, score, abs(length - durationMs))
            }
            .sortedWith(compareByDescending<Triple<Long, Int, Long>> { it.second }.thenBy { it.third })
            .map { it.first }
    }

    private fun lyricsFor(id: Long): List<LyricLine>? {
        val url = LYRIC.toHttpUrl().newBuilder()
            .addQueryParameter("id", id.toString())
            .addQueryParameter("cp", "false")
            .addQueryParameter("lv", "0")
            .addQueryParameter("kv", "0")
            .addQueryParameter("tv", "0")
            .addQueryParameter("rv", "0")
            .addQueryParameter("yv", "0")
            .addQueryParameter("ytv", "0")
            .addQueryParameter("yrv", "0")
            .build()
        val body = lyricsGetWithHeaders(url.toString(), HEADERS) ?: return null
        val root = runCatching { lyricsJson.parseToJsonElement(body) as? JsonObject }.getOrNull()
            ?: return null
        fun lyric(key: String) = (root[key] as? JsonObject)?.get("lyric")?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() }

        lyric("yrc")?.let { yrc ->
            KaraokeLrc.parse(yrc).stripCredits().takeIf { lines -> lines.any { it.isWordSynced } }
                ?.let { return it }
        }
        return lyric("lrc")?.let { LrcLib.parseLrc(it) }?.stripCredits()
            ?.takeIf { lines -> lines.any { it.timeMs > 0 && it.text.isNotBlank() } }
    }

    private fun String.matchKey(): String =
        lowercase().replace(Regex("""[(\[（].*?[)\]）]"""), "").replace(Regex("""[^\p{L}\p{N}]"""), "")
}

/**
 * The credits the Chinese catalogues open a song with — "作词 : …", "Composer:
 * …", "Producer：…" — each stamped as though it were sung. Cut from the head and
 * the tail of the song only, and only while they look like "label: value", so a
 * line of the lyric with a colon in it is left alone.
 */
internal fun List<LyricLine>.stripCredits(): List<LyricLine> {
    val credit = Regex("""^[^:：]{1,24}[:：].+$""")
    fun isCredit(line: LyricLine) = line.text.isNotBlank() && credit.matches(line.text.trim())
    var head = 0
    while (head < size && head < 30 && (isCredit(this[head]) || this[head].text.isBlank())) head++
    var tail = size
    while (tail > head && size - tail < 30 && (isCredit(this[tail - 1]) || this[tail - 1].text.isBlank())) tail--
    val body = subList(head, tail)
    return if (body.any { it.text.isNotBlank() }) body.toList() else this
}
