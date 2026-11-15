package it.vfsfitvnm.innertube.requests

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.ContinuationResponse
import it.vfsfitvnm.innertube.models.GridRenderer
import it.vfsfitvnm.innertube.models.MusicCarouselShelfRenderer
import it.vfsfitvnm.innertube.models.MusicShelfRenderer
import it.vfsfitvnm.innertube.models.MusicTwoRowItemRenderer
import it.vfsfitvnm.innertube.models.SearchResponse
import it.vfsfitvnm.innertube.models.SectionListRenderer
import it.vfsfitvnm.innertube.models.bodies.ContinuationBody
import it.vfsfitvnm.innertube.models.bodies.SearchBody
import it.vfsfitvnm.innertube.utils.runCatchingNonCancellable

/**
 * Dedicated Artist search + continuation parser adapted from Metrolist's artist loading path.
 * It does not depend on BlinkPlay's generic SearchPage parser; every renderer is passed through
 * MetrolistArtistParser so only actual artist entities enter the Artist list.
 */
suspend fun Innertube.metrolistArtistSearchPage(
    body: SearchBody
): Result<Innertube.ItemsPage<Innertube.ArtistItem>>? = runCatchingNonCancellable {
    val response = client.post(search) {
        setBody(body)
        mask("*")
    }.body<SearchResponse>()

    val items = mutableListOf<Innertube.ArtistItem>()
    val tokens = linkedSetOf<String>()

    fun addShelf(shelf: MusicShelfRenderer?) {
        shelf?.contents.orEmpty().forEach { content ->
            MetrolistArtistParser.fromShelf(content)?.let(items::add)
            content.continuationItemRenderer?.continuationEndpoint?.token()?.let(tokens::add)
        }
        shelf?.continuations.orEmpty().forEach { it.nextContinuationData?.continuation?.let(tokens::add) }
    }

    fun addCarousel(carousel: MusicCarouselShelfRenderer?) {
        carousel?.contents.orEmpty().forEach { content ->
            content.musicTwoRowItemRenderer?.let(MetrolistArtistParser::fromTwoRow)?.let(items::add)
            content.musicResponsiveListItemRenderer?.let {
                MetrolistArtistParser.fromShelf(MusicShelfRenderer.Content(musicResponsiveListItemRenderer = it))
            }?.let(items::add)
        }
        carousel?.continuations.orEmpty().forEach { it.nextContinuationData?.continuation?.let(tokens::add) }
    }

    fun addGrid(grid: GridRenderer?) {
        grid?.items.orEmpty().forEach { item ->
            item.musicTwoRowItemRenderer?.let(MetrolistArtistParser::fromTwoRow)?.let(items::add)
            item.musicResponsiveListItemRenderer?.let {
                MetrolistArtistParser.fromShelf(MusicShelfRenderer.Content(musicResponsiveListItemRenderer = it))
            }?.let(items::add)
            item.continuationItemRenderer?.continuationEndpoint?.token()?.let(tokens::add)
        }
        grid?.continuations.orEmpty().forEach { it.nextContinuationData?.continuation?.let(tokens::add) }
    }

    val sectionLists = response.contents?.tabbedSearchResultsRenderer?.tabs.orEmpty()
        .mapNotNull { it.tabRenderer?.content?.sectionListRenderer }
    sectionLists.forEach { sectionList ->
        sectionList.contents.orEmpty().forEach { content ->
            addShelf(content.musicShelfRenderer)
            addShelf(content.musicPlaylistShelfRenderer)
            addCarousel(content.musicCarouselShelfRenderer)
            addGrid(content.gridRenderer)
            content.continuationItemRenderer?.continuationEndpoint?.token()?.let(tokens::add)
        }
        sectionList.continuations.orEmpty().forEach { it.nextContinuationData?.continuation?.let(tokens::add) }
    }

    Innertube.ItemsPage(
        items = items.distinctBy(Innertube.Item::key),
        continuation = tokens.singleOrNull()
    )
}

suspend fun Innertube.metrolistArtistSearchPage(
    body: ContinuationBody
): Result<Innertube.ItemsPage<Innertube.ArtistItem>>? = runCatchingNonCancellable {
    val response = client.post(search) {
        setBody(body)
        mask("*")
    }.body<ContinuationResponse>()

    val items = mutableListOf<Innertube.ArtistItem>()
    val tokens = linkedSetOf<String>()

    fun addShelf(shelf: MusicShelfRenderer?) {
        shelf?.contents.orEmpty().forEach { content ->
            MetrolistArtistParser.fromShelf(content)?.let(items::add)
            content.continuationItemRenderer?.continuationEndpoint?.token()?.let(tokens::add)
        }
        shelf?.continuations.orEmpty().forEach { it.nextContinuationData?.continuation?.let(tokens::add) }
    }

    fun addCarousel(carousel: MusicCarouselShelfRenderer?) {
        carousel?.contents.orEmpty().forEach { content ->
            content.musicTwoRowItemRenderer?.let(MetrolistArtistParser::fromTwoRow)?.let(items::add)
            content.musicResponsiveListItemRenderer?.let {
                MetrolistArtistParser.fromShelf(MusicShelfRenderer.Content(musicResponsiveListItemRenderer = it))
            }?.let(items::add)
        }
        carousel?.continuations.orEmpty().forEach { it.nextContinuationData?.continuation?.let(tokens::add) }
    }

    fun addGrid(grid: GridRenderer?) {
        grid?.items.orEmpty().forEach { item ->
            item.musicTwoRowItemRenderer?.let(MetrolistArtistParser::fromTwoRow)?.let(items::add)
            item.musicResponsiveListItemRenderer?.let {
                MetrolistArtistParser.fromShelf(MusicShelfRenderer.Content(musicResponsiveListItemRenderer = it))
            }?.let(items::add)
            item.continuationItemRenderer?.continuationEndpoint?.token()?.let(tokens::add)
        }
        grid?.continuations.orEmpty().forEach { it.nextContinuationData?.continuation?.let(tokens::add) }
    }

    val contents = response.continuationContents
    addShelf(contents?.musicPlaylistShelfContinuation)
    addShelf(contents?.musicShelfContinuation)
    addCarousel(contents?.musicCarouselShelfContinuation)
    addGrid(contents?.gridContinuation)

    val actions = response.onResponseReceivedActions.orEmpty() +
        response.onResponseReceivedEndpoints.orEmpty() +
        response.onResponseReceivedCommands.orEmpty()
    actions.forEach { action ->
        val continuationItems = action.appendContinuationItemsAction?.continuationItems
            ?: action.reloadContinuationItemsCommand?.continuationItems
            ?: action.appendContinuationItemsCommand?.continuationItems
        continuationItems.orEmpty().forEach { item ->
            item.musicTwoRowItemRenderer?.let(MetrolistArtistParser::fromTwoRow)?.let(items::add)
            item.musicResponsiveListItemRenderer?.let {
                MetrolistArtistParser.fromShelf(MusicShelfRenderer.Content(musicResponsiveListItemRenderer = it))
            }?.let(items::add)
            item.continuationItemRenderer?.continuationEndpoint?.token()?.let(tokens::add)
        }
    }

    Innertube.ItemsPage(
        items = items.distinctBy(Innertube.Item::key),
        continuation = tokens.singleOrNull()
    )
}
