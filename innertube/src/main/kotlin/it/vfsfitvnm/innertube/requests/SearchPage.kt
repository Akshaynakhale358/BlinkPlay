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

    // Search artist grids can put the continuation renderer inside the final grid item,
    // rather than in GridRenderer.continuations. Read both response shapes.
    val continuation = sections.asReversed().firstNotNullOfOrNull { section ->
        section.musicShelfRenderer
            ?.continuations
            ?.firstOrNull()
            ?.nextContinuationData
            ?.continuation
            ?: section.musicShelfRenderer
                ?.contents
                ?.lastOrNull()
                ?.continuationItemRenderer
                ?.continuationEndpoint
                ?.token()
            ?: section.gridRenderer
                ?.continuations
                ?.firstOrNull()
                ?.nextContinuationData
                ?.continuation
            ?: section.gridRenderer
                ?.items
                ?.lastOrNull()
                ?.continuationItemRenderer
                ?.continuationEndpoint
                ?.token()
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
        mask("*")
    }.body<ContinuationResponse>()

    val shelf = response.continuationContents?.musicShelfContinuation
    val grid = response.continuationContents?.gridContinuation
    val carousel = response.continuationContents?.musicCarouselShelfContinuation

    val shelfItems = shelf?.contents.orEmpty().mapNotNull(fromMusicShelfRendererContent)
    val gridItems = grid?.items.orEmpty().mapNotNull { item ->
        item.musicTwoRowItemRenderer?.let(fromMusicTwoRowItemRenderer)
            ?: item.musicResponsiveListItemRenderer?.let {
                fromMusicShelfRendererContent(MusicShelfRenderer.Content(musicResponsiveListItemRenderer = it))
            }
    }

    val carouselItems = carousel?.contents.orEmpty().mapNotNull { item ->
        item.musicTwoRowItemRenderer?.let(fromMusicTwoRowItemRenderer)
            ?: item.musicResponsiveListItemRenderer?.let {
                fromMusicShelfRendererContent(MusicShelfRenderer.Content(musicResponsiveListItemRenderer = it))
            }
    }

    val responseActions = response.onResponseReceivedActions.orEmpty()
        .plus(response.onResponseReceivedEndpoints.orEmpty())
    val actionContinuationItems = responseActions.flatMap { action ->
        (action.appendContinuationItemsAction ?: action.reloadContinuationItemsCommand)
            ?.continuationItems.orEmpty()
    }
    val actionItems = actionContinuationItems.mapNotNull { item ->
        item.musicTwoRowItemRenderer?.let(fromMusicTwoRowItemRenderer)
            ?: item.musicResponsiveListItemRenderer?.let {
                fromMusicShelfRendererContent(MusicShelfRenderer.Content(musicResponsiveListItemRenderer = it))
            }
    }

    val continuation = shelf?.continuations?.firstOrNull()?.nextContinuationData?.continuation
        ?: shelf?.contents?.lastOrNull()?.continuationItemRenderer?.continuationEndpoint?.token()
        ?: grid?.continuations?.firstOrNull()?.nextContinuationData?.continuation
        ?: grid?.items?.lastOrNull()?.continuationItemRenderer?.continuationEndpoint?.token()
        ?: carousel?.continuations?.firstOrNull()?.nextContinuationData?.continuation
        ?: actionContinuationItems.lastOrNull()?.continuationItemRenderer?.continuationEndpoint?.token()

    Innertube.ItemsPage(
        items = (shelfItems + gridItems + carouselItems + actionItems).distinctBy(Innertube.Item::key),
        continuation = continuation
    )
}
