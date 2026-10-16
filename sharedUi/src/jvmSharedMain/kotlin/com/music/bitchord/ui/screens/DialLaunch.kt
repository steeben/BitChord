package com.music.bitchord.ui.screens

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext

/**
 * A dial tile that opens a page (an album, an artist, a playlist), as it was
 * when it was let go of: where it sat on screen ([bounds], in root
 * coordinates, at full size), how far it was pressed in, and which page of
 * which dial it was on, so Home can come back to that page for it to close into.
 */
class DialOrigin(
    /** The page it opens: its browse id, which is also the page's key. */
    val key: String,
    val bounds: Rect,
    val pressedScale: Float,
    val thumbnailUrl: String?,
    val shelfTitle: String,
    val page: Int,
)

/**
 * The phone-home-screen launch: a page opened from a dial tile grows out of
 * the tile, and closing it shrinks it back in. The tile is hidden for as long
 * as its page is open, as an app's icon is.
 */
object DialLaunch {
    var origin: DialOrigin? by mutableStateOf(null)

    /** The dial page Home should open on: the one a launched tile is on. */
    internal fun pageOf(shelfTitle: String): Int = origin?.takeIf { it.shelfTitle == shelfTitle }?.page ?: 0

    /** Whether the tile for [id] is out on its page, so not on the dial. */
    internal fun isOut(id: String): Boolean = origin?.key == id

    /** Whether going from page [from] to page [to] opens or closes the launched tile's page from [home]. */
    fun zooms(from: String, to: String, home: String): Boolean {
        val key = origin?.key ?: return false
        return (from == home && to == key) || (from == key && to == home)
    }

    const val OPEN_MS = 420
    const val CLOSE_MS = 360
}

/** Quick off the mark and long to settle, like a phone's own app launch. */
private val LaunchEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** The tile's corners, as drawn on the dial. */
private val TILE_CORNER = 10.dp

/**
 * One page of the app's content, grown out of / shrunk back into its dial
 * tile while [zooming]. [visibility] is the page's own enter/exit transition
 * (an AnimatedContent slot's), which carries the animation, so the page stays
 * up for as long as it lasts. Otherwise the page is drawn as it always is.
 */
@Composable
fun DialLaunchPage(
    key: String,
    zooming: Boolean,
    visibility: Transition<EnterExitState>,
    content: @Composable BoxScope.() -> Unit,
) {
    val closing = visibility.targetState == EnterExitState.PostExit
    val progress by visibility.animateFloat(
        transitionSpec = {
            if (zooming) tween(if (closing) DialLaunch.CLOSE_MS else DialLaunch.OPEN_MS, easing = LaunchEasing) else snap()
        },
        label = "dialLaunch",
    ) { if (it == EnterExitState.Visible) 1f else 0f }
    val origin = DialLaunch.origin?.takeIf { zooming && it.key == key }
    // Where this page sits on screen, to turn the tile's root position into one on the page.
    val pageAt = remember { mutableStateOf(Offset.Zero) }
    val ground = MaterialTheme.colorScheme.background
    Box(
        Modifier
            .onPlaced { pageAt.value = it.positionInRoot() }
            .then(
                if (origin == null) {
                    Modifier
                } else {
                    Modifier
                        .graphicsLayer {
                            val p = progress
                            // Out from the tile as it was let go of, pressed in;
                            // back into it at full size, as it will be drawn.
                            val from = if (closing) origin.bounds else origin.bounds.scaled(origin.pressedScale)
                            val tile = from.translate(-pageAt.value)
                            val w = size.width
                            val h = size.height
                            if (w <= 0f || h <= 0f) return@graphicsLayer
                            val scale = lerp(tile.width / w, 1f, p)
                            transformOrigin = TransformOrigin(0f, 0f)
                            scaleX = scale
                            scaleY = scale
                            translationX = lerp(tile.left, 0f, p)
                            translationY = lerp(tile.top, 0f, p)
                            // The window: the tile's shape growing to the page's,
                            // in the page's own (unscaled) units.
                            val shownHeight = lerp(tile.height, h, p) / scale
                            val radius = lerp(TILE_CORNER.toPx() * origin.pressedScale, 0f, p) / scale
                            shape = WindowShape(shownHeight, radius)
                            clip = true
                        }
                        // The page is see-through (the colour behind it belongs to
                        // the app), so while it is small it stands on solid ground,
                        // dissolving once it has filled the screen.
                        .drawBehind {
                            val solid = ((1f - progress) / 0.3f).coerceIn(0f, 1f)
                            if (solid > 0f) drawRect(ground.copy(alpha = solid))
                        }
                },
            ),
        propagateMinConstraints = true,
    ) {
        content()
        if (origin != null) {
            // The tile's own artwork over the top of the page, fading into
            // the page's banner as it opens, and back in as it closes.
            val context = LocalPlatformContext.current
            val request = remember(origin.thumbnailUrl, context) { speedDialArtRequest(context, origin.thumbnailUrl) }
            Box(
                Modifier
                    // Free of the page's full-size minimum, to be only as tall as the tile.
                    .wrapContentSize(Alignment.TopStart)
                    .fillMaxWidth()
                    .graphicsLayer { alpha = (1f - progress / 0.45f).coerceIn(0f, 1f) }
                    // As tall, against the page's width, as the tile is against its own.
                    .aspectRatio(origin.bounds.width / origin.bounds.height.coerceAtLeast(1f)),
            ) {
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
    }
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun Rect.scaled(factor: Float): Rect {
    val c = center
    val half = Size(width * factor / 2f, height * factor / 2f)
    return Rect(c.x - half.width, c.y - half.height, c.x + half.width, c.y + half.height)
}

/** The page's top [height], with [radius] corners: what of it shows while it grows. */
private class WindowShape(val height: Float, val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(
            RoundRect(0f, 0f, size.width, height.coerceAtMost(size.height), CornerRadius(radius)),
        )
}
