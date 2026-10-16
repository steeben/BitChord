package com.music.bitchord

import com.music.bitchord.data.lyrics.SongMatch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongMatchTest {

    @Test
    fun `another song off the same album is not a match`() {
        assertFalse(
            SongMatch.matches(
                wantedTitle = "Flaws in Our Design",
                wantedArtist = "ODESZA",
                wantedDurationMs = 224_000,
                title = "Heavier",
                artist = "ODESZA",
                durationMs = 226_000,
            ),
        )
    }

    @Test
    fun `the same song with different casing and credits matches`() {
        assertTrue(
            SongMatch.matches(
                wantedTitle = "Flaws in Our Design",
                wantedArtist = "ODESZA",
                wantedDurationMs = 224_000,
                title = "Flaws In Our Design (feat. Someone)",
                artist = "ODESZA, Someone",
                durationMs = 225_000,
            ),
        )
    }

    @Test
    fun `a far longer cut is not a match`() {
        assertFalse(
            SongMatch.matches("Song", "Artist", 200_000, "Song", "Artist", 260_000),
        )
    }

    @Test
    fun `a short title must match whole words`() {
        assertFalse(SongMatch.sameTitle("Home", "Homesick"))
        assertTrue(SongMatch.sameTitle("Bohemian Rhapsody", "Bohemian Rhapsody - Remastered 2011"))
    }
}
