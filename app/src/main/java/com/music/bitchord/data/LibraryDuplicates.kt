package com.music.bitchord.data

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.durationMillis
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/**
 * Spotting the same recording reached two ways — a track in the YouTube Music
 * library that is also an MP3 on the device, say — so the All library can show
 * it once.
 *
 * Two songs are the same when their titles and lead artists match once the
 * noise is taken out (case, punctuation, accents, "(Official Audio)" and the
 * like, a featured credit), and their lengths are within
 * [DURATION_TOLERANCE_MS] of each other. A song with no known length matches
 * on the names alone.
 */
object LibraryDuplicates {
    private const val DURATION_TOLERANCE_MS = 3_000L

    /** Bracketed asides that say nothing about which recording it is. */
    private val NOISE = Regex(
        "[(\\[][^)\\]]*(official|audio|video|lyric|visuali[sz]er|explicit|clean|remaster|hd|hq|4k|mv|m/v)[^)\\]]*[)\\]]",
        RegexOption.IGNORE_CASE,
    )
    private val FEATURING = Regex("\\s[(\\[]?(feat\\.?|ft\\.?|featuring)\\s.*$", RegexOption.IGNORE_CASE)
    private val ARTIST_SPLIT = Regex("\\s*(,|&|;|/|\\sx\\s|\\sand\\s|\\sfeat\\.?\\s|\\sft\\.?\\s|\\sfeaturing\\s)\\s*", RegexOption.IGNORE_CASE)
    private val MARKS = Regex("\\p{M}+")
    private val NOT_WORD = Regex("[^\\p{L}\\p{N}]+")

    private fun fold(text: String): String =
        Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(MARKS, "")
            .replace(NOT_WORD, "")

    fun titleKey(title: String): String {
        val stripped = title.replace(NOISE, " ").replace(FEATURING, "")
        return fold(stripped).ifEmpty { fold(title) }
    }

    fun artistKey(artist: String): String {
        val lead = artist.removeSuffix(" - Topic").split(ARTIST_SPLIT).firstOrNull().orEmpty()
        return fold(lead).ifEmpty { fold(artist) }
    }

    /** Whether [a] and [b] are the same recording by the rules above. */
    fun sameSong(a: Song, b: Song): Boolean {
        if (titleKey(a.title) != titleKey(b.title)) return false
        if (artistKey(a.artist) != artistKey(b.artist)) return false
        return closeInLength(a, b)
    }

    private fun closeInLength(a: Song, b: Song): Boolean {
        val first = a.durationMillis()
        val second = b.durationMillis()
        return first <= 0L || second <= 0L || abs(first - second) <= DURATION_TOLERANCE_MS
    }

    /**
     * This list with every detected duplicate left out. The first of each set
     * is the one kept, so a caller lists the copy it would rather play first —
     * the one on the device, ahead of the stream.
     */
    fun List<Song>.withoutDetectedDuplicates(): List<Song> {
        val kept = HashMap<String, MutableList<Song>>()
        return filter { song ->
            val key = titleKey(song.title) + "\u0000" + artistKey(song.artist)
            val same = kept.getOrPut(key) { mutableListOf() }
            if (same.any { closeInLength(it, song) }) {
                false
            } else {
                same += song
                true
            }
        }
    }
}
