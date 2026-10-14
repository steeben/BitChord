package com.music.bitchord.ui.components

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The mark's height on the launch screen: the system splash's icon
 * (`drawable/splash_logo`) drawn at this size, so the hand-over is seamless.
 */
val SPLASH_LOGO_HEIGHT = 120.dp

/**
 * Holds the system splash on screen until Home is ready to be seen.
 *
 * On Android 12 and up the system splash plays the logo building itself, as an
 * animated vector the system runs off the app's main thread, so it stays smooth
 * however busy the app is starting up. The activity holds back its first frame
 * (and with it the splash's exit) until [open] is set.
 */
object LaunchGate {
    @Volatile
    var open: Boolean = false

    /** When the activity was created, for the splash animation's minimum run. */
    @Volatile
    var createdAt: Long = 0L

    /** How long the system splash's animation takes; see `drawable/splash_logo_animated`. */
    const val ANIMATION_MS = 850L

    /** The longest the first frame is held back, whatever happens. */
    const val MAX_HOLD_MS = 6_000L
}

/**
 * What the app shows while it starts, carrying on from the system splash.
 *
 * With [reveal] (no system splash to have played it: Android 11 and older) the
 * squares start dim and light up a row at a time, bottom to top, into the logo.
 * Otherwise the logo is already built when this first shows. Once [ready], and
 * with a [target] (Home is the page underneath, its mark at that top-left corner
 * and [targetHeight] high), the logo glides there as the screen fades, landing
 * exactly on Home's own mark; without one it simply fades. [onFinished] is
 * called once it is fully gone.
 *
 * Always white on black, like the system splash before it, whatever the app's
 * theme; the mark eases into the theme's colour on its way to Home.
 */
@Composable
fun LaunchSplash(
    ready: Boolean,
    reveal: Boolean,
    target: DpOffset?,
    targetHeight: Dp,
    onFinished: () -> Unit,
) {
    val started = remember { SystemClock.uptimeMillis() }
    val built = remember { Animatable(if (reveal) 0f else 1f) }
    val travel = remember { Animatable(0f) }
    val cover = remember { Animatable(1f) }
    val finished by rememberUpdatedState(onFinished)
    val landing by rememberUpdatedState(target)
    val markColor = MaterialTheme.colorScheme.onBackground

    if (reveal) {
        LaunchedEffect(Unit) {
            built.animateTo(1f, tween(LaunchGate.ANIMATION_MS.toInt(), easing = LinearEasing))
        }
    }
    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        if (reveal) {
            // Let the logo finish building first.
            delay((LaunchGate.ANIMATION_MS + 150L - (SystemClock.uptimeMillis() - started)).coerceAtLeast(0L))
        } else {
            // The first frames after the system splash lets go are the heaviest;
            // start moving once they're past, not in the middle of them.
            delay(SETTLE_MS)
        }
        coroutineScope {
            if (landing != null) {
                launch { travel.animateTo(1f, tween(GLIDE_MS, easing = FastOutSlowInEasing)) }
                delay(GLIDE_MS / 4L)
                cover.animateTo(0f, tween(GLIDE_MS * 3 / 4))
            } else {
                cover.animateTo(0f, tween(FADE_MS))
            }
        }
        finished()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = cover.value }
                .background(Color.Black)
                // Nothing underneath takes a touch until the app is showing.
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent().changes.forEach { it.consume() }
                    }
                },
        )
        val startW = SPLASH_LOGO_HEIGHT * SQUARE_LOGO_ASPECT
        val endTarget = target
        val t = travel.value
        SquareLogo(
            color = lerp(Color.White, markColor, t),
            reveal = { built.value },
            modifier = Modifier
                .size(startW, SPLASH_LOGO_HEIGHT)
                .graphicsLayer {
                    val startH = SPLASH_LOGO_HEIGHT.toPx()
                    val endH = targetHeight.toPx()
                    val h = startH + (endH - startH) * t
                    val w = h * SQUARE_LOGO_ASPECT
                    val startCx = constraints.maxWidth / 2f
                    val startCy = constraints.maxHeight / 2f
                    val (endCx, endCy) = if (endTarget != null) {
                        with(density) {
                            (endTarget.x.toPx() + endH * SQUARE_LOGO_ASPECT / 2f) to (endTarget.y.toPx() + endH / 2f)
                        }
                    } else {
                        startCx to startCy
                    }
                    val cx = startCx + (endCx - startCx) * t
                    val cy = startCy + (endCy - startCy) * t
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = h / startH
                    scaleY = h / startH
                    translationX = cx - w / 2f
                    translationY = cy - h / 2f
                    // Landing on Home it stays solid; anywhere else it fades with the cover.
                    alpha = if (endTarget != null) 1f else cover.value
                },
        )
    }
}

private const val SETTLE_MS = 150L
private const val GLIDE_MS = 650
private const val FADE_MS = 350
