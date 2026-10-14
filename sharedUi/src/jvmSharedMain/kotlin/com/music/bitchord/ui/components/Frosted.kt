package com.music.bitchord.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The see-through fill for cards, fields and pills while the app draws a
 * coloured background behind its pages, or null for the usual solid surfaces.
 * Provided by the app.
 */
val LocalFrostedSurface = staticCompositionLocalOf<Color?> { null }

/** [LocalFrostedSurface] when there is one, else [fallback]. */
@Composable
fun frostedOr(fallback: Color): Color = LocalFrostedSurface.current ?: fallback

/** Whether the page has a coloured background that rows should let show through. */
@Composable
fun pageIsFrosted(): Boolean = LocalFrostedSurface.current != null

/**
 * The see-through fill for text fields while the app is in liquid glass (or
 * has a coloured background), so a field reads as a pane of frosted glass
 * over whatever is behind it rather than a solid grey block. Provided by the app.
 */
val LocalFrostedFields = staticCompositionLocalOf<Color?> { null }

/** A text field's fill: frosted when the app asks for it, else [fallback]. */
@Composable
fun fieldFill(fallback: Color): Color = LocalFrostedFields.current ?: LocalFrostedSurface.current ?: fallback
