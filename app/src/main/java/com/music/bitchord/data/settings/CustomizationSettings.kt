package com.music.bitchord.data.settings

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow

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
        miniPlayerShape.value = MiniPlayerShape.entries
            .firstOrNull { it.name == p.getString(KEY_MINI_PLAYER_SHAPE, null) } ?: MiniPlayerShape.CIRCULAR
    }

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

    fun setMiniPlayerShape(value: MiniPlayerShape) {
        miniPlayerShape.value = value
        prefs?.edit()?.putString(KEY_MINI_PLAYER_SHAPE, value.name)?.apply()
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
        val saved = stored.split(",").mapNotNull { entry ->
            val item = parse(entry.substringBefore(':')) ?: return@mapNotNull null
            OrderedToggle(item, entry.substringAfter(':', "1") != "0")
        }.distinctBy { it.item }
        val missing = all.filter { item -> saved.none { it.item == item } }
            .map { OrderedToggle(it, it in defaultOn) }
        return saved.filter { it.item in all } + missing
    }

    internal fun <T> writeOrdered(value: List<OrderedToggle<T>>, name: (T) -> String): String =
        value.joinToString(",") { "${name(it.item)}:${if (it.enabled) 1 else 0}" }

    private const val KEY_NAV_TABS = "custom_nav_tabs"
    private const val KEY_START_PAGE = "custom_start_page"
    private const val KEY_KEEP_NAV_EXPANDED = "custom_keep_nav_expanded"
    private const val KEY_MINI_PLAYER_SHAPE = "custom_mini_player_shape"
}
