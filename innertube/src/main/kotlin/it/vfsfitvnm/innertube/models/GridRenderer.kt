package it.vfsfitvnm.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class GridRenderer(
    val items: List<Item>? = null,
    val continuations: List<Continuation>? = null,
) {
    @Serializable
    data class Item(
        val musicTwoRowItemRenderer: MusicTwoRowItemRenderer? = null,
        val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer? = null,
    )
}
