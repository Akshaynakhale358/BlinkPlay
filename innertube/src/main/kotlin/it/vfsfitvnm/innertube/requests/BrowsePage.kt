package it.vfsfitvnm.innertube.requests

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.BrowseResponse
import it.vfsfitvnm.innertube.models.MusicCarouselShelfRenderer
import it.vfsfitvnm.innertube.models.MusicResponsiveListItemRenderer
import it.vfsfitvnm.innertube.models.MusicTwoRowItemRenderer
import it.vfsfitvnm.innertube.models.SectionListRenderer
import it.vfsfitvnm.innertube.models.bodies.BrowseBody
import it.vfsfitvnm.innertube.utils.from
import it.vfsfitvnm.innertube.utils.runCatchingNonCancellable

/**
 * A server-discovered shelf. browseId/params come from YouTube's own "more" endpoint;
 * they are not a locally maintained list of category names.
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

/** Fetches and parses every section exposed by a YouTube Music browse feed. */
suspend fun Innertube.browsePage(body: BrowseBody) = runCatchingNonCancellable {
    val response = client.post(browse) { setBody(body) }.body<BrowseResponse>()
    val sectionLists = buildList {
        response.contents?.sectionListRenderer?.let(::add)
        response.contents?.singleColumnBrowseResultsRenderer?.tabs.orEmpty()
            .mapNotNull { it.tabRenderer?.content?.sectionListRenderer }
            .forEach(::add)
        response.contents?.twoColumnBrowseResultsRenderer?.let { twoColumn ->
            twoColumn.tabs.orEmpty()
                .mapNotNull { it.tabRenderer?.content?.sectionListRenderer }
                .forEach(::add)
            twoColumn.secondaryContents?.sectionListRenderer?.let(::add)
        }
    }.distinctBy { it.contents?.hashCode() }

    val sections = sectionLists
        .flatMap { it.contents.orEmpty() }
        .mapNotNull(::toBrowseSection)
        .distinctBy { listOf(it.title, it.browseId, it.params) }

    MusicBrowsePage(
        title = response.header?.musicDetailHeaderRenderer?.title?.text
            ?: response.header?.musicImmersiveHeaderRenderer?.title?.text,
        sections = sections,
    )
}

/** Home feed. YouTube decides which shelves/categories are currently relevant. */
suspend fun Innertube.musicHomePage() =
    browsePage(BrowseBody(browseId = MUSIC_HOME_BROWSE_ID))

/** Explore feed. The returned categories and their order are server-controlled. */
suspend fun Innertube.musicExplorePage() =
    browsePage(BrowseBody(browseId = MUSIC_EXPLORE_BROWSE_ID))

private fun toBrowseSection(content: SectionListRenderer.Content): MusicBrowseSection? {
    val carousel = content.musicCarouselShelfRenderer
    val shelf = content.musicShelfRenderer
    val grid = content.gridRenderer

    val title = carousel?.header?.musicCarouselShelfBasicHeaderRenderer?.title?.text
        ?: carousel?.header?.musicTwoRowItemRenderer?.title?.text
        ?: shelf?.title?.text
        ?: return null

    val moreEndpoint = carousel?.header
        ?.musicCarouselShelfBasicHeaderRenderer
        ?.moreContentButton
        ?.buttonRenderer
        ?.navigationEndpoint
        ?.browseEndpoint

    val carouselItems = carousel?.contents.orEmpty().mapNotNull { item ->
        item.musicResponsiveListItemRenderer?.let(Innertube.SongItem::from)
            ?: item.musicTwoRowItemRenderer?.let(::toMusicItem)
    }
    val shelfItems = shelf?.contents.orEmpty()
        .mapNotNull(MusicShelfRendererItem::from)
    val gridItems = grid?.items.orEmpty()
        .mapNotNull { it.musicTwoRowItemRenderer?.let(::toMusicItem) }

    return MusicBrowseSection(
        title = title,
        browseId = moreEndpoint?.browseId,
        params = moreEndpoint?.params,
        items = (carouselItems + shelfItems + gridItems).distinctBy(Innertube.Item::key),
        continuation = shelf?.continuations?.firstOrNull()?.nextContinuationData?.continuation,
    )
}

private object MusicShelfRendererItem {
    fun from(item: it.vfsfitvnm.innertube.models.MusicShelfRenderer.Content): Innertube.Item? =
        item.musicResponsiveListItemRenderer?.let(Innertube.SongItem::from)
}

private fun toMusicItem(renderer: MusicTwoRowItemRenderer): Innertube.Item? =
    Innertube.PlaylistItem.from(renderer)
        ?: Innertube.AlbumItem.from(renderer)
        ?: Innertube.ArtistItem.from(renderer)

private const val MUSIC_HOME_BROWSE_ID = "FEmusic_home"
private const val MUSIC_EXPLORE_BROWSE_ID = "FEmusic_explore"
