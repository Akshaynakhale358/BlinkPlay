package it.vfsfitvnm.innertube.requests

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.ContinuationResponse
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

    // Keep the parent SectionListRenderer: YouTube may place the continuation
    // token on the section itself, not inside the shelf/grid.
    val sectionLists = response.contents
        ?.tabbedSearchResultsRenderer
        ?.tabs
        .orEmpty()
        .mapNotNull { it.tabRenderer?.content?.sectionListRenderer }

    val sections = sectionLists.flatMap { it.contents.orEmpty() }

    val items = sections.flatMap { section ->
        buildList {
            section.musicShelfRenderer?.contents
                ?.mapNotNullTo(this, fromMusicShelfRendererContent)
            section.musicPlaylistShelfRenderer?.contents
                ?.mapNotNullTo(this, fromMusicShelfRendererContent)
            section.musicCarouselShelfRenderer?.contents.orEmpty().forEach { item ->
                item.musicTwoRowItemRenderer?.let(fromMusicTwoRowItemRenderer)?.let(::add)
                item.musicResponsiveListItemRenderer
                    ?.let { MusicShelfRenderer.Content(musicResponsiveListItemRenderer = it) }
                    ?.let(fromMusicShelfRendererContent)
                    ?.let(::add)
            }
            section.gridRenderer?.items.orEmpty().forEach { item ->
                item.musicTwoRowItemRenderer?.let(fromMusicTwoRowItemRenderer)?.let(::add)
                item.musicResponsiveListItemRenderer
                    ?.let { MusicShelfRenderer.Content(musicResponsiveListItemRenderer = it) }
                    ?.let(fromMusicShelfRendererContent)
                    ?.let(::add)
            }
        }
    }.distinctBy(Innertube.Item::key)

    val continuation = sections.asReversed().firstNotNullOfOrNull { section ->
        section.musicShelfRenderer?.continuations?.firstOrNull()?.nextContinuationData?.continuation
            ?: section.musicShelfRenderer?.contents?.lastOrNull()?.continuationItemRenderer?.continuationEndpoint?.token()
            ?: section.musicPlaylistShelfRenderer?.continuations?.firstOrNull()?.nextContinuationData?.continuation
            ?: section.musicPlaylistShelfRenderer?.contents?.lastOrNull()?.continuationItemRenderer?.continuationEndpoint?.token()
            ?: section.musicCarouselShelfRenderer?.continuations?.firstOrNull()?.nextContinuationData?.continuation
            ?: section.gridRenderer?.continuations?.firstOrNull()?.nextContinuationData?.continuation
            ?: section.gridRenderer?.items?.lastOrNull()?.continuationItemRenderer?.continuationEndpoint?.token()
            ?: section.continuationItemRenderer?.continuationEndpoint?.token()
    } ?: sectionLists.asReversed().firstNotNullOfOrNull { list ->
        list.continuations?.firstOrNull()?.nextContinuationData?.continuation
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

    val playlistShelf = response.continuationContents?.musicPlaylistShelfContinuation
    val shelf = response.continuationContents?.musicShelfContinuation
    val grid = response.continuationContents?.gridContinuation
    val carousel = response.continuationContents?.musicCarouselShelfContinuation

    fun shelfItems(renderer: MusicShelfRenderer?) =
        renderer?.contents.orEmpty().mapNotNull(fromMusicShelfRendererContent)

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

    val responseActions = response.onResponseReceivedActions.orEmpty() +
        response.onResponseReceivedEndpoints.orEmpty()
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

    val continuation = playlistShelf?.continuations?.firstOrNull()?.nextContinuationData?.continuation
        ?: playlistShelf?.contents?.lastOrNull()?.continuationItemRenderer?.continuationEndpoint?.token()
        ?: shelf?.continuations?.firstOrNull()?.nextContinuationData?.continuation
        ?: shelf?.contents?.lastOrNull()?.continuationItemRenderer?.continuationEndpoint?.token()
        ?: grid?.continuations?.firstOrNull()?.nextContinuationData?.continuation
        ?: grid?.items?.lastOrNull()?.continuationItemRenderer?.continuationEndpoint?.token()
        ?: carousel?.continuations?.firstOrNull()?.nextContinuationData?.continuation
        ?: actionContinuationItems.lastOrNull()?.continuationItemRenderer?.continuationEndpoint?.token()

    Innertube.ItemsPage(
        items = (shelfItems(playlistShelf) + shelfItems(shelf) + gridItems + carouselItems + actionItems)
            .distinctBy(Innertube.Item::key),
        continuation = continuation
    )
}
