package com.music.bitchord.data.lyrics

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/**
 * Whether a search hit names the song being played, for the providers that
 * search by name and get back a list of candidates.
 *
 * Every test is a requirement, not a score. A search for one song on an album
 * routinely ranks another song from the same album, by the same artist, at
 * nearly the same length, first — "Flaws in Our Design" by ODESZA came back as
 * "Heavier" — and a score that rewards the artist and the length passes it. A
 * miss lets the next provider answer; the wrong song's words cannot be undone
 * by anything later in the lookup.
 *
 *  - The title, outside its brackets and any " - " suffix, is the same. One
 *    may contain the other when the shorter is still a real name, which keeps
 *    "Song" matching "Song (From the Film)" written without brackets.
 *  - At least one credited artist is shared, when the hit names any.
 *  - The length is within [toleranceMs], when both are known.
 */
object SongMatch {

    /** How far apart two lengths may be and still be one recording, by default. */
    const val DEFAULT_TOLERANCE_MS = 8_000L

    fun matches(
        wantedTitle: String,
        wantedArtist: String,
        wantedDurationMs: Long,
        title: String?,
        artist: String?,
        durationMs: Long?,
        toleranceMs: Long = DEFAULT_TOLERANCE_MS,
    ): Boolean {
        if (title.isNullOrBlank()) return false
        if (!sameTitle(wantedTitle, title)) return false
        if (!artist.isNullOrBlank() && !sharesArtist(wantedArtist, artist)) return false
        if (wantedDurationMs > 0 && durationMs != null && durationMs > 0 &&
            abs(durationMs - wantedDurationMs) > toleranceMs
        ) return false
        return true
    }

    fun sameTitle(wanted: String, candidate: String): Boolean {
        val a = coreOf(wanted)
        val b = coreOf(candidate)
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b) return true
        val (shorter, longer) = if (a.length <= b.length) a to b else b to a
        // A word-aligned containment of a name long enough to mean something.
        return shorter.length >= MIN_CONTAINED &&
            (" $longer ").contains(" $shorter ")
    }

    fun sharesArtist(wanted: String, candidate: String): Boolean {
        val want = artistsOf(wanted)
        if (want.isEmpty()) return true
        val have = artistsOf(candidate)
        if (have.isEmpty()) return true
        return have.any { name -> want.any { it == name || it.contains(name) || name.contains(it) } }
    }

    /** "Song (Live) - 2011 Remaster" → "song". */
    fun coreOf(title: String): String =
        normalized(title.replace(BRACKETED, " ").substringBefore(" - "))
            .ifEmpty { normalized(title) }

    fun artistsOf(artist: String): Set<String> =
        artist.split(ARTIST_SEPARATORS).map(::normalized).filter { it.isNotEmpty() }.toSet()

    /** Lower case, accents off ("ROSÉ" is "rose"), and only letters and digits. */
    fun normalized(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .lowercase(Locale.ROOT)
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .replace(WHITESPACE, " ")
            .trim()

    private const val MIN_CONTAINED = 4

    private val BRACKETED = Regex("""[(\[（][^)\]）]*[)\]）]""")
    private val COMBINING_MARKS = Regex("""\p{Mn}+""")
    private val WHITESPACE = Regex("""\s+""")
    private val ARTIST_SEPARATORS = Regex(
        """\s*(?:,|&|;|/|、|\s+and\s+|\s+x\s+|\s+with\s+|\s+feat\.?\s+|\s+ft\.?\s+)\s*""",
        RegexOption.IGNORE_CASE,
    )
}
