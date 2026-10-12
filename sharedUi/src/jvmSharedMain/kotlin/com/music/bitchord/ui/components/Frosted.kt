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
