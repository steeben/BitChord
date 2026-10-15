package com.music.bitchord.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.CARD_ART_PX
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.LibrarySource
import com.music.bitchord.data.stats.ListeningStats
import com.music.bitchord.data.stats.ReplayPeriod
import com.music.bitchord.sharedui.resources.Res
import com.music.bitchord.sharedui.resources.spotify_logo
import com.music.bitchord.ui.components.LIBRARY_GRID_SPACING
import com.music.bitchord.ui.components.MessageState
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.PopupLayer
import com.music.bitchord.ui.components.PullToRefresh
import com.music.bitchord.ui.components.TopBarContentHeight
import com.music.bitchord.ui.components.glassContentColor
import com.music.bitchord.ui.components.isGlassSupported
import com.music.bitchord.ui.components.liquidGlass
import com.music.bitchord.ui.components.longPressMenuClickable
import com.music.bitchord.ui.components.popupSurface
import com.music.bitchord.ui.components.thumbnailBorder
import com.music.bitchord.ui.icons.BitChordIcons
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.random.Random

/**
 * The Library tab, laid out after Apple Music's: "Library" with the picked
 * library's mark beside it, a 3×2 grid of things to go back to, then the
 * menu — Playlists, Artists, Albums, Songs, Spotify — and whichever extra
 * folders ([extraLinks]) are set up.
 *
 * Which library everything here reads from is [source]: YouTube Music, the
 * device, or both. Tapping the mark opens [LibrarySourcePicker].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryHomeScreen(
    source: LibrarySource,
    signedIn: Boolean,
    /** Null while listening history is still being read. */
    suggestions: List<LibrarySuggestion>?,
    accent: Color,
    listState: LazyListState,
    onSourceClick: () -> Unit,
    onSuggestionClick: (ShelfItem) -> Unit,
    onSuggestionLongPress: (ShelfItem) -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenArtists: () -> Unit,
    onOpenAlbums: () -> Unit,
    onOpenSongs: () -> Unit,
    onOpenSpotify: () -> Unit,
    extraLinks: List<LibraryLink>,
    onLinkClick: (ShelfItem) -> Unit,
    onSignIn: () -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    pullState: PullToRefreshState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    // The heading sits level with the top bar's buttons, the way Apple Music's
    // does, rather than under them: the bar has nothing at its leading end on
    // this page, so the space is the heading's.
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    PullToRefresh(
        refreshing = refreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = modifier,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = statusBar, bottom = contentPadding.calculateBottomPadding()),
        ) {
            item(key = "heading") {
                LibraryHeading(source = source, accent = accent, onSourceClick = onSourceClick)
            }
            item(key = "suggestions") {
                SuggestionGrid(
                    suggestions = suggestions,
                    onClick = onSuggestionClick,
                    onLongPress = onSuggestionLongPress,
                )
            }
            item(key = "menu") {
                val rows = buildList {
                    add(MenuRow(stringResource(R.string.playlists), Icons.AutoMirrored.Rounded.QueueMusic, onClick = onOpenPlaylists))
                    add(MenuRow(stringResource(R.string.artists), Icons.Rounded.Mic, onClick = onOpenArtists))
                    add(MenuRow(stringResource(R.string.albums), Icons.Rounded.Album, onClick = onOpenAlbums))
                    add(MenuRow(stringResource(R.string.songs), Icons.Rounded.MusicNote, onClick = onOpenSongs))
                    add(MenuRow(stringResource(R.string.spotify), icon = null, spotify = true, onClick = onOpenSpotify))
                    extraLinks.forEach { link ->
                        add(MenuRow(link.item.title, link.icon, onClick = { onLinkClick(link.item) }))
                    }
                }
                LibraryMenu(rows = rows, accent = accent)
            }
            if (!signedIn && source == LibrarySource.YOUTUBE_MUSIC) {
                item(key = "sign_in") {
                    MessageState(
                        message = stringResource(R.string.library_sign_in_description),
                        actionLabel = stringResource(R.string.sign_in),
                        onAction = onSignIn,
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryHeading(source: LibrarySource, accent: Color, onSourceClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(TopBarContentHeight)
            // Clear of the history button and account photo at the other end.
            .padding(start = PAGE_GUTTER, end = 120.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onSourceClick),
        ) {
            LibrarySourceIcon(source = source, tint = accent, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.library),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSourceClick,
            ),
        )
    }
}

/** One cell of the grid: an album or playlist, or an artist drawn round. */
data class LibrarySuggestion(val item: ShelfItem, val round: Boolean = false)

private const val GRID_COLUMNS = 3
private const val GRID_ROWS = 2
private const val GRID_SIZE = GRID_COLUMNS * GRID_ROWS

@Composable
private fun SuggestionGrid(
    suggestions: List<LibrarySuggestion>?,
    onClick: (ShelfItem) -> Unit,
    onLongPress: (ShelfItem) -> Unit,
) {
    // Nothing played yet and nothing saved: no grid at all, rather than six
    // grey squares that never fill in.
    if (suggestions != null && suggestions.isEmpty()) return
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_GUTTER)
            .padding(top = 8.dp, bottom = 10.dp),
    ) {
        val cell = (maxWidth - LIBRARY_GRID_SPACING * (GRID_COLUMNS - 1)) / GRID_COLUMNS
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            for (row in 0 until GRID_ROWS) {
                Row(horizontalArrangement = Arrangement.spacedBy(LIBRARY_GRID_SPACING)) {
                    for (column in 0 until GRID_COLUMNS) {
                        val index = row * GRID_COLUMNS + column
                        if (suggestions == null) {
                            SuggestionPlaceholder(cell)
                        } else {
                            val suggestion = suggestions.getOrNull(index)
                            if (suggestion != null) {
                                SuggestionCell(suggestion, cell, onClick, onLongPress)
                            } else {
                                Spacer(Modifier.width(cell))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionCell(
    suggestion: LibrarySuggestion,
    width: Dp,
    onClick: (ShelfItem) -> Unit,
    onLongPress: (ShelfItem) -> Unit,
) {
    val shape = if (suggestion.round) CircleShape else RoundedCornerShape(10.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(width)
            .longPressMenuClickable(
                onClick = { onClick(suggestion.item) },
                onLongClick = { onLongPress(suggestion.item) },
            ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .thumbnailBorder(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = suggestion.item.thumbnailUrl.artworkAt(CARD_ART_PX),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = suggestion.item.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SuggestionPlaceholder(width: Dp) {
    Column(Modifier.width(width)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .6f)),
        )
        Spacer(Modifier.height(6.dp))
        Spacer(Modifier.height(20.dp))
    }
}

private class MenuRow(
    val title: String,
    val icon: ImageVector?,
    val spotify: Boolean = false,
    val onClick: () -> Unit,
)

private val MENU_ICON_SIZE = 26.dp
private val MENU_ICON_GAP = 16.dp

/** Icon, name, chevron, hairlines between — the rows under the grid. */
@Composable
private fun LibraryMenu(rows: List<MenuRow>, accent: Color) {
    Column(Modifier.padding(top = 4.dp, bottom = 22.dp)) {
        rows.forEachIndexed { index, row ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .clickable(onClick = row.onClick)
                    .padding(horizontal = PAGE_GUTTER),
            ) {
                if (row.spotify) {
                    Icon(
                        painter = org.jetbrains.compose.resources.painterResource(Res.drawable.spotify_logo),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(MENU_ICON_SIZE),
                    )
                } else if (row.icon != null) {
                    Icon(
                        imageVector = row.icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(MENU_ICON_SIZE),
                    )
                }
                Spacer(Modifier.width(MENU_ICON_GAP))
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = BitChordIcons.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .6f),
                    modifier = Modifier.size(20.dp),
                )
            }
            if (index < rows.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = PAGE_GUTTER + MENU_ICON_SIZE + MENU_ICON_GAP),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

/**
 * The library switcher the Library heading's mark opens: YouTube Music, Local
 * (the device's files and the downloads) and All, on a liquid-glass panel
 * that grows out of the mark. Picking one is remembered for next time.
 */
@Composable
fun LibrarySourcePicker(
    visible: Boolean,
    selected: LibrarySource,
    signedIn: Boolean,
    hazeState: HazeState,
    accent: Color,
    onSelect: (LibrarySource) -> Unit,
    onDismiss: () -> Unit,
) {
    val shape = MaterialTheme.shapes.extraLarge
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val glass = isGlassSupported() && !reduceDynamicBlur
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    PopupLayer(
        visible = visible,
        onDismiss = onDismiss,
        contentAlignment = Alignment.TopStart,
        origin = TransformOrigin(0f, 0f),
    ) {
        Surface(
            color = Color.Transparent,
            contentColor = if (glass) glassContentColor() else MaterialTheme.colorScheme.onSurface,
            shape = shape,
            modifier = Modifier
                .padding(top = statusBar + TopBarContentHeight, start = PAGE_GUTTER)
                .width(IntrinsicSize.Max)
                .then(
                    if (isGlassSupported()) {
                        Modifier.clip(shape).liquidGlass(shape)
                    } else {
                        Modifier.popupSurface(shape, hazeState)
                    },
                )
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                ),
        ) {
            Column(Modifier.padding(vertical = 8.dp)) {
                LibrarySource.entries.forEach { source ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clickable(role = Role.Button) { onSelect(source) }
                            .padding(start = 18.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
                    ) {
                        LibrarySourceIcon(
                            source = source,
                            tint = if (source == selected) accent else androidx.compose.material3.LocalContentColor.current,
                            modifier = Modifier.size(26.dp),
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f, fill = false).padding(end = 16.dp)) {
                            Text(source.label(), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = stringResource(
                                    when (source) {
                                        LibrarySource.YOUTUBE_MUSIC -> if (signedIn) {
                                            R.string.library_source_youtube_music_subtitle
                                        } else {
                                            R.string.library_source_youtube_music_signed_out
                                        }
                                        LibrarySource.LOCAL -> R.string.library_source_local_subtitle
                                        LibrarySource.ALL -> R.string.library_source_all_subtitle
                                    },
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = androidx.compose.material3.LocalContentColor.current.copy(alpha = .7f),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.size(22.dp)) {
                            if (source == selected) {
                                Icon(Icons.Rounded.Check, contentDescription = null, tint = accent)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Suggestions ─────────────────────────────────────────────────────────────

/** What has been played, read once from [ListeningStats] each time the page opens. */
private class ListeningActivity(
    /** Newest first. */
    val recentAlbums: List<LibrarySuggestion>,
    val recentArtists: List<LibrarySuggestion>,
    /** Most played first. */
    val topAlbums: List<LibrarySuggestion>,
    val topArtists: List<LibrarySuggestion>,
    /** Album names, folded, for matching against the device's releases. */
    val recentAlbumNames: Set<String>,
    val topAlbumNames: Set<String>,
)

private fun String.folded(): String = lowercase(Locale.ROOT).trim()

/** A YouTube Music page id, rather than a device grouping or nothing. */
private fun String?.isRemoteBrowseId(): Boolean =
    !isNullOrBlank() && !startsWith("local:") && !contains("://")

private suspend fun readListeningActivity(): ListeningActivity = withContext(Dispatchers.IO) {
    val summary = runCatching { ListeningStats.summary(ReplayPeriod.ALL_TIME) }.getOrNull()
    val recent = runCatching { ListeningStats.recentTracks(RECENT_TRACKS) }.getOrDefault(emptyList())
    val artistsById = summary?.artists.orEmpty()
        .filter { it.browseId.isRemoteBrowseId() }
        .associateBy { it.browseId!! }
    val recentAlbums = recent
        .filter { it.albumId.isRemoteBrowseId() && !it.album.isNullOrBlank() }
        .distinctBy { it.albumId }
        .map {
            LibrarySuggestion(ShelfItem(it.album!!, it.artist, it.art, null, it.albumId))
        }
    // Only artists with a picture of their own: a round crop of a track's
    // sleeve would pass for the artist and not be them.
    val recentArtists = recent
        .mapNotNull { track -> track.artistId?.let(artistsById::get) }
        .distinctBy { it.browseId }
        .filter { !it.artworkUrl.isNullOrBlank() }
        .map { LibrarySuggestion(ShelfItem(it.title, it.subtitle.orEmpty(), it.artworkUrl, null, it.browseId), round = true) }
    val topAlbums = summary?.albums.orEmpty()
        .filter { it.browseId.isRemoteBrowseId() }
        .take(TOP_ENTRIES)
        .map { LibrarySuggestion(ShelfItem(it.title, it.subtitle.orEmpty(), it.artworkUrl, null, it.browseId)) }
    val topArtists = artistsById.values
        .filter { !it.artworkUrl.isNullOrBlank() }
        .sortedByDescending { it.ms }
        .take(TOP_ENTRIES)
        .map { LibrarySuggestion(ShelfItem(it.title, it.subtitle.orEmpty(), it.artworkUrl, null, it.browseId), round = true) }
    ListeningActivity(
        recentAlbums = recentAlbums,
        recentArtists = recentArtists,
        topAlbums = topAlbums,
        topArtists = topArtists,
        recentAlbumNames = recent.mapNotNull { it.album?.folded() }.toSet(),
        topAlbumNames = summary?.albums.orEmpty().take(TOP_ENTRIES * 2).map { it.title.folded() }.toSet(),
    )
}

private const val RECENT_TRACKS = 80
private const val TOP_ENTRIES = 12

/** How many of the six each kind of pick gets before the rest are filled at random. */
private const val PER_KIND = 2

/**
 * The six cells of the grid, for [source]: a mix of what was played lately,
 * what is played most, and playlists, drawn from YouTube Music's side
 * ([ytShelves], the account's library) and the device's ([deviceItems], the
 * releases downloaded whole and the app's own playlists) as [source] allows.
 *
 * Dealt afresh each time the page opens — the seed is held for as long as the
 * page is on screen, so a refresh underneath doesn't reshuffle what is being
 * looked at. Null until listening history has been read.
 */
@Composable
fun rememberLibrarySuggestions(
    source: LibrarySource,
    ytShelves: List<HomeShelf>,
    deviceItems: List<ShelfItem>,
): List<LibrarySuggestion>? {
    var activity by remember { mutableStateOf<ListeningActivity?>(null) }
    LaunchedEffect(Unit) { activity = readListeningActivity() }
    val seed = remember { Random.nextLong() }
    return remember(activity, source, ytShelves, deviceItems, seed) {
        activity?.let { dealSuggestions(it, source, ytShelves, deviceItems, Random(seed)) }
    }
}

private fun dealSuggestions(
    activity: ListeningActivity,
    source: LibrarySource,
    ytShelves: List<HomeShelf>,
    deviceItems: List<ShelfItem>,
    random: Random,
): List<LibrarySuggestion> {
    val youTube = source != LibrarySource.LOCAL
    val device = source != LibrarySource.YOUTUBE_MUSIC

    fun shelf(title: String) = ytShelves.firstOrNull { it.title == title }?.items.orEmpty()
    val ytPlaylists = if (youTube) shelf(YtMusicRepository.PLAYLISTS_SHELF).map { LibrarySuggestion(it) } else emptyList()
    val ytSaved = if (youTube) {
        shelf("Albums").map { LibrarySuggestion(it) } +
            (shelf("Artists") + shelf("Subscriptions")).map { LibrarySuggestion(it, round = true) }
    } else {
        emptyList()
    }
    val deviceCards = if (device) deviceItems.map { LibrarySuggestion(it) } else emptyList()

    val recent = buildList {
        if (youTube) {
            addAll(activity.recentAlbums.take(TOP_ENTRIES))
            addAll(activity.recentArtists.take(TOP_ENTRIES / 2))
        }
        addAll(deviceCards.filter { it.item.title.folded() in activity.recentAlbumNames })
    }
    val often = buildList {
        if (youTube) {
            addAll(activity.topAlbums)
            addAll(activity.topArtists)
        }
        addAll(deviceCards.filter { it.item.title.folded() in activity.topAlbumNames })
    }
    val playlists = ytPlaylists + deviceCards
    val rest = ytSaved

    val picked = LinkedHashMap<String, LibrarySuggestion>()
    fun take(pool: List<LibrarySuggestion>, count: Int) {
        var added = 0
        for (candidate in pool.shuffled(random)) {
            if (added >= count || picked.size >= GRID_SIZE) return
            val id = candidate.item.browseId ?: continue
            if (id in picked) continue
            picked[id] = candidate
            added++
        }
    }
    take(recent, PER_KIND)
    take(often, PER_KIND)
    take(playlists, PER_KIND)
    take(recent + often + playlists + rest, GRID_SIZE)
    return picked.values.toList().shuffled(random)
}
