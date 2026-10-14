package com.music.bitchord.data

import android.graphics.Bitmap
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.request.transformations
import coil3.size.Size
import coil3.transform.Transformation

/**
 * Cuts the black bars off YouTube's video thumbnails.
 *
 * A video's artwork (an upload, or a song YouTube only has as a video) comes
 * from i.ytimg.com as a 4:3 frame with the 16:9 picture letterboxed inside it.
 * Cropped square for a card, the bars stayed in: a strip of picture between
 * two black bands. With them trimmed, the card's crop fills it with picture.
 */
class LetterboxCrop : Transformation() {
    override val cacheKey: String = "letterbox-crop-1"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val w = input.width
        val h = input.height
        if (w < 16 || h < 16) return input
        var top = 0
        while (top < h / 3 && isBar(input, row = top, horizontal = true)) top++
        var bottom = h - 1
        while (bottom > h * 2 / 3 && isBar(input, row = bottom, horizontal = true)) bottom--
        var left = 0
        while (left < w / 3 && isBar(input, row = left, horizontal = false)) left++
        var right = w - 1
        while (right > w * 2 / 3 && isBar(input, row = right, horizontal = false)) right--
        val cropW = right - left + 1
        val cropH = bottom - top + 1
        // Nothing worth trimming: a frame that is only a pixel or two dark at
        // its edge is just a dark picture.
        if (cropW >= w - 4 && cropH >= h - 4) return input
        return Bitmap.createBitmap(input, left, top, cropW, cropH)
    }

    /** Whether one row (or column) of [bitmap] is all near-black. */
    private fun isBar(bitmap: Bitmap, row: Int, horizontal: Boolean): Boolean {
        val length = if (horizontal) bitmap.width else bitmap.height
        val step = (length / 24).coerceAtLeast(1)
        var i = 0
        while (i < length) {
            val pixel = if (horizontal) bitmap.getPixel(i, row) else bitmap.getPixel(row, i)
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            if (r > BAR_LEVEL || g > BAR_LEVEL || b > BAR_LEVEL) return false
            i += step
        }
        return true
    }

    /** Puts [LetterboxCrop] on every YouTube video thumbnail the app loads. */
    class Install : Interceptor {
        override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
            val request = chain.request
            val url = request.data.toString()
            if (!isVideoThumbnail(url)) return chain.proceed()
            return chain.withRequest(
                request.newBuilder().transformations(request.transformations + LetterboxCrop()).build(),
            ).proceed()
        }
    }

    companion object {
        /** How dark a pixel must be, per channel, to count as part of a bar (JPEG noise included). */
        private const val BAR_LEVEL = 18

        fun isVideoThumbnail(url: String): Boolean =
            url.contains("i.ytimg.com/vi") || url.contains("img.youtube.com/vi")
    }
}
