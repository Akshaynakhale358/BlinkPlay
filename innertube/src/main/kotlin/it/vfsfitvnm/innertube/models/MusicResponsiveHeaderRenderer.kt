package it.vfsfitvnm.innertube.models

import kotlinx.serialization.Serializable

/**
 * Header of an album or playlist page.
 *
 * Replaces `header.musicDetailHeaderRenderer`, which YouTube no longer sends — the cause of the
 * "album shows Unknown" / "albums not loading" reports. Note the artist is no longer part of
 * [subtitle] (that is now just "Album • 2013"); it moved to [straplineTextOne].
 */
@Serializable
data class MusicResponsiveHeaderRenderer(
    val title: Runs? = null,
    val subtitle: Runs? = null,
    val secondSubtitle: Runs? = null,
    val straplineTextOne: Runs? = null,
    val description: Runs? = null,
    val thumbnail: ThumbnailRenderer? = null,
)
