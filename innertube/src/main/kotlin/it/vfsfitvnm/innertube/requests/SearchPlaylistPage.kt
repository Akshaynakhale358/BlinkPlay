package it.vfsfitvnm.innertube.requests

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.SearchResponse
import it.vfsfitvnm.innertube.models.MusicResponsiveListItemRenderer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import it.vfsfitvnm.innertube.models.bodies.SearchBody
import it.vfsfitvnm.innertube.utils.from
import it.vfsfitvnm.innertube.utils.runCatchingNonCancellable

/**
 * Dedicated playlist search.
 *
 * YouTube Music places playlist results in the Playlists tab, which is not
 * guaranteed to be the first tab. The generic search parser used to inspect
 * only the first tab, so playlist searches could successfully return HTTP 200
 * and still produce an empty list.
 */
suspend fun Innertube.searchPlaylistPage(body: SearchBody) = runCatchingNonCancellable {
    val response = client.post(search) {
        setBody(body)
        mask(
            "contents.tabbedSearchResultsRenderer(" +
                "tabs.tabRenderer(" +
                "content.sectionListRenderer.contents(" +
                "musicShelfRenderer(continuations,contents.$musicResponsiveListItemRendererMask)," +
                "musicPlaylistShelfRenderer(continuations,contents.$musicResponsiveListItemRendererMask)," +
                "gridRenderer(items.$musicTwoRowItemRendererMask)" +
                ")))"
        )
    }.body<SearchResponse>()

    val tabs = response.contents
        ?.tabbedSearchResultsRenderer
        ?.tabs
        .orEmpty()

    val playlistTabs = tabs.filter { tab ->
        val title = tab.tabRenderer?.title.orEmpty()
        val identifier = tab.tabRenderer?.tabIdentifier.orEmpty()
        title.contains("playlist", ignoreCase = true) ||
            identifier.contains("playlist", ignoreCase = true)
    }

    // Prefer the explicit Playlists tab, but inspect every tab as a fallback
    // because YouTube changes tab labels and ordering by locale/experiment.
    val orderedTabs = (playlistTabs + tabs.filterNot { it in playlistTabs }).distinct()

    val playlists = orderedTabs.flatMap { tab ->
        tab.tabRenderer
            ?.content
            ?.sectionListRenderer
            ?.contents
            .orEmpty()
            .flatMap { section ->
                buildList {
                    section.musicShelfRenderer
                        ?.contents
                        ?.mapNotNullTo(this) { content ->
                            content.musicResponsiveListItemRenderer?.let { renderer ->
                                val titleRun = renderer.flexColumns
                                    .firstOrNull()
                                    ?.musicResponsiveListItemFlexColumnRenderer
                                    ?.text
                                    ?.runs
                                    ?.firstOrNull()
                                val browseEndpoint = renderer.navigationEndpoint?.browseEndpoint
                                    ?: titleRun?.navigationEndpoint?.browseEndpoint
                                Innertube.PlaylistItem(
                                    info = Innertube.Info(
                                        name = titleRun?.text,
                                        endpoint = browseEndpoint
                                    ),
                                    channel = renderer.flexColumns
                                        .getOrNull(1)
                                        ?.musicResponsiveListItemFlexColumnRenderer
                                        ?.text
                                        ?.runs
                                        ?.firstOrNull()
                                        ?.let(Innertube::Info),
                                    songCount = null,
                                    thumbnail = renderer.thumbnail
                                        ?.musicThumbnailRenderer
                                        ?.thumbnail
                                        ?.thumbnails
                                        ?.firstOrNull()
                                ).takeIf { it.info?.endpoint?.browseId != null }
                            }
                        }
                    section.gridRenderer
                        ?.items
                        ?.mapNotNull { it.musicTwoRowItemRenderer }
                        ?.mapNotNullTo(this, Innertube.PlaylistItem::from)
                }
            }
    }.distinctBy(Innertube.PlaylistItem::key)

    playlists
}


private val podcastSearchJson = Json { ignoreUnknownKeys = true; isLenient = true }

private fun JsonElement.podcastSearchDescendants(): Sequence<JsonElement> = sequence {
    yield(this@podcastSearchDescendants)
    when (val node = this@podcastSearchDescendants) {
        is JsonObject -> node.values.forEach { yieldAll(it.podcastSearchDescendants()) }
        is kotlinx.serialization.json.JsonArray -> node.forEach { yieldAll(it.podcastSearchDescendants()) }
        else -> Unit
    }
}

/**
 * Search the dedicated YouTube Music Podcasts result type.
 * Podcast shows use MPSP browse IDs; they are not ordinary featured playlists.
 */
suspend fun Innertube.searchPodcastPage(body: SearchBody) = runCatchingNonCancellable {
    // Keep the dedicated Podcast filter, but inspect the complete response tree.
    // YouTube Music has returned podcast rows outside the usual tabbed-search
    // wrapper in some client experiments; parsing only tabs silently drops them.
    val raw = client.post(search) {
        setBody(body)
        mask("*")
    }.body<String>()
    val root = podcastSearchJson.parseToJsonElement(raw)
    val response = runCatching {
        podcastSearchJson.decodeFromJsonElement<SearchResponse>(root)
    }.getOrNull()

    val tabs = response?.contents
        ?.tabbedSearchResultsRenderer
        ?.tabs
        .orEmpty()

    val tabResults = tabs.flatMap { tab ->
        tab.tabRenderer
            ?.content
            ?.sectionListRenderer
            ?.contents
            .orEmpty()
            .flatMap { section ->
                buildList {
                    section.musicShelfRenderer
                        ?.contents
                        ?.mapNotNull { it.musicResponsiveListItemRenderer }
                        ?.mapNotNull { renderer ->
                            val titleRun = renderer.flexColumns
                                .firstOrNull()
                                ?.musicResponsiveListItemFlexColumnRenderer
                                ?.text
                                ?.runs
                                ?.firstOrNull()
                            val endpoint = renderer.navigationEndpoint?.browseEndpoint
                                ?: titleRun?.navigationEndpoint?.browseEndpoint
                            val browseId = endpoint?.browseId
                            if (browseId.isNullOrBlank()) return@mapNotNull null

                            Innertube.PlaylistItem(
                                info = Innertube.Info(name = titleRun?.text, endpoint = endpoint),
                                channel = renderer.flexColumns
                                    .getOrNull(1)
                                    ?.musicResponsiveListItemFlexColumnRenderer
                                    ?.text
                                    ?.runs
                                    ?.firstOrNull()
                                    ?.let(Innertube::Info),
                                songCount = null,
                                thumbnail = renderer.thumbnail
                                    ?.musicThumbnailRenderer
                                    ?.thumbnail
                                    ?.thumbnails
                                    ?.firstOrNull()
                            )
                        }
                        ?.forEach { add(it) }
                    section.gridRenderer
                        ?.items
                        ?.mapNotNull { it.musicTwoRowItemRenderer }
                        ?.mapNotNull { Innertube.PlaylistItem.from(it) }
                        ?.forEach { add(it) }
                }
            }
    }

    val recursiveFallback = if (tabResults.isEmpty()) {
        root.podcastSearchDescendants()
            .mapNotNull { node ->
                val rendererJson = (node as? JsonObject)
                    ?.get("musicResponsiveListItemRenderer") ?: return@mapNotNull null
                runCatching {
                    podcastSearchJson.decodeFromJsonElement<MusicResponsiveListItemRenderer>(rendererJson)
                }.getOrNull()?.let { renderer ->
                    val titleRun = renderer.flexColumns
                        .firstOrNull()
                        ?.musicResponsiveListItemFlexColumnRenderer
                        ?.text
                        ?.runs
                        ?.firstOrNull()
                    val endpoint = renderer.navigationEndpoint?.browseEndpoint
                        ?: titleRun?.navigationEndpoint?.browseEndpoint
                    val browseId = endpoint?.browseId
                    if (browseId.isNullOrBlank()) null else Innertube.PlaylistItem(
                        info = Innertube.Info(name = titleRun?.text, endpoint = endpoint),
                        channel = renderer.flexColumns
                            .getOrNull(1)
                            ?.musicResponsiveListItemFlexColumnRenderer
                            ?.text
                            ?.runs
                            ?.firstOrNull()
                            ?.let(Innertube::Info),
                        songCount = null,
                        thumbnail = renderer.thumbnail
                            ?.musicThumbnailRenderer
                            ?.thumbnail
                            ?.thumbnails
                            ?.firstOrNull()
                    )
                }
            }
            .toList()
    } else emptyList()

    // The dedicated Podcast search may include other browseable rows in
    // experimental response layouts. Keep only YouTube Music Podcast show
    // roots; their MPSP browse IDs are then expanded into episode feeds.
    (tabResults + recursiveFallback)
        .filter { it.key.startsWith("MPSP", ignoreCase = true) }
        .distinctBy(Innertube.PlaylistItem::key)
}
