package com.music.bitchord.data.settings

import android.content.SharedPreferences
import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.spotify.SPOTIFY_PAGE_PREFIX
import org.json.JSONArray
import org.json.JSONObject

/**
 * Which album or playlist each recently played track was played from, so the
 * speed dial can show the album or playlist rather than its songs one by one.
 *
 * YouTube's history, which Recently played is built from, is a list of songs
 * with no word of where they were played from, so this is kept on the device:
 * each track that starts playing as part of an album or playlist is noted
 * against it, and a track that starts from anywhere else (Search, a radio,
 * the queue) has its note dropped, so the latest play of a song decides
 * whether it shows as itself or as its album.
 */
object PlayedCollections {
    private var prefs: SharedPreferences? = null

    /** Track → the collection it last played from, oldest first. */
    private val sources = LinkedHashMap<String, String>()

    /** What each collection looks like on a tile, by browse id. */
    private val collections = HashMap<String, ShelfItem>()

    fun init(context: android.content.Context) {
        val p = context.getSharedPreferences("bitchord_played_collections", android.content.Context.MODE_PRIVATE)
        prefs = p
        synchronized(this) {
            sources.clear()
            collections.clear()
            runCatching {
                val saved = JSONObject(p.getString(KEY, null) ?: return@runCatching)
                val pages = saved.optJSONObject("collections") ?: JSONObject()
                pages.keys().forEach { id ->
                    val o = pages.getJSONObject(id)
                    collections[id] = ShelfItem(
                        title = o.optString("title"),
                        subtitle = o.optString("subtitle"),
                        thumbnailUrl = o.optString("thumb").takeIf { it.isNotBlank() },
                        videoId = null,
                        browseId = id,
                    )
                }
                val tracks = saved.optJSONArray("tracks") ?: JSONArray()
                for (i in 0 until tracks.length()) {
                    val pair = tracks.getJSONArray(i)
                    val id = pair.getString(1)
                    if (id in collections) sources[pair.getString(0)] = id
                }
            }
        }
    }

    /** Whether [browseId] is an album or playlist page the dial can open. */
    fun isCollection(browseId: String?): Boolean =
        browseId != null && COLLECTION_PREFIXES.any { browseId.startsWith(it) }

    /**
     * How [browseId] should look on the dial, from its page. Called as it is
     * played, so the playlist's own cover is on hand rather than only the
     * artwork of a track in it.
     */
    @Synchronized
    fun describe(browseId: String?, title: String, subtitle: String, thumbnailUrl: String?) {
        if (!isCollection(browseId) || browseId == null || title.isBlank()) return
        val item = ShelfItem(title, subtitle, thumbnailUrl ?: collections[browseId]?.thumbnailUrl, null, browseId)
        if (collections[browseId] == item) return
        collections[browseId] = item
        save()
    }

    /** A track has started playing: note, or forget, the collection it came from. */
    @Synchronized
    fun onTrackStarted(song: Song) {
        val videoId = song.videoId.takeIf { it.isNotBlank() } ?: return
        val sourceId = song.playbackSourceId
        // Tracks queued by hand or added by AutoPlay carry the collection's
        // name on to Now Playing but were not played from it.
        val fromCollection = song.playbackSourceType == PlaybackSourceType.BROWSE &&
            song.queueTier == QueueTier.CONTEXT && isCollection(sourceId)
        if (!fromCollection || sourceId == null) {
            if (sources.remove(videoId) != null) save()
            return
        }
        if (sourceId !in collections) {
            collections[sourceId] = ShelfItem(
                title = song.playbackSource ?: song.albumName ?: return,
                subtitle = "",
                thumbnailUrl = song.thumbnailUrl,
                videoId = null,
                browseId = sourceId,
            )
        }
        sources.remove(videoId)
        sources[videoId] = sourceId
        while (sources.size > MAX_TRACKS) sources.remove(sources.keys.first())
        save()
    }

    /** The album or playlist [videoId] was last played from, or null for a play of its own. */
    @Synchronized
    fun collectionFor(videoId: String?): ShelfItem? = videoId?.let { sources[it] }?.let { collections[it] }

    private fun save() {
        // Pages described but not (yet) played from are kept for a while,
        // since a page is described just before its first track starts.
        if (collections.size > MAX_COLLECTIONS) collections.keys.retainAll(sources.values.toSet())
        val pages = JSONObject()
        collections.forEach { (id, item) ->
            pages.put(
                id,
                JSONObject()
                    .put("title", item.title)
                    .put("subtitle", item.subtitle)
                    .put("thumb", item.thumbnailUrl ?: ""),
            )
        }
        val tracks = JSONArray()
        sources.forEach { (videoId, id) -> tracks.put(JSONArray().put(videoId).put(id)) }
        prefs?.edit()?.putString(KEY, JSONObject().put("collections", pages).put("tracks", tracks).toString())?.apply()
    }

    private const val KEY = "played_collections"

    /** Well past what Recently played holds, so its oldest rows are still matched. */
    private const val MAX_TRACKS = 400
    private const val MAX_COLLECTIONS = 80

    private val COLLECTION_PREFIXES = listOf("MPREb_", "VL", SPOTIFY_PAGE_PREFIX)
}
