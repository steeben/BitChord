package com.music.bitchord.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.CARD_ART_PX
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.SearchPlayingBars
import com.music.bitchord.ui.components.contextClick
import com.music.bitchord.ui.components.longPressMenuClickable
import com.music.bitchord.ui.components.thumbnailBorder
import com.music.bitchord.ui.icons.BitChordIcons

/** The title a shelf is drawn as the speed dial under. */
internal const val SPEED_DIAL_SHELF_TITLE = "Speed dial"

private const val SPEED_DIAL_COLUMNS = 3
private const val SPEED_DIAL_PER_PAGE = 9

/**
 * YouTube Music's speed dial: a 3x3 grid of tiles, swiped sideways through
 * further pages of nine.
 */
@Composable
internal fun SpeedDialShelf(
    shelf: HomeShelf,
    pinnedIds: Set<String>,
    onItemClick: (ShelfItem) -> Unit,
    onItemLongPress: ((ShelfItem) -> Unit)?,
    currentSong: Song?,
    isPlaying: Boolean,
) {
    val pages = shelf.items.chunked(SPEED_DIAL_PER_PAGE)
    if (pages.isEmpty()) return
    val pagerState = rememberPagerState { pages.size }
    Column(Modifier.padding(bottom = 26.dp)) {
        SectionHeader(shelf.title, shelf.subtitle)
        HorizontalPager(
            state = pagerState,
            pageSpacing = 12.dp,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = PAGE_GUTTER),
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                pages[page].chunked(SPEED_DIAL_COLUMNS).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { item ->
                            SpeedDialTile(
                                item = item,
                                pinned = (item.videoId ?: item.browseId) in pinnedIds,
                                current = currentSong != null && item.videoId == currentSong.videoId,
                                isPlaying = isPlaying,
                                onClick = { onItemClick(item) },
                                onLongPress = onItemLongPress?.let { { it(item) } },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // Keep the last row's tiles the same size as the rest.
                        repeat(SPEED_DIAL_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (pages.size > 1) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
            ) {
                repeat(pages.size) { index ->
                    val selected = index == pagerState.currentPage
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.onBackground.copy(alpha = if (selected) 0.9f else 0.3f),
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedDialTile(
    item: ShelfItem,
    pinned: Boolean,
    current: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .thumbnailBorder(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .longPressMenuClickable(onClick = onClick, onLongClick = onLongPress)
            .contextClick(onLongPress),
    ) {
        AsyncImage(
            model = item.thumbnailUrl.artworkAt(CARD_ART_PX),
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))),
        )
        Text(
            text = item.title,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
        if (pinned) {
            Icon(
                imageVector = BitChordIcons.Pin,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(14.dp),
            )
        }
        if (current && isPlaying) SearchPlayingBars(Modifier.align(Alignment.Center))
    }
}
