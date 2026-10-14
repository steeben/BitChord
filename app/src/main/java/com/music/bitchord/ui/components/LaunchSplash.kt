package com.music.bitchord.ui.components

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
 * What the app shows while it starts, carrying on from the system splash.
 *
 * The squares chase round the grid until [ready], then settle into the logo.
 * With a [target] (Home is the page underneath, its mark at that top-left
 * corner and [targetHeight] high) the logo then glides there as the screen
 * fades, landing exactly on Home's own mark; without one it simply fades.
 * [onFinished] is called once it is fully gone.
 *
 * Always white on black, like the system splash before it, whatever the app's
 * theme; the mark eases into the theme's colour on its way to Home.
 */
@Composable
fun LaunchSplash(
    ready: Boolean,
    target: DpOffset?,
    targetHeight: Dp,
    onFinished: () -> Unit,
) {
    val started = remember { SystemClock.uptimeMillis() }
    var spinning by remember { mutableStateOf(true) }
    val travel = remember { Animatable(0f) }
    val cover = remember { Animatable(1f) }
    val finished by rememberUpdatedState(onFinished)
    val landing by rememberUpdatedState(target)
    val markColor = MaterialTheme.colorScheme.onBackground

    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        // At least most of a lap, so a fast start still reads as an animation
        // rather than a flicker.
        delay((MIN_SPIN_MS - (SystemClock.uptimeMillis() - started)).coerceAtLeast(0L))
        spinning = false
        // The squares resolving into the logo (SquareLogo's own settle).
        delay(SETTLE_MS)
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
            refreshing = spinning,
            color = lerp(Color.White, markColor, t),
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

private const val MIN_SPIN_MS = 900L
private const val SETTLE_MS = 700L
private const val GLIDE_MS = 650
private const val FADE_MS = 350
