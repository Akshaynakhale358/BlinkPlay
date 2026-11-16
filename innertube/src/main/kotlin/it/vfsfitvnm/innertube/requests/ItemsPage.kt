package it.vfsfitvnm.innertube.requests

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.BrowseResponse
import it.vfsfitvnm.innertube.models.Continuation
import it.vfsfitvnm.innertube.models.ContinuationResponse
import it.vfsfitvnm.innertube.models.GridRenderer
import it.vfsfitvnm.innertube.models.MusicResponsiveListItemRenderer
import it.vfsfitvnm.innertube.models.MusicCarouselShelfRenderer
import it.vfsfitvnm.innertube.models.MusicShelfRenderer
import it.vfsfitvnm.innertube.models.MusicTwoRowItemRenderer
import it.vfsfitvnm.innertube.models.bodies.BrowseBody
import it.vfsfitvnm.innertube.models.bodies.ContinuationBody
import it.vfsfitvnm.innertube.utils.runCatchingNonCancellable

suspend fun <T : Innertube.Item> Innertube.itemsPage(
    body: BrowseBody,
    fromMusicResponsiveListItemRenderer: (MusicResponsiveListItemRenderer) -> T? = { null },
    fromMusicTwoRowItemRenderer: (MusicTwoRowItemRenderer) -> T? = { null },
) = runCatchingNonCancellable {
    val response = client.post(browse) {
        setBody(body)
//        mask("contents.singleColumnBrowseResultsRenderer.tabs.tabRenderer.content.sectionListRenderer.contents(musicPlaylistShelfRenderer(continuations,contents.$musicResponsiveListItemRendererMask),gridRenderer(continuations,items.$musicTwoRowItemRendererMask))")
    }.body<BrowseResponse>()

    val sectionContents = buildList {
        addAll(response.contents?.twoColumnBrowseResultsRenderer?.secondaryContents?.sectionListRenderer?.contents.orEmpty())
        addAll(response.contents?.twoColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents.orEmpty())
        addAll(response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents.orEmpty())
        addAll(response.contents?.sectionListRenderer?.contents.orEmpty())
    }

    itemsPageFromMusicShelRendererOrGridRenderer(
        musicShelfRenderer = sectionContents
            ?.firstNotNullOfOrNull { it.musicPlaylistShelfRenderer ?: it.musicShelfRenderer },
        gridRenderer = sectionContents
            ?.firstNotNullOfOrNull { it.gridRenderer },
        fromMusicResponsiveListItemRenderer = fromMusicResponsiveListItemRenderer,
        fromMusicTwoRowItemRenderer = fromMusicTwoRowItemRenderer,
    )
}

suspend fun <T : Innertube.Item> Innertube.itemsPage(
    body: ContinuationBody,
    fromMusicResponsiveListItemRenderer: (MusicResponsiveListItemRenderer) -> T? = { null },
    fromMusicTwoRowItemRenderer: (MusicTwoRowItemRenderer) -> T? = { null },
) = runCatchingNonCancellable {
    val response = client.post(browse) {
        setBody(body)
//        mask("contents.singleColumnBrowseResultsRenderer.tabs.tabRenderer.content.sectionListRenderer.contents(musicPlaylistShelfRenderer(continuations,contents.$musicResponsiveListItemRendererMask),gridRenderer(continuations,items.$musicTwoRowItemRendererMask))")
    }.body<ContinuationResponse>()

    val actionItems = response.onResponseReceivedActions
        ?.plus(response.onResponseReceivedEndpoints.orEmpty())
        ?.flatMap { action ->
            (action.appendContinuationItemsAction ?: action.reloadContinuationItemsCommand)
                ?.continuationItems.orEmpty()
        }.orEmpty()
    val nextToken = actionItems
        .firstNotNullOfOrNull { it.continuationItemRenderer?.continuationEndpoint?.token() }
    val actionShelf = actionItems
        .mapNotNull { it.musicResponsiveListItemRenderer }
        .takeIf { it.isNotEmpty() }
        ?.let { renderers ->
            MusicShelfRenderer(
                contents = renderers.map { MusicShelfRenderer.Content(musicResponsiveListItemRenderer = it) },
                continuations = nextToken?.let { listOf(Continuation(Continuation.Data(it))) }
            )
        }
    val actionCarousel = actionItems
        .filter { it.musicTwoRowItemRenderer != null || it.musicResponsiveListItemRenderer != null }
        .takeIf { it.isNotEmpty() }
        ?.let { items ->
            MusicCarouselShelfRenderer(
                contents = items.map {
                    MusicCarouselShelfRenderer.Content(
                        musicTwoRowItemRenderer = it.musicTwoRowItemRenderer,
                        musicResponsiveListItemRenderer = it.musicResponsiveListItemRenderer,
                    )
                },
                continuations = nextToken?.let { listOf(Continuation(Continuation.Data(it))) }
            )
        }

    itemsPageFromMusicShelRendererOrGridRenderer(
        musicShelfRenderer = response.continuationContents
            ?.musicPlaylistShelfContinuation
            ?: response.continuationContents?.musicShelfContinuation
            ?: actionShelf,
        carouselShelfRenderer = response.continuationContents?.musicCarouselShelfContinuation ?: actionCarousel,
        gridRenderer = response.continuationContents?.gridContinuation,
        fromMusicResponsiveListItemRenderer = fromMusicResponsiveListItemRenderer,
        fromMusicTwoRowItemRenderer = fromMusicTwoRowItemRenderer,
    )
}

private fun <T : Innertube.Item> itemsPageFromMusicShelRendererOrGridRenderer(
    musicShelfRenderer: MusicShelfRenderer?,
    carouselShelfRenderer: MusicCarouselShelfRenderer? = null,
    gridRenderer: GridRenderer?,
    fromMusicResponsiveListItemRenderer: (MusicResponsiveListItemRenderer) -> T?,
    fromMusicTwoRowItemRenderer: (MusicTwoRowItemRenderer) -> T?,
): Innertube.ItemsPage<T>? {
    return if (musicShelfRenderer != null) {
        Innertube.ItemsPage(
            continuation = musicShelfRenderer
                .continuations
                ?.firstOrNull()
                ?.nextContinuationData
                ?.continuation,
            items = musicShelfRenderer
                .contents
                ?.mapNotNull(MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
                ?.mapNotNull(fromMusicResponsiveListItemRenderer)
        )
    } else if (carouselShelfRenderer != null) {
        Innertube.ItemsPage(
            continuation = carouselShelfRenderer.continuations
                ?.firstOrNull()
                ?.nextContinuationData
                ?.continuation,
            items = carouselShelfRenderer.contents?.mapNotNull { content ->
                content.musicResponsiveListItemRenderer?.let(fromMusicResponsiveListItemRenderer)
                    ?: content.musicTwoRowItemRenderer?.let(fromMusicTwoRowItemRenderer)
            }
        )
    } else if (gridRenderer != null) {
        Innertube.ItemsPage(
            continuation = gridRenderer.continuations
                ?.firstOrNull()
                ?.nextContinuationData
                ?.continuation,
            items = gridRenderer
                .items
                ?.mapNotNull { item ->
                    item.musicResponsiveListItemRenderer?.let(fromMusicResponsiveListItemRenderer)
                        ?: item.musicTwoRowItemRenderer?.let(fromMusicTwoRowItemRenderer)
                }
        )
    } else {
        null
    }
}
