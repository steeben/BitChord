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

/** The title the artists' dial goes by as a shelf: never shown, only matched on. */
const val ARTIST_DIAL_SHELF_TITLE = "Artist dial"

/** The artists' dial's key in Home's list: it is always the second shelf. */
const val ARTIST_DIAL_PAGE_KEY = ARTIST_DIAL_SHELF_TITLE + "1"

/**
 * Home's fling over its dial pages — the speed dial, then the artists' dial
 * when there is one: let go anywhere on one of them and the list settles on
 * that page or the next thing below it, like turning a page. Anywhere else it
 * is [fallback], the list's ordinary fling.
 *
 * [restOffsetPx] is where the speed dial sits with the list at its top,
 * measured from the content's start (the room the logo leaves above it);
 * [nextOffsetPx] is where anything after it comes to rest, clear of the top
 * bar — the artists' page included.
 */
internal class DialPageFling(
    private val listState: LazyListState,
    private val fallback: FlingBehavior,
    private val restOffsetPx: () -> Int,
    private val nextOffsetPx: () -> Int,
) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val next = nextOffsetPx()
        // The page that holds the top of the screen: its own resting place at
        // or above where it is now, and some of it still showing below where
        // the following item would rest.
        val page = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            val rest = if (item.key in SPEED_DIAL_PAGE_KEYS) restOffsetPx() else next
            (item.key in SPEED_DIAL_PAGE_KEYS || item.key == ARTIST_DIAL_PAGE_KEY) &&
                item.offset <= rest && item.offset + item.size > next
        } ?: return with(fallback) { performFling(initialVelocity) }
        val rest = if (page.key in SPEED_DIAL_PAGE_KEYS) restOffsetPx() else next
        // Scrolling by these brings the page back to rest, or what follows it
        // up to the top.
        val toTop = (page.offset - rest).toFloat()
        val toNext = (page.offset + page.size - next).toFloat()
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
