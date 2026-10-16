package com.music.bitchord.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp

/** Whether the dials' tiles carry the contoured rim rather than the plain hairline. */
val LocalDialContouredEdge = staticCompositionLocalOf { false }

/**
 * A dial tile's edge: the hairline every thumbnail has, or, with
 * [LocalDialContouredEdge] on, a bevelled rim that catches the light — bright
 * along the top, fading down the sides, a faint return along the foot — with a
 * soft sheen just inside it.
 */
fun Modifier.dialTileBorder(shape: Shape): Modifier = composed {
    if (!LocalDialContouredEdge.current) return@composed thumbnailBorder(shape)
    drawWithCache {
        val rimWidth = 1.5.dp.toPx()
        // Inset by half the stroke so the whole rim sits inside the clip.
        val outline = shape.createOutline(
            size.copy(width = size.width - rimWidth, height = size.height - rimWidth),
            layoutDirection,
            this,
        )
        val rim = Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.55f),
            0.18f to Color.White.copy(alpha = 0.22f),
            0.5f to Color.White.copy(alpha = 0.07f),
            0.85f to Color.White.copy(alpha = 0.05f),
            1f to Color.White.copy(alpha = 0.16f),
        )
        val sheen = Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.10f),
            0.12f to Color.Transparent,
        )
        onDrawWithContent {
            drawContent()
            drawRect(sheen)
            translate(rimWidth / 2f, rimWidth / 2f) {
                drawOutline(outline, rim, style = Stroke(rimWidth))
            }
        }
    }
}
