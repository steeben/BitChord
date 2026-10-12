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
 * Three soft, semi-large colour blobs behind the app's pages that drift around
 * and through each other: [primary], a dimmer [primary], and [secondary]. Drawn
 * as plain radial gradients straight onto the page's background — no blur pass
 * — so the motion costs three gradient draws a frame.
 *
 * Colours crossfade when they change. With [animate] off (reduce animation),
 * the blobs hold still.
 */
@Composable
fun rememberAmbientBlobs(primary: Color, secondary: Color, animate: Boolean): AmbientBlobs {
    val first by animateColorAsState(ambientTone(primary), tween(AMBIENT_COLOR_FADE_MS), label = "ambientFirst")
    val second by animateColorAsState(ambientTone(secondary), tween(AMBIENT_COLOR_FADE_MS), label = "ambientSecond")
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
    return AmbientBlobs({ first }, { second }, phase)
}

/** A second colour for a single brand colour: the same hue turned a little and darkened. */
fun ambientCompanion(color: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    hsv[0] = (hsv[0] + 28f) % 360f
    hsv[2] = hsv[2] * 0.8f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

class AmbientBlobs internal constructor(
    internal val primary: () -> Color,
    internal val secondary: () -> Color,
    internal val phase: State<Float>,
)

/** Draws [blobs] behind this; a null [blobs] draws nothing. */
fun Modifier.ambientBlobs(blobs: AmbientBlobs?): Modifier =
    if (blobs == null) {
        this
    } else {
        drawBehind {
            // One full turn of [phase] is one loop; each blob runs its own
            // Lissajous path at a different whole-number speed so the three
            // keep crossing and overlapping but the loop still joins up.
            val t = blobs.phase.value * 2f * PI.toFloat()
            val w = size.width
            val h = size.height
            val radius = max(w, h) * AMBIENT_RADIUS_FRACTION
            val main = blobs.primary()
            val blobs3 = listOf(
                Triple(
                    Offset(w * (0.5f + 0.38f * sin(t)), h * (0.35f + 0.28f * sin(2f * t + 0.6f))),
                    main,
                    AMBIENT_ALPHA,
                ),
                Triple(
                    Offset(w * (0.5f + 0.38f * cos(2f * t + 1.3f)), h * (0.6f + 0.3f * sin(t + 2.2f))),
                    main.copy(red = main.red * 0.7f, green = main.green * 0.7f, blue = main.blue * 0.7f),
                    AMBIENT_ALPHA * 0.9f,
                ),
                Triple(
                    Offset(w * (0.5f + 0.4f * sin(3f * t + 2.4f)), h * (0.5f + 0.34f * cos(t + 0.9f))),
                    blobs.secondary(),
                    AMBIENT_ALPHA * 0.95f,
                ),
            )
            blobs3.forEach { (centre, colour, strength) ->
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colour.copy(alpha = strength),
                            colour.copy(alpha = strength * 0.45f),
                            Color.Transparent,
                        ),
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
private const val AMBIENT_ALPHA = 0.30f

/** Each blob's reach, as a share of the page's longer side. */
private const val AMBIENT_RADIUS_FRACTION = 0.48f

/** One full loop of the blobs' paths. */
private const val AMBIENT_CYCLE_MS = 36_000

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
