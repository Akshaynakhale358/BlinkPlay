package it.vfsfitvnm.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class MusicCarouselShelfRenderer(
    val header: Header? = null,
    val contents: List<Content>? = null,
) {
    @Serializable
    data class Content(
        val musicTwoRowItemRenderer: MusicTwoRowItemRenderer? = null,
        val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer? = null,
    )

    @Serializable
    data class Header(
        val musicTwoRowItemRenderer: MusicTwoRowItemRenderer? = null,
        val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer? = null,
        val musicCarouselShelfBasicHeaderRenderer: MusicCarouselShelfBasicHeaderRenderer? = null
    ) {
        @Serializable
        data class MusicCarouselShelfBasicHeaderRenderer(
            val moreContentButton: MoreContentButton? = null,
            val title: Runs? = null,
            val strapline: Runs? = null,
        ) {
            @Serializable
            data class MoreContentButton(
                val buttonRenderer: ButtonRenderer? = null
            )
        }
    }
}
