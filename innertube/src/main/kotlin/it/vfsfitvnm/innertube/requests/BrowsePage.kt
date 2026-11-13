package it.vfsfitvnm.innertube.requests

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.BrowseResponse
import it.vfsfitvnm.innertube.models.Context
import it.vfsfitvnm.innertube.models.GridRenderer
import it.vfsfitvnm.innertube.models.MusicCarouselShelfRenderer
import it.vfsfitvnm.innertube.models.MusicShelfRenderer
import it.vfsfitvnm.innertube.models.MusicTwoRowItemRenderer
import it.vfsfitvnm.innertube.models.bodies.BrowseBody
import it.vfsfitvnm.innertube.models.bodies.SearchBody
import it.vfsfitvnm.innertube.utils.from
import it.vfsfitvnm.innertube.utils.runCatchingNonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * A shelf discovered from YouTube Music itself. Category names, order and destinations
 * are deliberately not maintained in BlinkPlay.
 */
data class MusicBrowseSection(
    val title: String,
    val browseId: String?,
    val params: String?,
    val items: List<Innertube.Item>,
    val continuation: String?,
)

data class MusicBrowsePage(
    val title: String?,
    val sections: List<MusicBrowseSection>,
)

data class MusicDiscovery(
    val home: MusicBrowsePage?,
    val explore: MusicBrowsePage?,
) {
    val sections: List<MusicBrowseSection>
        get() = (home?.sections.orEmpty() + explore?.sections.orEmpty())
            .distinctBy { listOf(it.title, it.browseId, it.params) }
}

private val browseResponseJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

private val discoveryMutex = Mutex()
@Volatile private var cachedMusicDiscovery: MusicDiscovery? = null
@Volatile private var cachedMusicDiscoveryAt: Long = 0L
private const val MUSIC_DISCOVERY_CACHE_MILLIS = 5 * 60 * 1000L
/**
 * YouTube occasionally returns a valid-looking shelf with only one or two songs.
 * Enrich sparse song shelves through the WEB_REMIX song-search route, retaining
 * YouTube's original ranking first and de-duplicating by video ID.
 */
private suspend fun enrichSparseSongSections(
    page: MusicBrowsePage?,
    context: Context,
): MusicBrowsePage? {
    page ?: return null
    val sections = page.sections.map { section ->
        val originalSongs = section.items.filterIsInstance<Innertube.SongItem>()
        val isMixedOrNonSongShelf = section.items.isNotEmpty() &&
            originalSongs.size != section.items.size
        val isNonSongCategory = Regex(
            "album|artist|playlist|video|podcast|radio|audiobook",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(section.title)
        if (isMixedOrNonSongShelf || isNonSongCategory) {
            return@map section
        }

        val prioritizedSongs = prioritizeIndianLanguageSongs(
            originalSongs, section.title, context
        )
        if (prioritizedSongs.size >= 10) {
            return@map section.copy(items = prioritizedSongs)
        }

        // Keep this shelf strictly tied to its own YouTube Music browse root.
        // A generic search fallback can silently turn (for example) Top Songs into
        // New Releases or another unrelated category.
        section.copy(items = prioritizedSongs)
    }
    return page.copy(sections = sections)
}


private fun JsonElement.descendants(): Sequence<JsonElement> = sequence {
    yield(this@descendants)
    when (val node = this@descendants) {
        is JsonObject -> node.values.forEach { yieldAll(it.descendants()) }
        is JsonArray -> node.forEach { yieldAll(it.descendants()) }
        else -> Unit
    }
}

/**
 * Parse server shelves independently. This is intentionally more tolerant than decoding the
 * complete response into one Kotlin model: YouTube frequently adds unrelated renderer fields.
 */
private fun parseBrowseSections(root: JsonElement): List<MusicBrowseSection> {
    val sectionLists = root.descendants().mapNotNull { node ->
        val obj = node as? JsonObject ?: return@mapNotNull null
        obj["sectionListRenderer"] as? JsonObject
    }.toList()

    val contents = sectionLists.flatMap { list ->
        (list["contents"] as? JsonArray).orEmpty()
    }

    return contents.mapNotNull { element ->
        val content = element as? JsonObject ?: return@mapNotNull null

        val carouselElement = content["musicCarouselShelfRenderer"]
            ?: content["musicImmersiveCarouselShelfRenderer"]
        if (carouselElement != null) {
            val carousel = runCatching {
                browseResponseJson.decodeFromJsonElement(MusicCarouselShelfRenderer.serializer(), carouselElement)
            }.getOrNull()
            if (carousel != null) {
                val header = carousel.header?.musicCarouselShelfBasicHeaderRenderer
                val title = header?.title?.text
                    ?: carousel.header?.musicTwoRowItemRenderer?.title?.text
                    ?: return@mapNotNull null
                val endpoint = header?.moreContentButton?.buttonRenderer
                    ?.navigationEndpoint?.browseEndpoint
                val items = carousel.contents.orEmpty().mapNotNull { item ->
                    item.musicResponsiveListItemRenderer?.let(Innertube.SongItem::from)
                        ?: item.musicTwoRowItemRenderer?.let(::toMusicItem)
                }.distinctBy(Innertube.Item::key)
                return@mapNotNull MusicBrowseSection(
                    title = title,
                    browseId = endpoint?.browseId,
                    params = endpoint?.params,
                    items = items,
                    continuation = null,
                )
            }
        }

        val shelfElement = content["musicShelfRenderer"] ?: content["musicPlaylistShelfRenderer"]
        if (shelfElement != null) {
            val shelf = runCatching {
                browseResponseJson.decodeFromJsonElement(MusicShelfRenderer.serializer(), shelfElement)
            }.getOrNull()
            if (shelf != null) {
                val title = shelf.title?.text ?: return@mapNotNull null
                val endpoint = shelf.bottomEndpoint?.browseEndpoint
                val items = shelf.contents.orEmpty().mapNotNull { item ->
                    item.musicResponsiveListItemRenderer?.let(Innertube.SongItem::from)
                }.distinctBy(Innertube.Item::key)
                return@mapNotNull MusicBrowseSection(
                    title = title,
                    browseId = endpoint?.browseId,
                    params = endpoint?.params,
                    items = items,
                    continuation = shelf.continuations?.firstOrNull()?.nextContinuationData?.continuation,
                )
            }
        }

        val gridElement = content["gridRenderer"]
        if (gridElement != null) {
            val grid = runCatching {
                browseResponseJson.decodeFromJsonElement(GridRenderer.serializer(), gridElement)
            }.getOrNull()
            val title = content["header"]?.let { header ->
                runCatching {
                    browseResponseJson.decodeFromJsonElement(MusicCarouselShelfRenderer.Header.serializer(), header)
                }.getOrNull()?.musicCarouselShelfBasicHeaderRenderer?.title?.text
            } ?: return@mapNotNull null
            val items = grid?.items.orEmpty()
                .mapNotNull { item ->
                    item.musicResponsiveListItemRenderer?.let(Innertube.SongItem::from)
                        ?: item.musicTwoRowItemRenderer?.let(::toMusicItem)
                }
                .distinctBy(Innertube.Item::key)
            return@mapNotNull MusicBrowseSection(
                title = title,
                browseId = null,
                params = null,
                items = items,
                continuation = null,
            )
        }

        null
    }
        .distinctBy { listOf(it.title, it.browseId, it.params) }
}

/**
 * Re-rank recommendation shelves toward Indian-language music. Language-specific
 * YouTube Music searches are used as ordered preference buckets; original feed
 * results remain as fallback, and English-search results are appended last.
 * Existing browse/search roots and category filters are not modified.
 */
suspend fun prioritizeIndianLanguageSongs(
    songs: List<Innertube.SongItem>,
    categoryTitle: String,
    context: Context,
): List<Innertube.SongItem> {
    if (songs.isEmpty()) return songs
    // Language searches may only reorder items already returned by this category's
    // own YouTube Music feed. They must never inject songs from another category.
    val original = songs.distinctBy(Innertube.SongItem::key)
    val allowedIds = original.map(Innertube.SongItem::key).toSet()
    suspend fun matching(query: String): List<Innertube.SongItem> =
        Innertube.searchPage(
            body = SearchBody(
                context = context,
                query = query,
                params = Innertube.SearchFilter.Song.value
            ),
            fromMusicShelfRendererContent = Innertube.SongItem::from
        )?.getOrNull()?.items.orEmpty().filter { it.key in allowedIds }

    val hindi = matching("$categoryTitle Hindi songs")
    val marathi = matching("$categoryTitle Marathi songs")
    val punjabi = matching("$categoryTitle Punjabi songs")
    val otherIndian = matching("$categoryTitle Tamil Telugu Kannada Malayalam Bengali Gujarati songs")
    val english = matching("$categoryTitle English songs")
    val prioritizedIds = (hindi + marathi + punjabi + otherIndian + english)
        .map(Innertube.SongItem::key).toSet()
    return (hindi + marathi + punjabi + otherIndian +
        original.filterNot { it.key in prioritizedIds } + english)
        .distinctBy(Innertube.SongItem::key)
}

/** Fetch and parse one server-defined browse feed. */
suspend fun Innertube.browsePage(body: BrowseBody) = runCatchingNonCancellable {
    val clientVersion = currentWebClientVersion()
    val requestBody = body.copy(
        context = body.context.copy(
            client = body.context.client.copy(clientVersion = clientVersion)
        )
    )
    val raw = client.post(browse) {
        headers.remove("X-YouTube-Client-Version")
        headers.append("X-YouTube-Client-Version", clientVersion)
        setBody(requestBody)
        // Ask for the complete browse tree; nested masks are fragile when YouTube
        // changes renderer fields and can make a valid feed look empty.
        mask("*")
    }.body<String>()
    val root = browseResponseJson.parseToJsonElement(raw)
    val response = runCatching {
        browseResponseJson.decodeFromJsonElement(BrowseResponse.serializer(), root)
    }.getOrNull()

    MusicBrowsePage(
        title = response?.header?.musicDetailHeaderRenderer?.title?.text
            ?: response?.header?.musicImmersiveHeaderRenderer?.title?.text,
        sections = parseBrowseSections(root),
    )
}

/** India WEB context; this is data localization, not a hard-coded category list. */
private val IndiaMusicBrowseContext = Context.DefaultWeb.copy(
    client = Context.DefaultWeb.client.copy(hl = "en", gl = "IN")
)

suspend fun Innertube.musicHomePage(context: Context = IndiaMusicBrowseContext) =
    browsePage(BrowseBody(context = context, browseId = MUSIC_HOME_BROWSE_ID))

suspend fun Innertube.musicExplorePage(context: Context = IndiaMusicBrowseContext) =
    browsePage(BrowseBody(context = context, browseId = MUSIC_EXPLORE_BROWSE_ID))

/**
 * Stable feed adapter with a short cache, serialized refreshes, retry and stale-cache fallback.
 * If one upstream feed fails, the other is still usable; if both fail, the last good feed remains
 * available to the UI instead of leaving the page empty.
 */
suspend fun Innertube.musicDiscovery(
    forceRefresh: Boolean = false,
    context: Context = IndiaMusicBrowseContext,
) = runCatchingNonCancellable {
    discoveryMutex.withLock {
        val cached = cachedMusicDiscovery
        val cacheIsFresh = cached != null &&
            System.currentTimeMillis() - cachedMusicDiscoveryAt < MUSIC_DISCOVERY_CACHE_MILLIS
        if (!forceRefresh && cacheIsFresh) return@withLock cached!!

        var lastError: Throwable? = null
        repeat(2) { attempt ->
            val (homeResult, exploreResult) = coroutineScope {
                val home = async { musicHomePage(context) }
                val explore = async { musicExplorePage(context) }
                home.await() to explore.await()
            }
            val home = enrichSparseSongSections(homeResult?.getOrNull(), context)
            val explore = enrichSparseSongSections(exploreResult?.getOrNull(), context)
            val validHome = home?.takeIf { it.sections.isNotEmpty() }
            val validExplore = explore?.takeIf { it.sections.isNotEmpty() }
            if (validHome != null || validExplore != null) {
                val previous = cachedMusicDiscovery
                val fresh = MusicDiscovery(
                    home = validHome ?: previous?.home,
                    explore = validExplore ?: previous?.explore
                )
                cachedMusicDiscovery = fresh
                cachedMusicDiscoveryAt = System.currentTimeMillis()
                return@withLock fresh
            }
            lastError = homeResult?.exceptionOrNull()
                ?: exploreResult?.exceptionOrNull()
                ?: IllegalStateException("YouTube Music returned no supported shelves")
            if (attempt == 0) delay(350L)
        }

        cachedMusicDiscovery?.let { return@withLock it }
        throw lastError ?: IllegalStateException("YouTube Music feeds are unavailable")
    }
}

/** Open a shelf destination exactly as supplied by YouTube Music. */
suspend fun Innertube.browseSection(
    section: MusicBrowseSection,
    context: Context = IndiaMusicBrowseContext,
): Result<MusicBrowsePage?> {
    val browseId = section.browseId ?: return Result.success(null)
    return browsePage(
        BrowseBody(context = context, browseId = browseId, params = section.params)
    )?.map { it } ?: Result.success(null)
}

private fun toMusicItem(renderer: MusicTwoRowItemRenderer): Innertube.Item? =
    Innertube.PlaylistItem.from(renderer)
        ?: Innertube.AlbumItem.from(renderer)
        ?: Innertube.ArtistItem.from(renderer)

private const val MUSIC_HOME_BROWSE_ID = "FEmusic_home"
private const val MUSIC_EXPLORE_BROWSE_ID = "FEmusic_explore"
