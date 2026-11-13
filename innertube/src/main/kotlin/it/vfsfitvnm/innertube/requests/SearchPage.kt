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
        mask("contents.tabbedSearchResultsRenderer.tabs.tabRenderer.content.sectionListRenderer.contents(musicShelfRenderer(continuations,contents.$musicResponsiveListItemRendererMask),musicPlaylistShelfRenderer(continuations,contents.$musicResponsiveListItemRendererMask),gridRenderer(items.$musicTwoRowItemRendererMask))")
    }.body<SearchResponse>()

    // Search filters can return the requested results on a non-first tab.
    // Collect matching shelves and grids from every tab so songs, albums,
    // artists and playlists all use the same reliable parser.
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
                ?.mapNotNull { it.musicTwoRowItemRenderer }
                ?.mapNotNullTo(this, fromMusicTwoRowItemRenderer)
        }
    }.distinctBy(Innertube.Item::key)

    val continuation = sections.asReversed().mapNotNull { section ->
        section.musicShelfRenderer
            ?.continuations
            ?.firstOrNull()
            ?.nextContinuationData
            ?.continuation
    }.firstOrNull()

    Innertube.ItemsPage(items = items, continuation = continuation)
}

suspend fun <T : Innertube.Item> Innertube.searchPage(
    body: ContinuationBody,
    fromMusicShelfRendererContent: (MusicShelfRenderer.Content) -> T?,
    fromMusicTwoRowItemRenderer: (MusicTwoRowItemRenderer) -> T? = { null }
) = runCatchingNonCancellable {
    val response = client.post(search) {
        setBody(body)
        mask("continuationContents.musicShelfContinuation(continuations,contents.$musicResponsiveListItemRendererMask)")
    }.body<ContinuationResponse>()

    response
        .continuationContents
        ?.musicShelfContinuation
        ?.toItemsPage(fromMusicShelfRendererContent)
}

private fun <T : Innertube.Item> MusicShelfRenderer?.toItemsPage(mapper: (MusicShelfRenderer.Content) -> T?) =
    Innertube.ItemsPage(
        items = this?.contents?.mapNotNull(mapper),
        continuation = this?.continuations?.firstOrNull()?.nextContinuationData?.continuation
    )
