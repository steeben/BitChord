package com.music.bitchord.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The artwork of the track in the player, for whatever is tinted after it.
 *
 * Set by the app as the track changes; read through [nowPlayingAccent].
 */
object NowPlayingArtwork {
    val url = MutableStateFlow<String?>(null)
}

/**
 * The playing song's colour: its artwork's accent, contrast-corrected for the
 * theme the way a release page's is (see [rememberArtworkPalette]). The
 * theme's primary while nothing is playing, and while a new sleeve is read
 * the colour fades across rather than jumping.
 *
 * One answer for everything that follows the song — the Library's logo and
 * menu icons, Home's mark — so they always agree.
 */
@Composable
fun nowPlayingAccent(): Color {
    val url by NowPlayingArtwork.url.collectAsStateWithLifecycle()
    return rememberArtworkPalette(url).accent
}
