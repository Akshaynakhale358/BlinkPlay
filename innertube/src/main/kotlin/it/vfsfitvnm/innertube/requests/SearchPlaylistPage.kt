package it.vfsfitvnm.innertube.requests

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.SearchResponse
import it.vfsfitvnm.innertube.models.MusicResponsiveListItemRenderer
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
