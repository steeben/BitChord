package com.music.bitchord.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.music.bitchord.data.settings.LibrarySource
import com.music.bitchord.ui.components.SQUARE_LOGO_ASPECT
import com.music.bitchord.ui.components.SquareLogo
import com.music.bitchord.ui.icons.BitChordIcons
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.sharedui.resources.Res
import com.music.bitchord.sharedui.resources.spotify_logo
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.download.Downloads
import com.music.bitchord.download.SavedCollection
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.ReplayCardRowSkeleton
import com.music.bitchord.ui.replay.ReplayCardRow
import com.music.bitchord.ui.replay.ReplayHeroCard
import com.music.bitchord.ui.replay.ReplayStoryPage

// The phone's halves of the shared Library page: its list of folders, what
// sits on its "On device" shelf, and the way in to Replay at its head.

/**
 * The Library menu's rows after Spotify: the song cache when Settings has it
 * shown, and the two remote libraries once they are set up. The libraries
 * themselves — YouTube Music, the device, both — are picked from the logo at
 * the head of the page instead; see [LibrarySource].
 */
@Composable
fun libraryExtraLinks(): List<LibraryLink> {
    val webdavConfigured by AppSettings.webdavUrl.collectAsStateWithLifecycle()
    val smbHost by AppSettings.smbHost.collectAsStateWithLifecycle()
    val smbShare by AppSettings.smbShare.collectAsStateWithLifecycle()
    val showCacheFolder by AppSettings.showCacheFolder.collectAsStateWithLifecycle()
    fun link(icon: ImageVector, title: String, subtitle: String, browseId: String) = LibraryLink(
        item = ShelfItem(
            title = title,
            subtitle = subtitle,
            thumbnailUrl = null,
            videoId = null,
            browseId = browseId,
        ),
        icon = icon,
    )
    return listOfNotNull(
        // Opt-in from Settings → Storage: what the song cache is holding from
        // YouTube and JioSaavn. See [com.music.bitchord.playback.AudioCache.cachedSongs].
        link(
            Icons.Rounded.Storage,
            stringResource(R.string.cached_songs),
            stringResource(R.string.cached_songs_subtitle),
            CACHE_FOLDER_BROWSE_ID,
        ).takeIf { showCacheFolder },
        // Only once set up in Sources; unconfigured, they'd just be dead ends.
        link(
            Icons.Rounded.Cloud,
            stringResource(R.string.webdav),
            stringResource(R.string.webdav_subtitle),
            com.music.bitchord.data.webdav.WebDavConfig.BROWSE_ID,
        ).takeIf { webdavConfigured.isNotBlank() },
        link(
            Icons.Rounded.Lan,
            stringResource(R.string.smb),
            stringResource(R.string.smb_subtitle),
            com.music.bitchord.data.smb.SmbConfig.BROWSE_ID,
        ).takeIf { smbHost.isNotBlank() && smbShare.isNotBlank() },
    )
}

/**
 * The phone's "On device" shelf: the playlists and albums downloaded whole —
 * the same promise the folders above it make, here, now, without a network.
 * Nothing is truncated: the shelf is a row that scrolls, so "all of them"
 * costs nothing.
 */
@Composable
fun libraryDeviceItems(downloadedReleases: List<SavedCollection>): List<ShelfItem> {
    val downloadedPlaylist = stringResource(R.string.downloaded_playlist)
    return downloadedReleases.map { release ->
        ShelfItem(
            title = release.title,
            // The credit the release was downloaded with, because this is also
            // what the page it opens bills itself by — see `headerLines`, which
            // reads the kind and the owner back out of it. Saying "Downloaded
            // playlist" here instead would make that header read "Downloaded
            // playlist" over "PLAYLIST • 12 SONGS", and the shelf this card is on
            // already says where it lives.
            subtitle = release.subtitle.ifBlank { if (release.playlist) downloadedPlaylist else "" },
            thumbnailUrl = release.thumbnailUrl,
            videoId = null,
            browseId = Downloads.pageIdFor(release.id),
        )
    }
}

/** The YouTube Music library folder: every song in the account's library, uploads included. */
const val YT_MUSIC_LIBRARY_BROWSE_ID = "local:ytmusic"

/** The Local library: the device's audio files and the downloads, as one list. */
const val LOCAL_LIBRARY_BROWSE_ID = "local:library"

/** The All library: YouTube Music's and the device's songs together, duplicates hidden by default. */
const val ALL_LIBRARY_BROWSE_ID = "local:everything"

/**
 * The page id an album or artist made of a library's own rows opens under —
 * see [libraryGroupId]. Opened with its songs already in hand, as a release
 * page rather than one of the tabbed device folders.
 */
const val LIBRARY_GROUP_PREFIX = "local:group:"

/** The page holding every song in [this] library, with its Songs / Artists / Albums tabs. */
val LibrarySource.browseId: String
    get() = when (this) {
        LibrarySource.YOUTUBE_MUSIC -> YT_MUSIC_LIBRARY_BROWSE_ID
        LibrarySource.LOCAL -> LOCAL_LIBRARY_BROWSE_ID
        LibrarySource.ALL -> ALL_LIBRARY_BROWSE_ID
    }

@Composable
fun LibrarySource.label(): String = stringResource(
    when (this) {
        LibrarySource.YOUTUBE_MUSIC -> R.string.youtube_music
        LibrarySource.LOCAL -> R.string.library_source_local
        LibrarySource.ALL -> R.string.library_source_all
    },
)

/**
 * A library's mark: YouTube Music's own for that one, the Downloads arrow for
 * the device's, and BitChord Dial's squares for both together.
 */
@Composable
fun LibrarySourceIcon(source: LibrarySource, tint: Color, modifier: Modifier = Modifier) {
    when (source) {
        LibrarySource.YOUTUBE_MUSIC -> Icon(
            painter = painterResource(R.drawable.ic_youtube_music),
            contentDescription = null,
            tint = tint,
            modifier = modifier,
        )
        LibrarySource.LOCAL -> Icon(
            imageVector = BitChordIcons.Download,
            contentDescription = null,
            tint = tint,
            modifier = modifier,
        )
        LibrarySource.ALL -> Box(modifier, contentAlignment = Alignment.Center) {
            SquareLogo(
                color = tint,
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(SQUARE_LOGO_ASPECT),
            )
        }
    }
}

/** The Library row that opens the connected Spotify account; handled by the app, not a browse page. */
const val SPOTIFY_BROWSE_ID = "app:spotify"

/** The Cached songs folder's page id — one of the `local:` device folders. */
const val CACHE_FOLDER_BROWSE_ID = "local:cache"

/**
 * The phone's way in to Replay: its row of headline cards, placeholders for
 * them while the history is read, and nothing at all while there is not yet
 * enough listening to deal them — Settings still opens Replay either way.
 */
@Composable
fun LibraryReplayEntry(
    cards: List<ReplayHeroCard>,
    loading: Boolean,
    holder: String,
    memberSince: String?,
    onOpenReplay: (ReplayStoryPage) -> Unit,
) {
    if (loading) {
        ReplayCardRowSkeleton()
    } else if (cards.isNotEmpty()) {
        ReplayCardRow(
            cards = cards,
            holder = holder,
            memberSince = memberSince,
            onCardClick = onOpenReplay,
            modifier = Modifier.padding(vertical = 6.dp),
            contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
        )
    }
}
