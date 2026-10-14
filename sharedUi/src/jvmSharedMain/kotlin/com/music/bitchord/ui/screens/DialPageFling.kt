package com.music.bitchord.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.lazy.LazyListState
import kotlin.math.abs

/** Keys the speed dial's page goes by in Home's list, real or placeholder. */
internal val SPEED_DIAL_PAGE_KEYS: Set<Any> = setOf(SPEED_DIAL_SHELF_TITLE + "0", "skeleton:speed-dial")

/**
 * Home's fling while the speed dial is its first screen: let go anywhere on
 * that screen and the list settles either back on the dial or on the first
 * section below it, like turning a page. Anywhere else it is [fallback], the
 * list's ordinary fling.
 *
 * [restOffsetPx] is where the dial sits with the list at its top, measured
 * from the content's start (the room the logo leaves above it); [nextOffsetPx]
 * is where the section after it comes to rest, clear of the top bar.
 */
internal class DialPageFling(
    private val listState: LazyListState,
    private val fallback: FlingBehavior,
    private val restOffsetPx: () -> Int,
    private val nextOffsetPx: () -> Int,
) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val dial = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key in SPEED_DIAL_PAGE_KEYS }
        val rest = restOffsetPx()
        // Only while some of the dial's screen is still showing above where the
        // next section rests; from there on the list flings as usual.
        if (dial == null || dial.offset + dial.size <= nextOffsetPx() || dial.offset > rest) {
            return with(fallback) { performFling(initialVelocity) }
        }
        // Scrolling by these brings the dial back to rest, or the next section
        // up to the top.
        val toTop = (dial.offset - rest).toFloat()
        val toNext = (dial.offset + dial.size - nextOffsetPx()).toFloat()
        val forward = when {
            initialVelocity > PAGE_FLING_VELOCITY -> true
            initialVelocity < -PAGE_FLING_VELOCITY -> false
            // A slow release: whichever it is past a third of the way to.
            else -> -toTop > (toNext - toTop) / 3f
        }
        val target = if (forward) toNext else toTop
        if (abs(target) < 1f) return 0f
        var scrolled = 0f
        animate(
            initialValue = 0f,
            targetValue = target,
            initialVelocity = initialVelocity,
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        ) { value, _ ->
            scrolled += scrollBy(value - scrolled)
        }
        return 0f
    }
}

/** How fast a release has to be, in px/s, to count as a flick one way or the other. */
private const val PAGE_FLING_VELOCITY = 600f
