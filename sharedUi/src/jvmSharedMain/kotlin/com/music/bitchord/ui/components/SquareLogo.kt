package com.music.bitchord.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.exp
import kotlin.math.min

/** The logo is three squares across and four down, in a 3:4 box. */
const val SQUARE_LOGO_ASPECT = 3f / 4f

/** Columns and rows of the lit (true) and dim (false) squares that make the mark. */
private val LOGO_LIT = arrayOf(
    booleanArrayOf(false, true, true),
    booleanArrayOf(false, true, false),
    booleanArrayOf(true, true, false),
    booleanArrayOf(true, true, false),
)

private const val DIM_ALPHA = 0.2f

/**
 * Where each square sits on the loop the spinner runs round: the ten squares
 * on the edge of the grid in order, then the two in the middle placed
 * opposite one another. Indexed by `row * 3 + column`.
 */
private val LOOP_POSITION = floatArrayOf(
    0f, 1f, 2f,
    9f, 3f, 3f,
    8f, 8f, 4f,
    7f, 6f, 5f,
).also {
    // The middle column's two inner squares share the loop with the edge.
    it[4] = 3.0f // row 1, column 1
    it[7] = 8.0f // row 2, column 1
}

/** One lap of the spinner. */
private const val SPIN_SECONDS = 1.1f

/** How the lit squares trail behind the head of the spinner, in squares. */
private const val TRAIL_LENGTH = 2.4f

/**
 * The app's mark, drawn as squares so it can come apart and go back together.
 *
 * At rest it is the logo: the lit squares solid, the others faint. Pulled down
 * it dims and lights square by square along the loop with [pullFraction];
 * while [refreshing] a bright head with a tail chases round the grid; and when
 * the refresh ends the squares settle back into the logo's layout.
 */
@Composable
fun SquareLogo(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onBackground,
    refreshing: Boolean = false,
    pullFraction: () -> Float = { 0f },
) {
    val spinWeight = remember { Animatable(0f) }
    LaunchedEffect(refreshing) {
        if (refreshing) {
            spinWeight.animateTo(1f, tween(180))
        } else {
            spinWeight.animateTo(0f, tween(750, easing = FastOutSlowInEasing))
        }
    }
    var phase by remember { mutableFloatStateOf(0f) }
    val currentRefreshing by rememberUpdatedState(refreshing)
    val animating by remember { derivedStateOf { currentRefreshing || spinWeight.value > 0.001f } }
    LaunchedEffect(animating) {
        if (!animating) return@LaunchedEffect
        var last = androidx.compose.runtime.withFrameNanos { it }
        while (true) {
            val now = androidx.compose.runtime.withFrameNanos { it }
            phase = (phase + (now - last) / 1_000_000_000f / SPIN_SECONDS) % 1f
            last = now
        }
    }

    Canvas(modifier = modifier.aspectRatio(SQUARE_LOGO_ASPECT)) {
        // The logo's own 180 x 240 grid: 40-unit squares every 60.
        val unit = min(size.width / 180f, size.height / 240f)
        val cell = 40f * unit
        val pitch = 60f * unit
        val originX = (size.width - (2 * pitch + cell)) / 2f
        val originY = (size.height - (3 * pitch + cell)) / 2f
        val corner = CornerRadius(8f * unit)
        val pull = pullFraction().coerceIn(0f, 2f)
        val spin = spinWeight.value
        val head = phase * LOOP_LENGTH
        val litCells = pull.coerceAtMost(1f) * LOOP_CELLS
        val pullBlend = (pull * 5f).coerceIn(0f, 1f)

        for (row in 0 until 4) {
            for (col in 0 until 3) {
                val index = row * 3 + col
                val rest = if (LOGO_LIT[row][col]) 1f else DIM_ALPHA
                val loopAt = LOOP_POSITION[index]

                // Pulled: everything dims, then lights along the loop.
                val pulled = DIM_ALPHA + (1f - DIM_ALPHA) * (litCells - loopAt).coerceIn(0f, 1f)
                val afterPull = rest + (pulled - rest) * pullBlend

                // Refreshing: a head and its tail running round the loop.
                var behind = (head - loopAt) % LOOP_LENGTH
                if (behind < 0f) behind += LOOP_LENGTH
                val spinning = DIM_ALPHA + (1f - DIM_ALPHA) * exp(-behind / TRAIL_LENGTH)

                val alpha = afterPull + (spinning - afterPull) * spin
                drawRoundRect(
                    color = color.copy(alpha = color.alpha * alpha),
                    topLeft = Offset(originX + col * pitch, originY + row * pitch),
                    size = Size(cell, cell),
                    cornerRadius = corner,
                )
            }
        }
    }
}

private const val LOOP_LENGTH = 10f
private const val LOOP_CELLS = 10f

/** The mark's height at rest, in the list's first row. */
val HOME_LOGO_LARGE = 60.dp

/** And pinned, once the list has been scrolled. */
val HOME_LOGO_SMALL = 34.dp

/** Space the list reserves at its top for the mark, so the first section clears it. */
val HOME_LOGO_RESERVE = 72.dp

/**
 * The mark on Home: in the list's first row at rest, shrinking as the list
 * scrolls and then staying at the top left, level with the account button.
 *
 * Meant to be drawn above the top bar, which is why it is not part of the list:
 * the fade the bar lays over scrolled content would otherwise wash it out.
 * [topPadding] is the list's own top content padding, from which the mark's
 * two resting places are measured.
 */
@Composable
fun HomeStickyLogo(
    listState: LazyListState,
    topPadding: Dp,
    refreshing: Boolean,
    pullFraction: () -> Float,
    modifier: Modifier = Modifier,
    /** False hides the mark itself (not [trailing]), while the launch screen's is on its way here. */
    showMark: Boolean = true,
    /** Drawn beside the mark and shrinking with it, e.g. the app's name. */
    trailing: @Composable RowScope.() -> Unit = {},
) {
    // Where the mark sits at rest, and where it ends up pinned: centred on the
    // top bar, whose content is 52dp high with TopBarContentGap (12dp) below it.
    val restTop = topPadding - 20.dp
    val pinnedTop = topPadding - 12.dp - 26.dp - HOME_LOGO_SMALL / 2
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            // Clear of the account button on the right.
            .padding(start = PAGE_GUTTER, end = 84.dp)
            .height(HOME_LOGO_LARGE)
            .graphicsLayer {
                val travel = (restTop - pinnedTop).toPx()
                val scrolled = if (listState.firstVisibleItemIndex == 0) {
                    listState.firstVisibleItemScrollOffset.toFloat()
                } else {
                    travel
                }
                val t = (scrolled / travel).coerceIn(0f, 1f)
                val scale = 1f + (HOME_LOGO_SMALL / HOME_LOGO_LARGE - 1f) * t
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = scale
                scaleY = scale
                translationY = restTop.toPx() - scrolled.coerceAtMost(travel)
            },
    ) {
        SquareLogo(
            refreshing = refreshing,
            pullFraction = pullFraction,
            modifier = Modifier
                .fillMaxHeight()
                .graphicsLayer { alpha = if (showMark) 1f else 0f },
        )
        trailing()
    }
}
