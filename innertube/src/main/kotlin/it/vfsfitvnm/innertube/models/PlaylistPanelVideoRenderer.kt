package it.vfsfitvnm.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class PlaylistPanelVideoRenderer(
    val title: Runs? = null,
    val longBylineText: Runs? = null,
    val shortBylineText: Runs? = null,
    val lengthText: Runs? = null,
    val navigationEndpoint: NavigationEndpoint? = null,
    val thumbnail: ThumbnailRenderer.MusicThumbnailRenderer.Thumbnail? = null,
)
