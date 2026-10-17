package com.music.bitchord

import com.music.bitchord.data.DuplicateChoices
import com.music.bitchord.data.LibraryDuplicates
import com.music.bitchord.data.LibraryDuplicates.withDuplicatesMerged
import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryDuplicatesTest {
    private fun stream(id: String, title: String, artist: String, length: String? = "4:28") =
        Song(
            videoId = id,
            title = title,
            artist = artist,
            thumbnailUrl = "https://yt/$id",
            durationText = length,
            albumId = "MPRE$id",
            albumName = "YT album",
        )

    private fun file(n: Int, title: String, artist: String, length: String? = "4:27") =
        Song(
            videoId = "content://media/external/audio/media/$n",
            title = title,
            artist = artist,
            thumbnailUrl = "content://media/external/audio/albumart/$n",
            durationText = length,
            albumName = "File album",
            localUri = "content://media/external/audio/media/$n",
            localPath = "/sdcard/Music/$title.mp3",
            localDateAddedSeconds = 100L + n,
        )

    @Test
    fun creditsInEitherOrderAreTheSameSong() {
        val a = file(1, "A Real Hero", "Electric Youth, College")
        val b = stream("aaaaaaaaaaa", "A Real Hero", "College, Electric Youth")
        assertTrue(LibraryDuplicates.sameSong(a, b))
        assertEquals(1, LibraryDuplicates.detect(listOf(a, b)).size)
    }

    @Test
    fun lengthsAFewSecondsApartStillMatch() {
        val a = file(1, "A Sky Full of Stars", "Coldplay", "4:28")
        val b = stream("bbbbbbbbbbb", "A Sky Full Of Stars", "Coldplay", "4:37")
        assertTrue(LibraryDuplicates.sameSong(a, b))
    }

    @Test
    fun aSameNamedSongOfAnotherLengthIsNotADuplicate() {
        val a = file(1, "Intro", "Artist", "1:10")
        val b = stream("ccccccccccc", "Intro", "Artist", "3:40")
        assertTrue(LibraryDuplicates.detect(listOf(a, b)).isEmpty())
    }

    @Test
    fun aDifferentArtistIsNotADuplicate() {
        val a = file(1, "Home", "Artist One")
        val b = stream("ddddddddddd", "Home", "Artist Two")
        assertTrue(LibraryDuplicates.detect(listOf(a, b)).isEmpty())
    }

    @Test
    fun mergedSongShowsYouTubeDetailsAndPlaysTheFile() {
        val local = file(1, "A Real Hero", "Electric Youth, College")
        val yt = stream("eeeeeeeeeee", "A Real Hero", "College, Electric Youth")
        val other = file(2, "Other", "Someone")
        val merged = listOf(local, other, yt).withDuplicatesMerged()
        assertEquals(2, merged.size)
        val song = merged.first()
        assertEquals("eeeeeeeeeee", song.videoId)
        assertEquals("College, Electric Youth", song.artist)
        assertEquals("https://yt/eeeeeeeeeee", song.thumbnailUrl)
        assertEquals("YT album", song.albumName)
        assertEquals(local.localUri, song.localUri)
        assertEquals(local.localPath, song.localPath)
        assertEquals(other, merged[1])
    }

    @Test
    fun choicesCanStreamShowTheFileOrKeepCopiesApart() {
        val local = file(1, "Song", "Artist")
        val yt = stream("fffffffffff", "Song", "Artist")
        val songs = listOf(local, yt)
        val key = LibraryDuplicates.detect(songs).single().key

        val streamed = songs.withDuplicatesMerged(DuplicateChoices(playStream = setOf(key))).single()
        assertEquals("fffffffffff", streamed.videoId)
        assertNull(streamed.localUri)

        val tagged = songs.withDuplicatesMerged(DuplicateChoices(fileDetails = setOf(key))).single()
        assertEquals("File album", tagged.albumName)
        assertEquals(local.localUri, tagged.localUri)

        assertEquals(songs, songs.withDuplicatesMerged(DuplicateChoices(keptApart = setOf(key))))
    }

    @Test
    fun aKeyNamesTheSameSetWhateverTheOrder() {
        val local = file(1, "Song", "Artist")
        val yt = stream("ggggggggggg", "Song", "Artist")
        assertEquals(
            LibraryDuplicates.detect(listOf(local, yt)).single().key,
            LibraryDuplicates.detect(listOf(yt, local)).single().key,
        )
    }
}
