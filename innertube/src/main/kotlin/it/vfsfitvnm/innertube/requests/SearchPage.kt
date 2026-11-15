package it.vfsfitvnm.innertube.requests

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.ContinuationResponse
import it.vfsfitvnm.innertube.models.GridRenderer
import it.vfsfitvnm.innertube.models.MusicShelfRenderer
import it.vfsfitvnm.innertube.models.MusicTwoRowItemRenderer
import it.vfsfitvnm.innertube.models.SearchResponse
import it.vfsfitvnm.innertube.models.bodies.ContinuationBody
import it.vfsfitvnm.innertube.models.bodies.SearchBody
import it.vfsfitvnm.innertube.utils.runCatchingNonCancellable

suspend fun <T : Innertube.Item> Innertube.searchPage(
    body: SearchBody,
    fromMusicShelfRendererContent: (MusicShelfRenderer.Content) -> T?,
    fromMusicTwoRowItemRenderer: (MusicTwoRowItemRenderer) -> T? = { null }
) = runCatchingNonCancellable {
    val response = client.post(search) {
        setBody(body)
        // Keep the complete response: Artist search can return a grid rather than a shelf.
        mask("*")
    }.body<SearchResponse>()

    val sections = response.contents
        ?.tabbedSearchResultsRenderer
        ?.tabs
        .orEmpty()
        .flatMap { tab ->
            tab.tabRenderer
                ?.content
                ?.sectionListRenderer
                ?.contents
                .orEmpty()
        }
        .filter { it.musicShelfRenderer != null || it.gridRenderer != null }

    val items = sections.flatMap { section ->
        buildList {
            section.musicShelfRenderer
                ?.contents
                ?.mapNotNullTo(this, fromMusicShelfRendererContent)
            section.gridRenderer
                ?.items
                ?.forEach { gridItem ->
                    gridItem.musicTwoRowItemRenderer
                        ?.let(fromMusicTwoRowItemRenderer)
                        ?.let(::add)
                    gridItem.musicResponsiveListItemRenderer
                        ?.let { MusicShelfRenderer.Content(musicResponsiveListItemRenderer = it) }
                        ?.let(fromMusicShelfRendererContent)
                        ?.let(::add)
                }
        }
    }.distinctBy(Innertube.Item::key)

    val continuation = sections.asReversed().firstNotNullOfOrNull { section ->
        section.musicShelfRenderer
            ?.continuations
            ?.firstOrNull()
            ?.nextContinuationData
            ?.continuation
            ?: section.gridRenderer
                ?.continuations
                ?.firstOrNull()
                ?.nextContinuationData
                ?.continuation
    }

    Innertube.ItemsPage(items = items, continuation = continuation)
}

suspend fun <T : Innertube.Item> Innertube.searchPage(
    body: ContinuationBody,
    fromMusicShelfRendererContent: (MusicShelfRenderer.Content) -> T?,
    fromMusicTwoRowItemRenderer: (MusicTwoRowItemRenderer) -> T? = { null }
) = runCatchingNonCancellable {
    val response = client.post(search) {
        setBody(body)
        // Continuation responses may be shelf, grid, or append-action payloads.
        mask("*")
    }.body<ContinuationResponse>()

    val shelf = response.continuationContents?.musicShelfContinuation
    val grid = response.continuationContents?.gridContinuation

    val shelfItems = shelf?.contents.orEmpty().mapNotNull(fromMusicShelfRendererContent)
    val gridItems = grid?.items.orEmpty().mapNotNull { item ->
        item.musicTwoRowItemRenderer?.let(fromMusicTwoRowItemRenderer)
            ?: item.musicResponsiveListItemRenderer?.let {
                fromMusicShelfRendererContent(MusicShelfRenderer.Content(musicResponsiveListItemRenderer = it))
            }
    }

    val actionItems = response.onResponseReceivedActions.orEmpty()
        .plus(response.onResponseReceivedEndpoints.orEmpty())
        .flatMap { action -> action.appendContinuationItemsAction?.continuationItems.orEmpty() }
        .mapNotNull { item ->
            item.musicTwoRowItemRenderer?.let(fromMusicTwoRowItemRenderer)
                ?: item.musicResponsiveListItemRenderer?.let {
                    fromMusicShelfRendererContent(MusicShelfRenderer.Content(musicResponsiveListItemRenderer = it))
                }
        }

    val continuation = shelf?.continuations?.firstOrNull()?.nextContinuationData?.continuation
        ?: grid?.continuations?.firstOrNull()?.nextContinuationData?.continuation
        ?: sequenceOf(
            shelf?.contents.orEmpty().lastOrNull()?.continuationItemRenderer?.continuationEndpoint?.token(),
            response.onResponseReceivedActions.orEmpty()
                .plus(response.onResponseReceivedEndpoints.orEmpty())
                .flatMap { it.appendContinuationItemsAction?.continuationItems.orEmpty() }
                .lastOrNull()?.continuationItemRenderer?.continuationEndpoint?.token()
        ).filterNotNull().firstOrNull()

    Innertube.ItemsPage(
        items = (shelfItems + gridItems + actionItems).distinctBy(Innertube.Item::key),
        continuation = continuation
    )
}
