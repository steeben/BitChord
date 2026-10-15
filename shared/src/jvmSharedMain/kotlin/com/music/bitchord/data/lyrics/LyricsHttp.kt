package com.music.bitchord.data.lyrics

import com.music.bitchord.data.Http
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Shared plumbing for the lyric providers.
 *
 * They are all raced against each other by [LyricsRepository], so a provider
 * that hangs holds up the whole lookup. [LYRICS_TIMEOUT_SECONDS] is deliberately
 * far shorter than [Http]'s stream-oriented timeouts: a lyric that arrives
 * after the second chorus is of no use to anyone, and the fallbacks behind it
 * are the better answer.
 */
private const val LYRICS_TIMEOUT_SECONDS = 6L

internal const val LYRICS_AGENT = "BitChord (https://github.com/bitchord)"

val lyricsJson = Json { ignoreUnknownKeys = true; isLenient = true }

private val client by lazy {
    // Derived from the shared client, so the connection pool and DNS stay
    // common — only the deadline differs.
    Http.client.newBuilder()
        .callTimeout(LYRICS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .connectTimeout(3, TimeUnit.SECONDS)
        .build()
}

// PaxSenix's authenticated routes often have to query an upstream catalogue
// before answering. Keep the fast deadline for the ordinary providers, but
// match PaxSenix's own client timeout here so valid requests are not discarded
// while its backend is still resolving a track.
private val authenticatedClient by lazy {
    client.newBuilder()
        .callTimeout(15, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()
}

/** Body of a successful GET, or null for any failure at all. */
internal fun lyricsGet(url: String): String? = runCatching {
    val request = Request.Builder().url(url)
        .header("User-Agent", LYRICS_AGENT)
        .header("Accept", "application/json")
        .build()
    client.newCall(request).execute().use { response ->
        if (response.isSuccessful) response.body?.string() else null
    }
}.getOrNull()

/** Body of an authenticated provider GET without service-specific browser headers. */
internal fun lyricsGetBearer(url: String, bearer: String): String? = runCatching {
    if (bearer.isBlank()) return null
    val request = Request.Builder().url(url)
        .header("User-Agent", LYRICS_AGENT)
        .header("Accept", "application/json, text/plain, */*")
        .header("Authorization", "Bearer $bearer")
        .build()
    authenticatedClient.newCall(request).execute().use { response ->
        if (response.isSuccessful) response.body?.string() else null
    }
}.getOrNull()

/**
 * [lyricsGet], with a bearer token and the headers Apple's own web player
 * sends alongside one — `amp-api.music.apple.com` answers a token with no
 * `Origin` at all the same way it answers a wrong one, with a 403.
 */
internal fun lyricsGetAuthorized(url: String, bearer: String): String? = runCatching {
    val request = Request.Builder().url(url)
        .header("User-Agent", LYRICS_AGENT)
        .header("Accept", "application/json")
        .header("Authorization", "Bearer $bearer")
        .header("Origin", "https://music.apple.com")
        .header("Referer", "https://music.apple.com/")
        .build()
    client.newCall(request).execute().use { response ->
        if (response.isSuccessful) response.body?.string() else null
    }
}.getOrNull()

/**
 * [lyricsGet] with headers of the provider's own choosing — the Chinese
 * catalogues answer an unknown client with an empty page, and only a browser's
 * `Referer` and cookie get the real one.
 */
internal fun lyricsGetWithHeaders(url: String, headers: Map<String, String>): String? = runCatching {
    val request = Request.Builder().url(url)
        .header("User-Agent", BROWSER_AGENT)
        .apply { headers.forEach { (name, value) -> header(name, value) } }
        .build()
    client.newCall(request).execute().use { response ->
        if (response.isSuccessful) response.body?.string() else null
    }
}.getOrNull()

/** A form POST, for the providers whose search only takes one. Null for any failure. */
internal fun lyricsPostForm(
    url: String,
    fields: Map<String, String>,
    headers: Map<String, String> = emptyMap(),
): String? = runCatching {
    val body = FormBody.Builder().apply { fields.forEach { (name, value) -> add(name, value) } }.build()
    val request = Request.Builder().url(url)
        .header("User-Agent", BROWSER_AGENT)
        .apply { headers.forEach { (name, value) -> header(name, value) } }
        .post(body)
        .build()
    client.newCall(request).execute().use { response ->
        if (response.isSuccessful) response.body?.string() else null
    }
}.getOrNull()

private const val BROWSER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/124.0 Safari/537.36"
