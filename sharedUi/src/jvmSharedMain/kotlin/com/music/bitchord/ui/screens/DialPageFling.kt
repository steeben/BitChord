package com.music.bitchord.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.lazy.LazyListState
import kotlin.math.abs

/** A shelf's key in Home's list: by title, so shelves landing above it don't re-key it. */
internal fun homeShelfKey(title: String): String = "shelf:$title"

/** Keys the speed dial's page goes by in Home's list, real or placeholder. */
internal val SPEED_DIAL_PAGE_KEYS: Set<Any> = setOf(homeShelfKey(SPEED_DIAL_SHELF_TITLE), "skeleton:speed-dial")

/** The title the artists' dial goes by as a shelf: never shown, only matched on. */
const val ARTIST_DIAL_SHELF_TITLE = "Artist dial"

/** The artists' dial's key in Home's list. */
val ARTIST_DIAL_PAGE_KEY: String = homeShelfKey(ARTIST_DIAL_SHELF_TITLE)

/**
 * Home's fling over its dial pages — the speed dial, then the artists' dial
 * when there is one: let go anywhere on one of them and the list settles on
 * that page or the next thing below it, like turning a page. Anywhere else it
 * is [fallback], the list's ordinary fling.
 *
 * [restOffsetPx] is where a dial page sits, measured from the content's start
 * (the room the logo leaves above it) — both dials rest there, so the artists'
 * dial sits exactly where the speed dial does. [nextOffsetPx] is where
 * anything after the dials comes to rest, clear of the top bar.
 * [artistPageFollows] says whether the speed dial has the artists' dial after it.
 */
internal class DialPageFling(
    private val listState: LazyListState,
    private val fallback: FlingBehavior,
    private val restOffsetPx: () -> Int,
    private val nextOffsetPx: () -> Int,
    private val artistPageFollows: () -> Boolean = { false },
) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val dialRest = restOffsetPx()
        val next = nextOffsetPx()
        fun isDial(key: Any) = key in SPEED_DIAL_PAGE_KEYS || key == ARTIST_DIAL_PAGE_KEY
        // Where whatever follows a dial page comes to rest.
        fun followingRest(key: Any) =
            if (key in SPEED_DIAL_PAGE_KEYS && artistPageFollows()) dialRest else next
        // The page that holds the top of the screen: its own resting place at
        // or above where it is now, and some of it still showing below where
        // the following item would rest.
        val page = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            isDial(item.key) && item.offset <= dialRest && item.offset + item.size > followingRest(item.key)
        } ?: return with(fallback) { performFling(initialVelocity) }
        // Scrolling by these brings the page back to rest, or what follows it
        // up to its own.
        val toTop = (page.offset - dialRest).toFloat()
        val toNext = (page.offset + page.size - followingRest(page.key)).toFloat()
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
