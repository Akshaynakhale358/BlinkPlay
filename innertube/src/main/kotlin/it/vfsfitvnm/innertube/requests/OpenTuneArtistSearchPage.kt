package it.vfsfitvnm.innertube.requests

import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.MusicShelfRenderer
import it.vfsfitvnm.innertube.models.MusicResponsiveListItemRenderer
import it.vfsfitvnm.innertube.models.bodies.ContinuationBody
import it.vfsfitvnm.innertube.models.bodies.SearchBody
import it.vfsfitvnm.innertube.utils.from

/**
 * Artist search adapter following OpenTune's Innertube parser:
 * classify responsive rows by browseEndpoint pageType == MUSIC_PAGE_TYPE_ARTIST,
 * rather than relying only on two-row carousel cards.
 */
private fun Innertube.ArtistItem.Companion.fromOpenTune(
    renderer: MusicResponsiveListItemRenderer
): Innertube.ArtistItem? {
    val endpoint = renderer.navigationEndpoint?.browseEndpoint ?: return null
    if (endpoint.type?.contains("ARTIST") != true) return null
    val name = renderer.flexColumns
        .getOrNull(0)
        ?.musicResponsiveListItemFlexColumnRenderer
        ?.text
        ?.runs
        ?.joinToString("") { it.text.orEmpty() }
        ?.takeIf { it.isNotBlank() }
        ?: return null
    val subscribers = renderer.flexColumns
        .getOrNull(1)
        ?.musicResponsiveListItemFlexColumnRenderer
        ?.text
        ?.runs
        ?.joinToString("") { it.text.orEmpty() }
    val thumbnail = renderer.thumbnail
        ?.musicThumbnailRenderer
        ?.thumbnail
        ?.thumbnails
        ?.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }

    return Innertube.ArtistItem(
        info = Innertube.Info(name = name, endpoint = endpoint),
        subscribersCountText = subscribers,
        thumbnail = thumbnail
    ).takeIf { it.info?.endpoint?.browseId != null }
}

private fun openTuneArtistRow(content: MusicShelfRenderer.Content): Innertube.ArtistItem? =
    content.musicResponsiveListItemRenderer?.let(Innertube.ArtistItem.Companion::fromOpenTune)

suspend fun Innertube.openTuneArtistSearchPage(body: SearchBody) =
    searchPage(
        body = body,
        fromMusicShelfRendererContent = ::openTuneArtistRow,
        fromMusicTwoRowItemRenderer = Innertube.ArtistItem::from
    )

suspend fun Innertube.openTuneArtistSearchPage(body: ContinuationBody) =
    searchPage(
        body = body,
        fromMusicShelfRendererContent = ::openTuneArtistRow,
        fromMusicTwoRowItemRenderer = Innertube.ArtistItem::from
    )
