package com.music.bitchord.data

import android.content.Context
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The last Home feed each listener saw, kept on disk so the next launch can
 * show it straight away while a fresh one is fetched behind it.
 */
object HomeDiskCache {
    private fun file(context: Context, listener: String?): File =
        File(context.cacheDir, "home_feed_${(listener ?: "guest").hashCode().toUInt()}.json")

    fun read(context: Context, listener: String?): List<HomeShelf>? = runCatching {
        val f = file(context, listener)
        if (!f.exists()) return null
        val json = JSONArray(f.readText())
        (0 until json.length()).map { i ->
            val o = json.getJSONObject(i)
            val items = o.getJSONArray("items")
            HomeShelf(
                title = o.getString("title"),
                items = (0 until items.length()).map { j ->
                    val entry = items.getJSONObject(j)
                    ShelfItem(
                        title = entry.optString("title"),
                        subtitle = entry.optString("subtitle"),
                        thumbnailUrl = entry.optNullableString("thumb"),
                        videoId = entry.optNullableString("video"),
                        browseId = entry.optNullableString("browse"),
                    )
                },
                subtitle = o.optString("subtitle"),
                moreBrowseId = o.optNullableString("moreBrowse"),
                moreParams = o.optNullableString("moreParams"),
            )
        }.takeIf { it.isNotEmpty() }
    }.getOrNull()

    fun write(context: Context, listener: String?, shelves: List<HomeShelf>) {
        runCatching {
            val json = JSONArray()
            shelves.forEach { shelf ->
                val items = JSONArray()
                shelf.items.forEach { item ->
                    items.put(
                        JSONObject()
                            .put("title", item.title)
                            .put("subtitle", item.subtitle)
                            .put("thumb", item.thumbnailUrl ?: JSONObject.NULL)
                            .put("video", item.videoId ?: JSONObject.NULL)
                            .put("browse", item.browseId ?: JSONObject.NULL),
                    )
                }
                json.put(
                    JSONObject()
                        .put("title", shelf.title)
                        .put("subtitle", shelf.subtitle)
                        .put("moreBrowse", shelf.moreBrowseId ?: JSONObject.NULL)
                        .put("moreParams", shelf.moreParams ?: JSONObject.NULL)
                        .put("items", items),
                )
            }
            val target = file(context, listener)
            val temp = File(target.parentFile, target.name + ".tmp")
            temp.writeText(json.toString())
            temp.renameTo(target)
        }
    }

    private fun JSONObject.optNullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
}
