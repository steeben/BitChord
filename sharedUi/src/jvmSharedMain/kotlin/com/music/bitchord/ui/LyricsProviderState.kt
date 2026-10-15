package com.music.bitchord.ui

/** What the current track's provider picker already knows without another request. */
enum class LyricsProviderState {
    NOT_FETCHED,
    FETCHING,
    FOUND,
    NOT_FOUND,
    /** Answered, but with two lines or fewer, and short answers are being ignored. */
    TOO_SHORT,
}
