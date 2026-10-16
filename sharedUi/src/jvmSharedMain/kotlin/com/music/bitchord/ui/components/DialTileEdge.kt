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

/** How the dials' tiles are edged. */
enum class DialTileStyle {
    /** The plain hairline every thumbnail has. */
    NONE,

    /** A bevelled rim that catches the light along the top. */
    EDGE,

    /** Liquid glass's own rim, with none of its blur: the tile shows through untouched. */
    GLASS,
}

val LocalDialTileStyle = staticCompositionLocalOf { DialTileStyle.NONE }

/**
 * The edge [DialTileStyle.GLASS] draws, handed down by the app, which owns the
 * glass. Without one, glass tiles fall back to the [DialTileStyle.EDGE] rim.
 */
val LocalDialTileGlassEdge = staticCompositionLocalOf<((Shape) -> Modifier)?> { null }

/**
 * A dial tile's edge, as [LocalDialTileStyle] says: the hairline every
 * thumbnail has; a bevelled rim that catches the light — bright along the top,
 * fading down the sides, a faint return along the foot — with a soft sheen
 * just inside it; or liquid glass's rim, drawn over the art without blurring it.
 */
fun Modifier.dialTileBorder(shape: Shape): Modifier = composed {
    val glassEdge = LocalDialTileGlassEdge.current
    when (LocalDialTileStyle.current) {
        DialTileStyle.NONE -> return@composed thumbnailBorder(shape)
        DialTileStyle.GLASS -> if (glassEdge != null) return@composed glassEdge(shape)
        DialTileStyle.EDGE -> Unit
    }
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
