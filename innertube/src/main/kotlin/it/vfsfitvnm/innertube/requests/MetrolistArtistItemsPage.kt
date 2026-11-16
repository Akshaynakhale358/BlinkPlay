package it.vfsfitvnm.innertube.requests

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.BrowseResponse
import it.vfsfitvnm.innertube.models.ContinuationResponse
import it.vfsfitvnm.innertube.models.GridRenderer
import it.vfsfitvnm.innertube.models.MusicCarouselShelfRenderer
import it.vfsfitvnm.innertube.models.MusicResponsiveListItemRenderer
import it.vfsfitvnm.innertube.models.MusicShelfRenderer
import it.vfsfitvnm.innertube.models.MusicTwoRowItemRenderer
import it.vfsfitvnm.innertube.models.SectionListRenderer
import it.vfsfitvnm.innertube.models.bodies.BrowseBody
import it.vfsfitvnm.innertube.models.bodies.ContinuationBody
import it.vfsfitvnm.innertube.utils.runCatchingNonCancellable

/**
 * Artist-specific continuation parser adapted from MetrolistGroup/Metrolist's
 * BrowseContinuationPager and ContinuationPager (GPL-3.0). It guards repeated tokens,
 * merges continuation action payloads, deduplicates items, and keeps unknown audio types.
 */
suspend fun <T : Innertube.Item> Innertube.metrolistArtistItemsPage(
    body: BrowseBody,
    fromMusicResponsiveListItemRenderer: (MusicResponsiveListItemRenderer) -> T? = { null },
    fromMusicTwoRowItemRenderer: (MusicTwoRowItemRenderer) -> T? = { null }
): Result<Innertube.ItemsPage<T>?>? = runCatchingNonCancellable {
    val response = client.post(browse) {
        setBody(body)
        mask("*")
    }.body<BrowseResponse>()

    val items = mutableListOf<T>()
    val tokens = linkedSetOf<String>()
    fun append(item: T?) {
        if (item != null && (item !is Innertube.SongItem || isMetrolistArtistAudio(item))) items += item
    }
    fun shelf(shelf: MusicShelfRenderer?) {
        shelf?.contents.orEmpty().forEach { content ->
            append(content.musicResponsiveListItemRenderer?.let(fromMusicResponsiveListItemRenderer))
            content.continuationItemRenderer?.continuationEndpoint?.token()?.let(tokens::add)
        }
        shelf?.continuations.orEmpty().forEach { it.nextContinuationData?.continuation?.let(tokens::add) }
    }
    fun carousel(carousel: MusicCarouselShelfRenderer?) {
        carousel?.contents.orEmpty().forEach { content ->
            append(content.musicResponsiveListItemRenderer?.let(fromMusicResponsiveListItemRenderer))
            append(content.musicTwoRowItemRenderer?.let(fromMusicTwoRowItemRenderer))
        }
        carousel?.continuations.orEmpty().forEach { it.nextContinuationData?.continuation?.let(tokens::add) }
    }
    fun grid(grid: GridRenderer?) {
        grid?.items.orEmpty().forEach { item ->
            append(item.musicResponsiveListItemRenderer?.let(fromMusicResponsiveListItemRenderer))
            append(item.musicTwoRowItemRenderer?.let(fromMusicTwoRowItemRenderer))
            item.continuationItemRenderer?.continuationEndpoint?.token()?.let(tokens::add)
        }
        grid?.continuations.orEmpty().forEach { it.nextContinuationData?.continuation?.let(tokens::add) }
    }

    val sectionLists = buildList {
        response.contents?.twoColumnBrowseResultsRenderer?.secondaryContents?.sectionListRenderer?.let(::add)
        response.contents?.twoColumnBrowseResultsRenderer?.tabs.orEmpty()
            .mapNotNull { it.tabRenderer?.content?.sectionListRenderer }.forEach(::add)
        response.contents?.singleColumnBrowseResultsRenderer?.tabs.orEmpty()
            .mapNotNull { it.tabRenderer?.content?.sectionListRenderer }.forEach(::add)
        response.contents?.sectionListRenderer?.let(::add)
    }
    sectionLists.forEach { sectionList ->
        sectionList.contents.orEmpty().forEach { content ->
            shelf(content.musicPlaylistShelfRenderer ?: content.musicShelfRenderer)
            carousel(content.musicCarouselShelfRenderer)
            grid(content.gridRenderer)
            content.continuationItemRenderer?.continuationEndpoint?.token()?.let(tokens::add)
        }
        sectionList.continuations.orEmpty().forEach { it.nextContinuationData?.continuation?.let(tokens::add) }
    }

    val page: Innertube.ItemsPage<T>? = Innertube.ItemsPage(
        items = items.distinctBy(Innertube.Item::key),
        continuation = tokens.firstOrNull()
    )
    page
}

suspend fun <T : Innertube.Item> Innertube.metrolistArtistItemsPage(
    body: ContinuationBody,
    fromMusicResponsiveListItemRenderer: (MusicResponsiveListItemRenderer) -> T? = { null },
    fromMusicTwoRowItemRenderer: (MusicTwoRowItemRenderer) -> T? = { null }
): Result<Innertube.ItemsPage<T>?>? = runCatchingNonCancellable {
    val response = client.post(browse) {
        setBody(body)
        mask("*")
    }.body<ContinuationResponse>()

    val items = mutableListOf<T>()
    val tokens = linkedSetOf<String>()
    fun append(item: T?) {
        if (item != null && (item !is Innertube.SongItem || isMetrolistArtistAudio(item))) items += item
    }
    fun shelf(shelf: MusicShelfRenderer?) {
        shelf?.contents.orEmpty().forEach { content ->
            append(content.musicResponsiveListItemRenderer?.let(fromMusicResponsiveListItemRenderer))
            content.continuationItemRenderer?.continuationEndpoint?.token()?.let(tokens::add)
        }
        shelf?.continuations.orEmpty().forEach { it.nextContinuationData?.continuation?.let(tokens::add) }
    }
    fun carousel(carousel: MusicCarouselShelfRenderer?) {
        carousel?.contents.orEmpty().forEach { content ->
            append(content.musicResponsiveListItemRenderer?.let(fromMusicResponsiveListItemRenderer))
            append(content.musicTwoRowItemRenderer?.let(fromMusicTwoRowItemRenderer))
        }
        carousel?.continuations.orEmpty().forEach { it.nextContinuationData?.continuation?.let(tokens::add) }
    }
    fun grid(grid: GridRenderer?) {
        grid?.items.orEmpty().forEach { item ->
            append(item.musicResponsiveListItemRenderer?.let(fromMusicResponsiveListItemRenderer))
            append(item.musicTwoRowItemRenderer?.let(fromMusicTwoRowItemRenderer))
            item.continuationItemRenderer?.continuationEndpoint?.token()?.let(tokens::add)
        }
        grid?.continuations.orEmpty().forEach { it.nextContinuationData?.continuation?.let(tokens::add) }
    }

    val contents = response.continuationContents
    shelf(contents?.musicPlaylistShelfContinuation ?: contents?.musicShelfContinuation)
    carousel(contents?.musicCarouselShelfContinuation)
    grid(contents?.gridContinuation)

    val actions = response.onResponseReceivedActions.orEmpty() +
        response.onResponseReceivedEndpoints.orEmpty() +
        response.onResponseReceivedCommands.orEmpty()
    actions.forEach { action ->
        val continuationItems = action.appendContinuationItemsAction?.continuationItems
            ?: action.reloadContinuationItemsCommand?.continuationItems
            ?: action.appendContinuationItemsCommand?.continuationItems
        continuationItems.orEmpty().forEach { item ->
            append(item.musicResponsiveListItemRenderer?.let(fromMusicResponsiveListItemRenderer))
            append(item.musicTwoRowItemRenderer?.let(fromMusicTwoRowItemRenderer))
            item.continuationItemRenderer?.continuationEndpoint?.token()?.let(tokens::add)
        }
    }

    val page: Innertube.ItemsPage<T>? = Innertube.ItemsPage(
        items = items.distinctBy(Innertube.Item::key),
        continuation = tokens.firstOrNull()
    )
    page
}
