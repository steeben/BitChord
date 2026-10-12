package com.music.bitchord.data.innertube

import java.util.concurrent.ConcurrentHashMap

/**
 * Video ids known to be the signed-in user's own uploads to YouTube Music.
 *
 * An upload is not public media. Resolved like any other track it is refused
 * by every anonymous client, so InnerTubeX has to be told up front — through
 * `ContentHints.isUploaded` — to restrict itself to authenticated clients, try
 * the login cookies first and skip the embedded-player fallback, which cannot
 * see a private library. [StreamResolver] only receives a video id, so the
 * knowledge lives here rather than on the track.
 *
 * Filled two ways: by [InnertubeParser] when a row says it is an upload, and by
 * [StreamResolver] when a track that failed to resolve ordinarily resolves once
 * asked as an upload. The second is what keeps this working when YouTube moves
 * the marker in a row's JSON.
 *
 * Held in memory only. A track not yet known this run costs one extra attempt
 * the first time it is played, after which it is remembered.
 */
object UploadedTracks {

    private const val MAX_REMEMBERED = 20_000

    private val ids = ConcurrentHashMap.newKeySet<String>()

    fun mark(videoId: String) {
        if (videoId.isBlank()) return
        // Bounded so a very large library cannot grow this without limit; a
        // forgotten id only costs the extra attempt described above.
        if (ids.size >= MAX_REMEMBERED) ids.clear()
        ids.add(videoId)
    }

    fun contains(videoId: String): Boolean = videoId in ids

    /** Forgets everything — an account switch is a different private library. */
    fun clear() {
        ids.clear()
    }
}
