package it.vfsfitvnm.innertube.requests

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.BrowseResponse
import it.vfsfitvnm.innertube.models.ContinuationResponse
import it.vfsfitvnm.innertube.models.MusicCarouselShelfRenderer
import it.vfsfitvnm.innertube.models.MusicResponsiveHeaderRenderer
import it.vfsfitvnm.innertube.models.MusicShelfRenderer
import it.vfsfitvnm.innertube.models.NavigationEndpoint
import it.vfsfitvnm.innertube.models.SectionListRenderer
import it.vfsfitvnm.innertube.models.bodies.BrowseBody
import it.vfsfitvnm.innertube.models.bodies.ContinuationBody
import it.vfsfitvnm.innertube.utils.from
import it.vfsfitvnm.innertube.utils.runCatchingNonCancellable

suspend fun Innertube.playlistPage(body: BrowseBody) = runCatchingNonCancellable {
    val response = client.post(browse) {
        setBody(body)
        // Playlist browse responses have changed their nested shelf shape over time.
        // Request the full payload, just as the working Songs For You search does,
        // so the playlist and its continuation tokens are not cut off by a stale mask.
        mask("*")
    }.body<BrowseResponse>()

    val twoColumn = response.contents?.twoColumnBrowseResultsRenderer

    val header: MusicResponsiveHeaderRenderer? = twoColumn
        ?.tabs
        ?.firstOrNull()
        ?.tabRenderer
        ?.content
        ?.sectionListRenderer
        ?.contents
        ?.firstNotNullOfOrNull(SectionListRenderer.Content::musicResponsiveHeaderRenderer)

    // YouTube Music returns playlist songs in three layouts depending on the
    // playlist type/client: two-column secondary contents, single-column tabs,
    // or a root sectionListRenderer. Check all of them before declaring empty.
    val sectionListRendererContents = twoColumn
        ?.secondaryContents
        ?.sectionListRenderer
        ?.contents
        ?: response
            .contents
            ?.singleColumnBrowseResultsRenderer
            ?.tabs
            ?.firstOrNull()
            ?.tabRenderer
            ?.content
            ?.sectionListRenderer
            ?.contents
        ?: response
            .contents
            ?.sectionListRenderer
            ?.contents

    val musicShelfRenderer = sectionListRendererContents
        ?.firstNotNullOfOrNull(SectionListRenderer.Content::musicShelfRenderer)

    val musicCarouselShelfRenderer = sectionListRendererContents
        ?.firstNotNullOfOrNull(SectionListRenderer.Content::musicCarouselShelfRenderer)

    val legacyHeader = response.header?.musicDetailHeaderRenderer

    Innertube.PlaylistOrAlbumPage(
        title = header?.title?.text ?: legacyHeader?.title?.text,
        thumbnail = (header?.thumbnail ?: legacyHeader?.thumbnail)
            ?.musicThumbnailRenderer
            ?.thumbnail
            ?.thumbnails
            ?.firstOrNull(),
        authors = header
            ?.straplineTextOne
            ?.runs
            ?.filter { it.navigationEndpoint != null }
            ?.map { Innertube.Info<NavigationEndpoint.Endpoint.Browse>(it) }
            ?.takeIf { it.isNotEmpty() }
            ?: legacyHeader
                ?.subtitle
                ?.splitBySeparator()
                ?.getOrNull(1)
                ?.map { Innertube.Info<NavigationEndpoint.Endpoint.Browse>(it) },
        year = header
            ?.subtitle
            ?.splitBySeparator()
            ?.lastOrNull()
            ?.firstOrNull()
            ?.text
            ?: legacyHeader
                ?.subtitle
                ?.splitBySeparator()
                ?.getOrNull(2)
                ?.firstOrNull()
                ?.text,
        url = response
            .microformat
            ?.microformatDataRenderer
            ?.urlCanonical,
        songsPage = musicShelfRenderer?.toSongsPage(
            continuation = sectionListRendererContents
                .orEmpty()
                .mapNotNull { it.continuationItemRenderer?.continuationEndpoint?.token() }
                .firstOrNull()
                ?: musicShelfRenderer
                    ?.contents
                    ?.mapNotNull { it.continuationItemRenderer?.continuationEndpoint?.token() }
                    ?.firstOrNull()
        ),
        otherVersions = musicCarouselShelfRenderer
            ?.contents
            ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
            ?.mapNotNull(Innertube.AlbumItem::from)
    )
}

suspend fun Innertube.playlistPage(body: ContinuationBody) = runCatchingNonCancellable {
    val response = client.post(browse) {
        setBody(body)
        // Keep all continuation fields; the API can return either playlist-shelf or music-shelf data.
        mask("*")
    }.body<ContinuationResponse>()

    val shelf = response.continuationContents?.musicShelfContinuation
    val appendedItems = (response.onResponseReceivedActions.orEmpty() +
        response.onResponseReceivedEndpoints.orEmpty())
        .flatMap { action ->
            (action.appendContinuationItemsAction
                ?: action.reloadContinuationItemsCommand)
                ?.continuationItems
                .orEmpty()
        }

    val songs = shelf
        ?.contents
        ?.mapNotNull(MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
        ?.mapNotNull(Innertube.SongItem::from)
        .orEmpty() + appendedItems
        .mapNotNull { it.musicResponsiveListItemRenderer }
        .mapNotNull(Innertube.SongItem::from)

    val nextToken = appendedItems
        .mapNotNull { it.continuationItemRenderer?.continuationEndpoint?.token() }
        .lastOrNull()
        ?: shelf
            ?.continuations
            ?.firstOrNull()
            ?.nextContinuationData
            ?.continuation

    Innertube.ItemsPage(
        items = songs.distinctBy(Innertube.SongItem::key),
        continuation = nextToken
    )
}

private fun MusicShelfRenderer?.toSongsPage(continuation: String? = null) =
    Innertube.ItemsPage(
        items = this
            ?.contents
            ?.mapNotNull(MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
            ?.mapNotNull(Innertube.SongItem::from),
        continuation = continuation
            ?: this
                ?.continuations
                ?.firstOrNull()
                ?.nextContinuationData
                ?.continuation
    )
