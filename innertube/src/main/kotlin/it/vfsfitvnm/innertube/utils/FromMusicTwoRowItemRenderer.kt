package it.vfsfitvnm.innertube.utils

import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.MusicTwoRowItemRenderer

fun Innertube.AlbumItem.Companion.from(renderer: MusicTwoRowItemRenderer): Innertube.AlbumItem? {
    val titleRun = renderer.title?.runs?.firstOrNull()
    val browseEndpoint = renderer.navigationEndpoint?.browseEndpoint
        ?: titleRun?.navigationEndpoint?.browseEndpoint

    return Innertube.AlbumItem(
        info = Innertube.Info(
            name = titleRun?.text,
            endpoint = browseEndpoint
        ),
        authors = null,
        year = renderer
            .subtitle
            ?.runs
            ?.lastOrNull()
            ?.text,
        thumbnail = renderer
            .thumbnailRenderer
            ?.musicThumbnailRenderer
            ?.thumbnail
            ?.thumbnails
            ?.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }
    ).takeIf { it.info?.endpoint?.browseId != null }
}

fun Innertube.ArtistItem.Companion.from(renderer: MusicTwoRowItemRenderer): Innertube.ArtistItem? {
    val titleRun = renderer.title?.runs?.firstOrNull()
    val browseEndpoint = renderer.navigationEndpoint?.browseEndpoint
        ?: titleRun?.navigationEndpoint?.browseEndpoint
    val browseId = browseEndpoint?.browseId ?: return null
    val pageType = browseEndpoint.type
    val isArtist = pageType == "MUSIC_PAGE_TYPE_ARTIST" ||
        pageType == "MUSIC_PAGE_TYPE_LIBRARY_ARTIST" ||
        pageType == "MUSIC_PAGE_TYPE_USER_CHANNEL" ||
        browseId.startsWith("UC")
    if (!isArtist) return null

    val title = titleRun?.text?.takeIf { it.isNotBlank() } ?: return null
    val thumbnail = renderer.thumbnailRenderer
        ?.musicThumbnailRenderer
        ?.thumbnail
        ?.thumbnails
        ?.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }
        ?: return null

    return Innertube.ArtistItem(
        info = Innertube.Info(name = title, endpoint = browseEndpoint),
        subscribersCountText = renderer.subtitle?.runs?.firstOrNull()?.text,
        thumbnail = thumbnail
    )
}

fun Innertube.PlaylistItem.Companion.from(renderer: MusicTwoRowItemRenderer): Innertube.PlaylistItem? {
    // Playlist grid cards expose their browse endpoint on the renderer itself.
    // The title run often has text only; using that run alone discarded valid playlists.
    val titleRun = renderer.title?.runs?.firstOrNull()
    val browseEndpoint = renderer.navigationEndpoint?.browseEndpoint
        ?: titleRun?.navigationEndpoint?.browseEndpoint

    return Innertube.PlaylistItem(
        info = Innertube.Info(
            name = titleRun?.text,
            endpoint = browseEndpoint
        ),
        channel = renderer
            .subtitle
            ?.runs
            ?.getOrNull(2)
            ?.let(Innertube::Info),
        songCount = renderer
            .subtitle
            ?.runs
            ?.getOrNull(4)
            ?.text
            ?.split(' ')
            ?.firstOrNull()
            ?.toIntOrNull(),
        thumbnail = renderer
            .thumbnailRenderer
            ?.musicThumbnailRenderer
            ?.thumbnail
            ?.thumbnails
            ?.firstOrNull()
    ).takeIf { it.info?.endpoint?.browseId != null }
}
