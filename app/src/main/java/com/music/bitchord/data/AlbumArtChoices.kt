package com.music.bitchord.data

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaMetadataRetriever
import android.net.Uri
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.DetailPage
import com.music.bitchord.data.model.Song
import com.music.bitchord.ui.screens.LIBRARY_GROUP_PREFIX
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.FileSystem
import org.json.JSONObject
import java.security.MessageDigest

/**
 * The cover the user picked for an album out of the artwork its own files
 * carry, kept on this device only.
 *
 * Android files every track with one album name under one album id and hands
 * all of them the same picture — whichever file it read first — so an album
 * holding a single with its own sleeve can end up wearing the single's. The
 * files themselves still carry their own pictures; [candidates] reads them,
 * and the one picked stands for the album wherever the app draws the album
 * (its page, its card). The songs are left alone: each keeps its own art.
 *
 * A device album has no id anything else knows it by, so it is keyed by its
 * name, which is also what the library groups it by. A YouTube Music album
 * is keyed by its browse id.
 */
object AlbumArtChoices {
    private const val PREF_NAME = "bitchord_album_art"
    private const val KEY_CHOICES = "choices_json"

    /** The scheme [TrackArtFetcher] answers: a track's own embedded picture. */
    const val TRACK_ART_SCHEME = "trackart"

    private var prefs: SharedPreferences? = null

    /** Album key (see [keyOf]) to the picked artwork's url. */
    private val _choices = MutableStateFlow<Map<String, String>>(emptyMap())
    val choices = _choices.asStateFlow()

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs = p
        val raw = p.getString(KEY_CHOICES, null) ?: return
        runCatching {
            val obj = JSONObject(raw)
            _choices.value = obj.keys().asSequence().associateWith { obj.getString(it) }
        }
    }

    fun keyOf(browseId: String?, title: String): String =
        browseId?.takeUnless { it.isBlank() || it.startsWith(LIBRARY_GROUP_PREFIX) }
            ?: ("album:" + LibraryArtists.key(title))

    fun chosen(browseId: String?, title: String, choices: Map<String, String> = _choices.value): String? =
        choices[keyOf(browseId, title)]

    fun set(browseId: String?, title: String, url: String) = update(_choices.value + (keyOf(browseId, title) to url))

    fun clear(browseId: String?, title: String) = update(_choices.value - keyOf(browseId, title))

    private fun update(map: Map<String, String>) {
        _choices.value = map
        prefs?.edit()?.putString(KEY_CHOICES, JSONObject(map).toString())?.apply()
    }

    /** Whether [songs] could hold more than one cover worth choosing between. */
    fun worthOffering(songs: List<Song>): Boolean =
        songs.any { it.localUri?.startsWith("content://") == true } ||
            songs.mapNotNullTo(HashSet()) { it.thumbnailUrl }.size > 1

    /** The url that draws [song]'s own embedded picture, for a file on the device. */
    private fun trackArtUrl(contentUri: String): String =
        TRACK_ART_SCHEME + "://" + contentUri.removePrefix("content://")

    /**
     * Every different cover among [songs], in track order: the picture embedded
     * in each file on the device, told apart by content rather than by url (a
     * whole album of identical sleeves is one choice), and the artwork url of
     * anything that isn't a file with a picture in it.
     */
    suspend fun candidates(context: Context, songs: List<Song>): List<String> = withContext(Dispatchers.IO) {
        val seen = HashSet<String>()
        val out = ArrayList<String>()
        songs.forEach { song ->
            val local = song.localUri?.takeIf { it.startsWith("content://") }
            val bytes = local?.let { embeddedPicture(context, it) }
            if (local != null && bytes != null) {
                if (seen.add("sha:" + digest(bytes))) out += trackArtUrl(local)
                return@forEach
            }
            song.thumbnailUrl?.let { if (seen.add(it)) out += it }
        }
        out
    }

    private fun digest(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

    fun embeddedPicture(context: Context, contentUri: String): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(contentUri))
            retriever.embeddedPicture
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}

/** [this] page with the album cover the user picked, if they picked one. */
fun DetailPage.withChosenAlbumArt(choices: Map<String, String>): DetailPage {
    if (choices.isEmpty()) return this
    val own = if (type == BrowseType.ALBUM) AlbumArtChoices.chosen(browseId, title, choices) else null
    val albumCards = sections.map { shelf ->
        shelf.copy(
            items = shelf.items.map { item ->
                val id = item.browseId
                if (id != null && id.startsWith(LIBRARY_GROUP_PREFIX + "album:")) {
                    AlbumArtChoices.chosen(id, item.title, choices)?.let { item.copy(thumbnailUrl = it) } ?: item
                } else {
                    item
                }
            },
        )
    }
    return copy(thumbnailUrl = own ?: thumbnailUrl, sections = albumCards)
}

/**
 * Draws a `trackart://` url — see [AlbumArtChoices] — as the picture embedded
 * in that one file, rather than the album-wide one Android files it under.
 */
class TrackArtFetcher private constructor(
    private val contentUri: String,
    private val context: Context,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val bytes = withContext(Dispatchers.IO) { AlbumArtChoices.embeddedPicture(context, contentUri) }
            ?: error("No picture in $contentUri")
        return SourceFetchResult(
            source = ImageSource(Buffer().write(bytes), FileSystem.SYSTEM),
            mimeType = null,
            dataSource = DataSource.DISK,
        )
    }

    /** Coil reads a string model as a [coil3.Uri] before asking any fetcher. */
    class Factory : Fetcher.Factory<coil3.Uri> {
        override fun create(data: coil3.Uri, options: Options, imageLoader: ImageLoader): Fetcher? =
            from(data.toString(), options)
    }

    /** In case a string model ever reaches the fetchers as it is. */
    class StringFactory : Fetcher.Factory<String> {
        override fun create(data: String, options: Options, imageLoader: ImageLoader): Fetcher? =
            from(data, options)
    }

    private companion object {
        fun from(text: String, options: Options): Fetcher? {
            val prefix = "${AlbumArtChoices.TRACK_ART_SCHEME}://"
            if (!text.startsWith(prefix)) return null
            val rest = text.removePrefix(prefix)
            if (rest.isEmpty()) return null
            return TrackArtFetcher("content://$rest", options.context)
        }
    }
}
