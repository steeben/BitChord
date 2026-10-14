package com.music.bitchord.data.settings

import android.content.SharedPreferences
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** A page the bottom bar can lead to. [id] is the tab index MainActivity keys its pages by. */
enum class NavPage(val id: Int) {
    HOME(0),
    EXPLORE(1),
    LIBRARY(2),
    SEARCH(3),
    PLAYLISTS(4),
    ;

    companion object {
        fun fromId(id: Int): NavPage? = entries.firstOrNull { it.id == id }

        /** Pages that can sit in the bar's main pill. Search always has its own button at the end. */
        val pillPages = listOf(HOME, EXPLORE, LIBRARY, PLAYLISTS)
        val defaultPill = listOf(HOME, EXPLORE, LIBRARY)
    }
}

enum class MiniPlayerShape { CIRCULAR, SQUARED }

/** Which tab a library folder opens on. Matches the folder page's tab order. */
enum class LibraryStartPage { SONGS, ARTISTS, ALBUMS }

/** A library folder the Library tab opens straight into. */
data class DefaultLibrary(val browseId: String, val title: String)

/** One entry in an ordered list where each entry can also be switched off. */
data class OrderedToggle<T>(val item: T, val enabled: Boolean)

/**
 * Layout and navigation preferences: the bottom bar, the start page, the mini
 * player's shape, the Home page's sections and the speed dial pins.
 *
 * Stored in the same preference file as [AppSettings], so backups and restores
 * carry them along with everything else.
 */
object CustomizationSettings {
    private var prefs: SharedPreferences? = null

    /** The pages in the bar's main pill, in order, each either shown or hidden. */
    val navTabs = MutableStateFlow(NavPage.pillPages.map { OrderedToggle(it, it in NavPage.defaultPill) })
    val startPage = MutableStateFlow(NavPage.HOME)
    val keepNavBarExpanded = MutableStateFlow(false)
    val miniPlayerShape = MutableStateFlow(MiniPlayerShape.CIRCULAR)

    /** How many rows of three tiles the speed dial shows per page: 3 or 4. */
    val speedDialRows = MutableStateFlow(DEFAULT_SPEED_DIAL_ROWS)

    /** The words beside the logo on Home; whatever the user typed there. */
    val homeTitle = MutableStateFlow(DEFAULT_HOME_TITLE)

    /**
     * Over a Spotify Canvas, shrink the controls to the foot of the screen
     * instead of hiding them: title, live lyrics and transport stay.
     */
    val compactCanvasControls = MutableStateFlow(true)

    private val DEFAULT_TAB_VIEWS = listOf(LibraryViewType.LIST, LibraryViewType.LIST, LibraryViewType.GRID)

    /** List or grid for each folder tab — songs, artists, albums — on its own. */
    val libraryTabViews = MutableStateFlow(DEFAULT_TAB_VIEWS)

    /** Slow colour blobs behind the app's pages. */
    val ambientBackground = MutableStateFlow(true)

    /** Whether those blobs take the playing song's artwork colour. */
    val ambientFollowsArtwork = MutableStateFlow(false)

    /** The library folder the Library tab opens into, or null for the Library page itself. */
    val defaultLibrary = MutableStateFlow<DefaultLibrary?>(null)
    val defaultLibraryPage = MutableStateFlow(LibraryStartPage.SONGS)

    /**
     * Home page sections by title, in the order to show them, each shown or
     * hidden. YouTube decides which sections exist, so titles are added here as
     * the feed first shows them; see [learnHomeSections].
     */
    val homeSections = MutableStateFlow<List<OrderedToggle<String>>>(listOf(OrderedToggle(SPEED_DIAL_TITLE, true)))

    /** Items pinned to the speed dial, in the order they were pinned. */
    val speedDialPins = MutableStateFlow<List<ShelfItem>>(emptyList())

    fun init(prefs: SharedPreferences) {
        this.prefs = prefs
        readAll()
    }

    fun readAll() {
        val p = prefs ?: return
        navTabs.value = readOrdered(p.getString(KEY_NAV_TABS, null), NavPage.pillPages, NavPage.defaultPill.toSet()) {
            NavPage.entries.firstOrNull { page -> page.name == it }
        }.let(::ensureOneTab)
        startPage.value = NavPage.entries.firstOrNull { it.name == p.getString(KEY_START_PAGE, null) } ?: NavPage.HOME
        keepNavBarExpanded.value = p.getBoolean(KEY_KEEP_NAV_EXPANDED, false)
        speedDialRows.value = p.getInt(KEY_SPEED_DIAL_ROWS, DEFAULT_SPEED_DIAL_ROWS).takeIf { it in 3..4 }
            ?: DEFAULT_SPEED_DIAL_ROWS
        homeTitle.value = p.getString(KEY_HOME_TITLE, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_HOME_TITLE
        miniPlayerShape.value = MiniPlayerShape.entries
            .firstOrNull { it.name == p.getString(KEY_MINI_PLAYER_SHAPE, null) } ?: MiniPlayerShape.CIRCULAR
        compactCanvasControls.value = p.getBoolean(KEY_COMPACT_CANVAS_CONTROLS, true)
        libraryTabViews.value = p.getString(KEY_LIBRARY_TAB_VIEWS, null)
            ?.split(",")
            ?.mapNotNull { name -> LibraryViewType.entries.firstOrNull { it.name == name } }
            ?.takeIf { it.size == DEFAULT_TAB_VIEWS.size }
            ?: DEFAULT_TAB_VIEWS
        ambientBackground.value = p.getBoolean(KEY_AMBIENT_BACKGROUND, true)
        ambientFollowsArtwork.value = p.getBoolean(KEY_AMBIENT_FOLLOWS_ARTWORK, false)
        defaultLibrary.value = p.getString(KEY_DEFAULT_LIBRARY_ID, null)?.takeIf { it.isNotBlank() }?.let {
            DefaultLibrary(it, p.getString(KEY_DEFAULT_LIBRARY_TITLE, null).orEmpty())
        }
        defaultLibraryPage.value = LibraryStartPage.entries
            .firstOrNull { it.name == p.getString(KEY_DEFAULT_LIBRARY_PAGE, null) } ?: LibraryStartPage.SONGS
        homeSections.value = readHomeSections(p.getString(KEY_HOME_SECTIONS, null))
        speedDialPins.value = readPins(p.getString(KEY_SPEED_DIAL_PINS, null))
    }

    // ── Home sections ───────────────────────────────────────────────────

    /** Adds any section titles not seen before, switched on, at the end of the list. */
    fun learnHomeSections(titles: List<String>) {
        val known = homeSections.value
        val fresh = titles
            .filter { title ->
                title.isNotBlank() && known.none { it.item == title } &&
                    !title.equals(SPEED_DIAL_TITLE, ignoreCase = true)
            }
            .distinct()
        if (fresh.isEmpty()) return
        setHomeSections((known + fresh.map { OrderedToggle(it, true) }).take(MAX_HOME_SECTIONS))
    }

    fun setHomeSections(value: List<OrderedToggle<String>>) {
        homeSections.value = value
        val json = JSONArray()
        value.forEach { json.put(JSONObject().put("t", it.item).put("on", it.enabled)) }
        prefs?.edit()?.putString(KEY_HOME_SECTIONS, json.toString())?.apply()
    }

    /**
     * The feed rearranged: sections switched off are dropped, the rest go in the
     * saved order, and any the settings haven't seen yet keep their feed order
     * after them.
     */
    fun arrangeHome(shelves: List<HomeShelf>, sections: List<OrderedToggle<String>> = homeSections.value): List<HomeShelf> {
        val rank = sections.withIndex().associate { (index, entry) -> entry.item to index }
        val hidden = sections.filterNot { it.enabled }.mapTo(HashSet()) { it.item }
        return shelves
            .filterNot { it.title in hidden }
            .withIndex()
            .sortedWith(compareBy({ rank[it.value.title] ?: Int.MAX_VALUE }, { it.index }))
            .map { it.value }
    }

    private fun readHomeSections(stored: String?): List<OrderedToggle<String>> {
        val default = listOf(OrderedToggle(SPEED_DIAL_TITLE, true))
        if (stored.isNullOrBlank()) return default
        return runCatching {
            val json = JSONArray(stored)
            (0 until json.length()).map { i ->
                val entry = json.getJSONObject(i)
                OrderedToggle(entry.getString("t"), entry.optBoolean("on", true))
            }.distinctBy { it.item }
        }.getOrDefault(default).let { list ->
            if (list.any { it.item == SPEED_DIAL_TITLE }) list else listOf(OrderedToggle(SPEED_DIAL_TITLE, true)) + list
        }
    }

    // ── Speed dial ──────────────────────────────────────────────────────

    fun isPinnedToSpeedDial(id: String?): Boolean =
        id != null && speedDialPins.value.any { it.speedDialId == id }

    /** Pins [item], or unpins it if it is already pinned. Returns whether it is pinned now. */
    fun toggleSpeedDial(item: ShelfItem): Boolean {
        val id = item.speedDialId ?: return false
        val current = speedDialPins.value
        val pinned = current.none { it.speedDialId == id }
        val updated = if (pinned) {
            (current + item).takeLast(SPEED_DIAL_SIZE)
        } else {
            current.filterNot { it.speedDialId == id }
        }
        setSpeedDialPins(updated)
        return pinned
    }

    /** Replaces the pins, in the order given. */
    fun setSpeedDialPins(value: List<ShelfItem>) {
        val updated = value.distinctBy { it.speedDialId }.take(SPEED_DIAL_SIZE)
        speedDialPins.value = updated
        val json = JSONArray()
        updated.forEach { pin ->
            json.put(
                JSONObject()
                    .put("title", pin.title)
                    .put("subtitle", pin.subtitle)
                    .put("thumb", pin.thumbnailUrl ?: JSONObject.NULL)
                    .put("video", pin.videoId ?: JSONObject.NULL)
                    .put("browse", pin.browseId ?: JSONObject.NULL),
            )
        }
        prefs?.edit()?.putString(KEY_SPEED_DIAL_PINS, json.toString())?.apply()
    }

    /**
     * Up to [SPEED_DIAL_SIZE] items: pins first, then YouTube's own speed dial
     * when the feed has one, then recently played, then the rest of the feed.
     */
    fun buildSpeedDial(pins: List<ShelfItem>, shelves: List<HomeShelf>): List<ShelfItem> {
        fun shelf(title: String) = shelves.firstOrNull { it.title.equals(title, ignoreCase = true) }?.items.orEmpty()
        val official = shelf(SPEED_DIAL_TITLE)
        val recents = shelf(RECENTS_TITLE)
        val rest = shelves
            .filterNot { it.title.equals(SPEED_DIAL_TITLE, true) || it.title.equals(RECENTS_TITLE, true) }
            .flatMap { it.items }
        return (pins + official + recents + rest)
            .filter { it.speedDialId != null }
            .distinctBy { it.speedDialId }
            .take(SPEED_DIAL_SIZE)
    }

    private fun readPins(stored: String?): List<ShelfItem> {
        if (stored.isNullOrBlank()) return emptyList()
        return runCatching {
            val json = JSONArray(stored)
            (0 until json.length()).map { i ->
                val o = json.getJSONObject(i)
                ShelfItem(
                    title = o.optString("title"),
                    subtitle = o.optString("subtitle"),
                    thumbnailUrl = o.optStringOrNull("thumb"),
                    videoId = o.optStringOrNull("video"),
                    browseId = o.optStringOrNull("browse"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    /** The pages the bar shows, Search last. */
    fun visibleNavPages(tabs: List<OrderedToggle<NavPage>> = navTabs.value): List<NavPage> =
        tabs.filter { it.enabled }.map { it.item } + NavPage.SEARCH

    fun setNavTabs(value: List<OrderedToggle<NavPage>>) {
        val fixed = ensureOneTab(value)
        navTabs.value = fixed
        prefs?.edit()?.putString(KEY_NAV_TABS, writeOrdered(fixed) { it.name })?.apply()
    }

    fun setStartPage(value: NavPage) {
        startPage.value = value
        prefs?.edit()?.putString(KEY_START_PAGE, value.name)?.apply()
    }

    fun setKeepNavBarExpanded(value: Boolean) {
        keepNavBarExpanded.value = value
        prefs?.edit()?.putBoolean(KEY_KEEP_NAV_EXPANDED, value)?.apply()
    }

    fun setDefaultLibrary(value: DefaultLibrary?) {
        defaultLibrary.value = value
        prefs?.edit()
            ?.putString(KEY_DEFAULT_LIBRARY_ID, value?.browseId.orEmpty())
            ?.putString(KEY_DEFAULT_LIBRARY_TITLE, value?.title.orEmpty())
            ?.apply()
    }

    fun setDefaultLibraryPage(value: LibraryStartPage) {
        defaultLibraryPage.value = value
        prefs?.edit()?.putString(KEY_DEFAULT_LIBRARY_PAGE, value.name)?.apply()
    }

    fun setLibraryTabView(tab: Int, value: LibraryViewType) {
        val updated = libraryTabViews.value.toMutableList()
        if (tab !in updated.indices) return
        updated[tab] = value
        libraryTabViews.value = updated
        prefs?.edit()?.putString(KEY_LIBRARY_TAB_VIEWS, updated.joinToString(",") { it.name })?.apply()
    }

    fun setAmbientBackground(value: Boolean) {
        ambientBackground.value = value
        prefs?.edit()?.putBoolean(KEY_AMBIENT_BACKGROUND, value)?.apply()
    }

    fun setAmbientFollowsArtwork(value: Boolean) {
        ambientFollowsArtwork.value = value
        prefs?.edit()?.putBoolean(KEY_AMBIENT_FOLLOWS_ARTWORK, value)?.apply()
    }

    fun setCompactCanvasControls(value: Boolean) {
        compactCanvasControls.value = value
        prefs?.edit()?.putBoolean(KEY_COMPACT_CANVAS_CONTROLS, value)?.apply()
    }

    fun setMiniPlayerShape(value: MiniPlayerShape) {
        miniPlayerShape.value = value
        prefs?.edit()?.putString(KEY_MINI_PLAYER_SHAPE, value.name)?.apply()
    }

    /** Saves the Home title. Blank puts the default back. */
    fun setHomeTitle(value: String) {
        val title = value.trim().take(HOME_TITLE_MAX).ifEmpty { DEFAULT_HOME_TITLE }
        homeTitle.value = title
        prefs?.edit()?.apply { if (title == DEFAULT_HOME_TITLE) remove(KEY_HOME_TITLE) else putString(KEY_HOME_TITLE, title) }?.apply()
    }

    fun setSpeedDialRows(value: Int) {
        val rows = value.coerceIn(3, 4)
        speedDialRows.value = rows
        prefs?.edit()?.putInt(KEY_SPEED_DIAL_ROWS, rows)?.apply()
    }

    private fun ensureOneTab(tabs: List<OrderedToggle<NavPage>>): List<OrderedToggle<NavPage>> =
        if (tabs.any { it.enabled }) {
            tabs
        } else {
            tabs.map { if (it.item == NavPage.HOME) it.copy(enabled = true) else it }
        }

    /**
     * Reads "NAME:1,NAME:0,…". Unknown names are dropped, and any [all] entry the
     * stored list doesn't mention is appended, enabled when it is in [defaultOn],
     * so entries added in later builds still show up.
     */
    internal fun <T> readOrdered(
        stored: String?,
        all: List<T>,
        defaultOn: Set<T>,
        parse: (String) -> T?,
    ): List<OrderedToggle<T>> {
        if (stored.isNullOrBlank()) return all.map { OrderedToggle(it, it in defaultOn) }
        val saved: List<OrderedToggle<T>> = stored.split(",").mapNotNull { entry ->
            val item: T = parse(entry.substringBefore(':')) ?: return@mapNotNull null
            OrderedToggle<T>(item, entry.substringAfter(':', "1") != "0")
        }.distinctBy { it.item }
        val missing: List<OrderedToggle<T>> = all.filter { item -> saved.none { it.item == item } }
            .map { OrderedToggle<T>(it, it in defaultOn) }
        return saved.filter { it.item in all } + missing
    }

    internal fun <T> writeOrdered(value: List<OrderedToggle<T>>, name: (T) -> String): String =
        value.joinToString(",") { "${name(it.item)}:${if (it.enabled) 1 else 0}" }

    /** The title the speed dial goes by, both YouTube's and the one built here. */
    const val SPEED_DIAL_TITLE = "Speed dial"
    private const val RECENTS_TITLE = "Recents"
    const val SPEED_DIAL_SIZE = 36
    const val DEFAULT_SPEED_DIAL_ROWS = 4
    const val DEFAULT_HOME_TITLE = "BitChord Dial"
    const val HOME_TITLE_MAX = 24
    private const val MAX_HOME_SECTIONS = 60

    private const val KEY_NAV_TABS = "custom_nav_tabs"
    private const val KEY_HOME_SECTIONS = "custom_home_sections"
    private const val KEY_COMPACT_CANVAS_CONTROLS = "custom_compact_canvas_controls"
    private const val KEY_AMBIENT_BACKGROUND = "custom_ambient_background"
    private const val KEY_LIBRARY_TAB_VIEWS = "custom_library_tab_views"
    private const val KEY_AMBIENT_FOLLOWS_ARTWORK = "custom_ambient_follows_artwork"
    private const val KEY_DEFAULT_LIBRARY_ID = "custom_default_library_id"
    private const val KEY_DEFAULT_LIBRARY_TITLE = "custom_default_library_title"
    private const val KEY_DEFAULT_LIBRARY_PAGE = "custom_default_library_page"
    private const val KEY_SPEED_DIAL_PINS = "custom_speed_dial_pins"
    private const val KEY_START_PAGE = "custom_start_page"
    private const val KEY_KEEP_NAV_EXPANDED = "custom_keep_nav_expanded"
    private const val KEY_MINI_PLAYER_SHAPE = "custom_mini_player_shape"
    private const val KEY_SPEED_DIAL_ROWS = "custom_speed_dial_rows"
    private const val KEY_HOME_TITLE = "custom_home_title"
}

/** What identifies an item on the speed dial: its track, or the page it opens. */
val ShelfItem.speedDialId: String? get() = videoId ?: browseId

/**
 * When each track was last started, by video id, for the folders' "Last played"
 * sort. Kept on this device only; YouTube's own history is per account and
 * not something a folder of local files could be matched against.
 */
object LastPlayed {
    private var prefs: SharedPreferences? = null
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun init(context: android.content.Context) {
        val p = context.getSharedPreferences("bitchord_last_played", android.content.Context.MODE_PRIVATE)
        prefs = p
        cache.clear()
        p.all.forEach { (key, value) -> (value as? Long)?.let { cache[key] = it } }
    }

    fun record(videoId: String) {
        if (videoId.isBlank()) return
        val now = System.currentTimeMillis()
        cache[videoId] = now
        prefs?.edit()?.putLong(videoId, now)?.apply()
    }

    /** Milliseconds since the epoch, or 0 for never. */
    fun at(videoId: String): Long = cache[videoId] ?: 0L
}
