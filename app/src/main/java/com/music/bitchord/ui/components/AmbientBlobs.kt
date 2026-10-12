package com.music.bitchord.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.luminance
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.CustomizationSettings
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** The app icon's red, the ambient background's colour unless something says otherwise. */
val AmbientAppColor = Color(0xFFF9263E)
val AmbientSpotifyColor = Color(0xFF1DB954)
val AmbientYouTubeColor = Color(0xFFFF0033)

/**
 * The colour [target] is drawn in behind the pages: never brighter than a
 * mid-tone, so text on top of it stays legible whatever colour it starts as.
 * A near-white cover comes out as a dim grey rather than a white wash.
 */
private fun ambientTone(target: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(target.toArgb(), hsv)
    hsv[2] = hsv[2].coerceAtMost(AMBIENT_MAX_VALUE)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/**
 * Slow-moving, heavily dimmed colour blobs behind the app's pages, in the
 * spirit of the player's own backdrop but far cheaper: three soft radial
 * gradients drawn straight onto the page's background, no blur pass.
 *
 * [color] crossfades when it changes. With [animate] off (reduce animation),
 * the blobs hold still.
 */
@Composable
fun rememberAmbientBlobs(color: Color, animate: Boolean): AmbientBlobs {
    val tone by animateColorAsState(ambientTone(color), tween(AMBIENT_COLOR_FADE_MS), label = "ambientTone")
    val phase: State<Float> = if (animate) {
        rememberInfiniteTransition(label = "ambientBlobs").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(AMBIENT_CYCLE_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "ambientPhase",
        )
    } else {
        remember { object : State<Float> { override val value = 0.15f } }
    }
    return AmbientBlobs({ tone }, phase)
}

class AmbientBlobs internal constructor(
    internal val tone: () -> Color,
    internal val phase: State<Float>,
)

/** Draws [blobs] behind this; a null [blobs] draws nothing. */
fun Modifier.ambientBlobs(blobs: AmbientBlobs?): Modifier =
    if (blobs == null) {
        this
    } else {
        drawBehind {
            val t = blobs.phase.value * 2f * PI.toFloat()
            val base = blobs.tone()
            val radius = max(size.width, size.height) * 0.62f
            // Three blobs on their own slow orbits, out of step with each other.
            val centres = listOf(
                Offset(
                    size.width * (0.18f + 0.14f * cos(t)),
                    size.height * (0.12f + 0.08f * sin(t)),
                ),
                Offset(
                    size.width * (0.86f + 0.10f * sin(t + 2.1f)),
                    size.height * (0.42f + 0.10f * cos(t + 2.1f)),
                ),
                Offset(
                    size.width * (0.30f + 0.16f * cos(t + 4.2f)),
                    size.height * (0.88f + 0.06f * sin(t + 4.2f)),
                ),
            )
            val strengths = floatArrayOf(AMBIENT_ALPHA, AMBIENT_ALPHA * 0.75f, AMBIENT_ALPHA * 0.6f)
            centres.forEachIndexed { index, centre ->
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(base.copy(alpha = strengths[index]), Color.Transparent),
                        center = centre,
                        radius = radius,
                    ),
                    radius = radius,
                    center = centre,
                )
            }
        }
    }

/** The brightest the blobs' colour may be (HSV value), before its alpha. */
private const val AMBIENT_MAX_VALUE = 0.85f

/** How strongly the nearest blob tints the page at its centre. */
private const val AMBIENT_ALPHA = 0.26f

/** One full orbit of the blobs: slow enough to notice only over time. */
private const val AMBIENT_CYCLE_MS = 40_000

private const val AMBIENT_COLOR_FADE_MS = 900

/**
 * A surface colour for cards, fields and bars that sit on the page: a thin,
 * see-through frost while the ambient background is on, so the colour behind
 * reads through it, and [fallback] (the usual solid grey) when it is off.
 */
@Composable
fun frostedSurfaceColor(fallback: Color): Color {
    val ambient by CustomizationSettings.ambientBackground.collectAsStateWithLifecycle()
    if (!ambient) return fallback
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (dark) Color.White.copy(alpha = 0.075f) else Color.Black.copy(alpha = 0.05f)
}
