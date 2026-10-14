package com.music.bitchord.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.floor
import kotlin.math.roundToInt

/** The title a shelf is drawn as the speed dial under. */
internal const val SPEED_DIAL_SHELF_TITLE = "Speed dial"

private const val SPEED_DIAL_COLUMNS = 3
private val TILE_GAP = 8.dp

/** A little air above the grid; the shelf has no heading of its own any more. */
internal val SPEED_DIAL_HEADER_HEIGHT = 4.dp

/** The page dots' row. Reserved even with a single page, for the same reason. */
private val SPEED_DIAL_DOTS_HEIGHT = 16.dp

/** How close to a side the finger has to be, mid-drag, to turn the page. */
private val EDGE_PAGE_ZONE = 36.dp

/** [inset] is the space either side of a grid that had to shrink to fit. */
internal data class SpeedDialMetrics(val tileWidth: Dp, val tileHeight: Dp, val inset: Dp)

/**
 * The tiles' size for a grid [contentWidth] wide with [rows] rows.
 *
 * Tiles are always square. When [maxShelfHeight] — everything the shelf may
 * take, down to its page dots — is too short for the full-width squares, they
 * shrink (never below 80% of full size) and the grid centres, so the whole
 * grid sits above the miniplayer instead of running under it.
 */
internal fun speedDialMetrics(contentWidth: Dp, rows: Int, maxShelfHeight: Dp?): SpeedDialMetrics {
    val full = (contentWidth - TILE_GAP * (SPEED_DIAL_COLUMNS - 1)) / SPEED_DIAL_COLUMNS
    var side = full
    if (maxShelfHeight != null) {
        val fit = (maxShelfHeight - SPEED_DIAL_HEADER_HEIGHT - SPEED_DIAL_DOTS_HEIGHT - TILE_GAP * (rows - 1)) / rows
        side = minOf(full, maxOf(fit, full * 0.8f))
    }
    val inset = (contentWidth - (side * SPEED_DIAL_COLUMNS + TILE_GAP * (SPEED_DIAL_COLUMNS - 1))) / 2
    return SpeedDialMetrics(side, side, inset)
}

/**
 * Space to leave above the grid, below the top bar: [DIAL_TOP_LEAD], or less
 * when the shelf has less room than that to spare.
 */
internal fun speedDialLead(metrics: SpeedDialMetrics, rows: Int, maxShelfHeight: Dp?): Dp {
    if (maxShelfHeight == null) return 0.dp
    val used = SPEED_DIAL_HEADER_HEIGHT + metrics.tileHeight * rows + TILE_GAP * (rows - 1) + SPEED_DIAL_DOTS_HEIGHT
    return (maxShelfHeight - used).coerceIn(0.dp, DIAL_TOP_LEAD)
}

private val DIAL_TOP_LEAD = 42.dp

private fun ShelfItem.dialId(): String = videoId ?: browseId ?: title

/**
 * YouTube Music's speed dial: a grid of tiles three across and [rows] down,
 * swiped sideways through further pages.
 *
 * Held tiles that are pinned can be dragged, as on a phone's home screen: hold,
 * then move, and the others make way. Holding at either side turns the page.
 * Lifting without having moved opens the tile's menu instead.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SpeedDialShelf(
    shelf: HomeShelf,
    pinnedIds: Set<String>,
    rows: Int,
    /** What the shelf may take of the screen, heading to page dots; null for no limit. */
    maxShelfHeight: Dp?,
    onItemClick: (ShelfItem) -> Unit,
    onItemLongPress: ((ShelfItem) -> Unit)?,
    /** The pinned tiles in their new order, after a drag. */
    onReorder: ((List<ShelfItem>) -> Unit)?,
    currentSong: Song?,
    isPlaying: Boolean,
) {
    if (shelf.items.isEmpty()) return
    val perPage = SPEED_DIAL_COLUMNS * rows
    // The order on screen: the shelf's, until a drag changes it.
    var order by remember(shelf.items) { mutableStateOf(shelf.items) }
    val pageCount = ((order.size + perPage - 1) / perPage).coerceAtLeast(1)
    val latestPageCount by rememberUpdatedState(pageCount)
    val pagerState = rememberPagerState { latestPageCount }
    val pinnedCount = remember(order, pinnedIds) { order.takeWhile { it.dialId() in pinnedIds }.size }
    val latestPinned by rememberUpdatedState(pinnedCount)
    val latestOrder by rememberUpdatedState(order)
    val latestReorder by rememberUpdatedState(onReorder)
    val latestMenu by rememberUpdatedState(onItemLongPress)
    val latestItems by rememberUpdatedState(shelf.items)

    var dragId by remember { mutableStateOf<String?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var grab by remember { mutableStateOf(Offset.Zero) }
    var edge by remember { mutableIntStateOf(0) }

    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current

    // Turn the page while the finger rests at a side.
    LaunchedEffect(edge, dragId) {
        if (dragId == null || edge == 0) return@LaunchedEffect
        while (true) {
            delay(600)
            val target = (pagerState.currentPage + edge).coerceIn(0, latestPageCount - 1)
            if (target != pagerState.currentPage) pagerState.animateScrollToPage(target)
            delay(400)
        }
    }

    Column(Modifier.padding(bottom = 66.dp)) {
        Spacer(Modifier.height(SPEED_DIAL_HEADER_HEIGHT))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val metrics = speedDialMetrics(maxWidth - PAGE_GUTTER * 2, rows, maxShelfHeight)
            val lead = speedDialLead(metrics, rows, maxShelfHeight)
            val tileW = metrics.tileWidth
            val tileH = metrics.tileHeight
            val gridHeight = tileH * rows + TILE_GAP * (rows - 1)
            val tileWPx = with(density) { tileW.toPx() }
            val tileHPx = with(density) { tileH.toPx() }
            val gapPx = with(density) { TILE_GAP.toPx() }
            val gutterPx = with(density) { PAGE_GUTTER.toPx() }
            val insetPx = with(density) { metrics.inset.toPx() }
            val widthPx = with(density) { maxWidth.toPx() }
            val edgePx = with(density) { EDGE_PAGE_ZONE.toPx() }

            fun cellOrigin(cell: Int) = Offset(
                insetPx + (cell % SPEED_DIAL_COLUMNS) * (tileWPx + gapPx),
                (cell / SPEED_DIAL_COLUMNS) * (tileHPx + gapPx),
            )

            /** The index in the whole order under [pos], or null in a gap or past the last tile. */
            fun hit(pos: Offset, page: Int): Int? {
                val x = pos.x - gutterPx - insetPx
                val col = floor(x / (tileWPx + gapPx)).toInt()
                val row = floor(pos.y / (tileHPx + gapPx)).toInt()
                if (col !in 0 until SPEED_DIAL_COLUMNS || row !in 0 until rows) return null
                if (x - col * (tileWPx + gapPx) > tileWPx || pos.y - row * (tileHPx + gapPx) > tileHPx) return null
                return (page * perPage + row * SPEED_DIAL_COLUMNS + col).takeIf { it < latestOrder.size }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = lead)
                    .pointerInput(rows, tileWPx, tileHPx, insetPx) {
                        awaitEachGesture {
                            // Watched from the first pass, ahead of the tiles and the
                            // pager, so that once a hold has landed the drag is ours
                            // and neither takes it for a tap or a swipe.
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                var done = false
                                while (!done) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    done = change == null || change.changedToUp() ||
                                        event.changes.size > 1 ||
                                        (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                                }
                                true
                            }
                            if (ended != null) return@awaitEachGesture // not a hold

                            val page = pagerState.currentPage
                            val index = hit(down.position, page) ?: return@awaitEachGesture
                            val item = latestOrder[index]
                            // Tiles that are not pinned have no place to be dragged
                            // to; their own hold opens the menu as it always did.
                            if (latestReorder == null || index >= latestPinned) return@awaitEachGesture

                            val id = item.dialId()
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            dragId = id
                            dragPos = down.position
                            grab = down.position - cellOrigin(index - page * perPage) - Offset(gutterPx, 0f)
                            var moved = false
                            var finished = false
                            try {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    change.consume()
                                    if (change.changedToUp()) {
                                        finished = true
                                        if (moved) {
                                            latestReorder?.invoke(latestOrder.take(latestPinned))
                                        } else {
                                            latestMenu?.invoke(item)
                                        }
                                        break
                                    }
                                    dragPos = change.position
                                    if (!moved &&
                                        (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                                    ) {
                                        moved = true
                                    }
                                    if (moved && !pagerState.isScrollInProgress) {
                                        val x = dragPos.x - gutterPx - insetPx
                                        val col = floor(x / (tileWPx + gapPx)).toInt().coerceIn(0, SPEED_DIAL_COLUMNS - 1)
                                        val row = floor(dragPos.y / (tileHPx + gapPx)).toInt().coerceIn(0, rows - 1)
                                        val target = (pagerState.currentPage * perPage + row * SPEED_DIAL_COLUMNS + col)
                                            .coerceIn(0, (latestPinned - 1).coerceAtLeast(0))
                                        val current = latestOrder.indexOfFirst { it.dialId() == id }
                                        if (current >= 0 && target != current) {
                                            order = latestOrder.toMutableList().apply { add(target, removeAt(current)) }
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    }
                                    edge = when {
                                        !moved -> 0
                                        dragPos.x < edgePx -> -1
                                        dragPos.x > widthPx - edgePx -> 1
                                        else -> 0
                                    }
                                }
                            } finally {
                                if (!finished) order = latestItems
                                dragId = null
                                edge = 0
                            }
                        }
                    },
            ) {
                HorizontalPager(
                    state = pagerState,
                    pageSpacing = 12.dp,
                    contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
                    verticalAlignment = Alignment.Top,
                    userScrollEnabled = dragId == null,
                    modifier = Modifier.fillMaxWidth(),
                ) { page ->
                    Box(Modifier.fillMaxWidth().height(gridHeight)) {
                        order.drop(page * perPage).take(perPage).forEachIndexed { cell, item ->
                            key(item.dialId()) {
                                val target = cellOrigin(cell).let { IntOffset(it.x.roundToInt(), it.y.roundToInt()) }
                                val offset by animateIntOffsetAsState(
                                    targetValue = target,
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                    label = "speedDialTile",
                                )
                                val id = item.dialId()
                                SpeedDialTile(
                                    item = item,
                                    pinned = id in pinnedIds,
                                    current = currentSong != null && item.videoId == currentSong.videoId,
                                    isPlaying = isPlaying,
                                    onClick = { onItemClick(item) },
                                    // A pinned tile's hold belongs to the drag above.
                                    onLongPress = if (onReorder != null && id in pinnedIds) {
                                        null
                                    } else {
                                        onItemLongPress?.let { { it(item) } }
                                    },
                                    modifier = Modifier
                                        .offset { offset }
                                        .size(tileW, tileH)
                                        .graphicsLayer { alpha = if (dragId == id) 0f else 1f },
                                )
                            }
                        }
                    }
                }

                // The tile in the finger's hand, lifted out of the grid.
                val liftedId = dragId
                if (liftedId != null) {
                    order.firstOrNull { it.dialId() == liftedId }?.let { item ->
                        SpeedDialTile(
                            item = item,
                            pinned = true,
                            current = false,
                            isPlaying = false,
                            onClick = {},
                            onLongPress = null,
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        (dragPos.x - grab.x).roundToInt(),
                                        (dragPos.y - grab.y).roundToInt(),
                                    )
                                }
                                .size(tileW, tileH)
                                .graphicsLayer {
                                    scaleX = 1.08f
                                    scaleY = 1.08f
                                }
                                .shadow(14.dp, RoundedCornerShape(10.dp)),
                        )
                    }
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(SPEED_DIAL_DOTS_HEIGHT)
                .padding(top = 10.dp),
        ) {
            if (pageCount > 1) {
                repeat(pageCount) { index ->
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
