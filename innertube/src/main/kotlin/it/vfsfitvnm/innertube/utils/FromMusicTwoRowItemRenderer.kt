package it.vfsfitvnm.innertube.utils

import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.MusicTwoRowItemRenderer

fun Innertube.AlbumItem.Companion.from(renderer: MusicTwoRowItemRenderer): Innertube.AlbumItem? {
    return Innertube.AlbumItem(
        info = renderer
            .title
            ?.runs
            ?.firstOrNull()
            ?.let(Innertube::Info),
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
    return Innertube.ArtistItem(
        info = renderer
            .title
            ?.runs
            ?.firstOrNull()
            ?.let(Innertube::Info),
        subscribersCountText = renderer
            .subtitle
            ?.runs
            ?.firstOrNull()
            ?.text,
        thumbnail = renderer
            .thumbnailRenderer
            ?.musicThumbnailRenderer
            ?.thumbnail
            ?.thumbnails
            ?.firstOrNull()
    ).takeIf { it.info?.endpoint?.browseId != null }
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

// Adapted from haturatu/ViMusic (GPL-3.0): artist video cards expose the Watch
// endpoint on the renderer, not necessarily on the title run.
fun Innertube.VideoItem.Companion.from(renderer: MusicTwoRowItemRenderer): Innertube.VideoItem? =
    runCatching {
        val endpoint = renderer.navigationEndpoint?.watchEndpoint
            ?: renderer.title
                ?.runs
                ?.firstOrNull()
                ?.navigationEndpoint
                ?.watchEndpoint

        Innertube.VideoItem(
            info = endpoint?.let { watchEndpoint ->
                Innertube.Info(
                    name = renderer.title?.text
                        ?: renderer.title?.runs?.firstOrNull()?.text,
                    endpoint = watchEndpoint
                )
            },
            authors = null,
            viewsText = renderer.subtitle?.runs?.lastOrNull()?.text,
            durationText = null,
            thumbnail = renderer
                .thumbnailRenderer
                ?.musicThumbnailRenderer
                ?.thumbnail
                ?.thumbnails
                ?.firstOrNull()
        ).takeIf { it.info?.endpoint?.videoId != null }
    }.getOrNull()
