package it.vfsfitvnm.innertube.utils

import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.MusicResponsiveListItemRenderer
import it.vfsfitvnm.innertube.models.NavigationEndpoint
import it.vfsfitvnm.innertube.models.Runs

fun Innertube.SongItem.Companion.from(renderer: MusicResponsiveListItemRenderer): Innertube.SongItem? {
    return Innertube.SongItem(
        info = renderer
            .flexColumns
            .getOrNull(0)
            ?.musicResponsiveListItemFlexColumnRenderer
            ?.text
            ?.runs
            ?.getOrNull(0)
            ?.let(Innertube::Info),
        authors = renderer
            .flexColumns
            .getOrNull(1)
            ?.musicResponsiveListItemFlexColumnRenderer
            ?.text
            ?.runs
            ?.map<Runs.Run, Innertube.Info<NavigationEndpoint.Endpoint.Browse>>(Innertube::Info)
            ?.takeIf(List<Any>::isNotEmpty),
        durationText = renderer
            .fixedColumns
            ?.getOrNull(0)
            ?.musicResponsiveListItemFlexColumnRenderer
            ?.text
            ?.runs
            ?.getOrNull(0)
            ?.text,
        album = renderer
            .flexColumns
            .getOrNull(2)
            ?.musicResponsiveListItemFlexColumnRenderer
            ?.text
            ?.runs
            ?.firstOrNull()
            ?.let(Innertube::Info),
        thumbnail = renderer
            .thumbnail
            ?.musicThumbnailRenderer
            ?.thumbnail
            ?.thumbnails
            ?.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }
    ).takeIf { it.info?.endpoint?.videoId != null }
}

fun Innertube.ArtistItem.Companion.from(renderer: MusicResponsiveListItemRenderer): Innertube.ArtistItem? {
    val endpoint = renderer.navigationEndpoint?.browseEndpoint
        ?: renderer.flexColumns.firstOrNull()
            ?.musicResponsiveListItemFlexColumnRenderer
            ?.text?.runs
            ?.firstNotNullOfOrNull { it.navigationEndpoint?.browseEndpoint }
        ?: return null

    val browseId = endpoint.browseId ?: return null
    val pageType = endpoint.type
    val isArtist = pageType == "MUSIC_PAGE_TYPE_ARTIST" ||
        pageType == "MUSIC_PAGE_TYPE_LIBRARY_ARTIST" ||
        pageType == "MUSIC_PAGE_TYPE_USER_CHANNEL" ||
        browseId.startsWith("UC")
    if (!isArtist) return null

    val title = renderer.flexColumns.firstOrNull()
        ?.musicResponsiveListItemFlexColumnRenderer
        ?.text?.runs?.firstOrNull()?.text
        ?.takeIf { it.isNotBlank() } ?: return null

    val thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.thumbnail
        ?.thumbnails?.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }
        ?: return null

    val subscribers = renderer.flexColumns.getOrNull(1)
        ?.musicResponsiveListItemFlexColumnRenderer
        ?.text?.runs?.lastOrNull()?.text

    return Innertube.ArtistItem(
        info = Innertube.Info(name = title, endpoint = endpoint),
        subscribersCountText = subscribers,
        thumbnail = thumbnail
    )
}
