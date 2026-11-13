package it.vfsfitvnm.innertube.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
data class BrowseResponse(
    val contents: Contents? = null,
    val header: Header? = null,
    val microformat: Microformat? = null
) {
    @Serializable
    data class Contents(
        val singleColumnBrowseResultsRenderer: Tabs? = null,
        val twoColumnBrowseResultsRenderer: TwoColumnBrowseResults? = null,
        val sectionListRenderer: SectionListRenderer? = null,
    )

    @Serializable
    data class Header @OptIn(ExperimentalSerializationApi::class) constructor(
        @JsonNames("musicVisualHeaderRenderer")
        val musicImmersiveHeaderRenderer: MusicImmersiveHeaderRenderer? = null,
        val musicDetailHeaderRenderer: MusicDetailHeaderRenderer? = null,
    ) {
        @Serializable
        data class MusicDetailHeaderRenderer(
            val title: Runs? = null,
            val subtitle: Runs? = null,
            val secondSubtitle: Runs? = null,
            val thumbnail: ThumbnailRenderer? = null,
        )

        @Serializable
        data class MusicImmersiveHeaderRenderer(
            val description: Runs? = null,
            val playButton: PlayButton? = null,
            val startRadioButton: StartRadioButton? = null,
            val thumbnail: ThumbnailRenderer? = null,
            val foregroundThumbnail: ThumbnailRenderer? = null,
            val title: Runs? = null,
            val subscriptionButton: SubscriptionButton? = null
        ) {
            @Serializable
            data class SubscriptionButton(
                val subscribeButtonRenderer: SubscribeButtonRenderer? = null
            ) {
                @Serializable
                data class SubscribeButtonRenderer(
                    val subscriberCountText: Runs? = null,
                    val subscribed: Boolean? = null
                )
            }

            @Serializable
            data class PlayButton(
                val buttonRenderer: ButtonRenderer? = null
            )

            @Serializable
            data class StartRadioButton(
                val buttonRenderer: ButtonRenderer? = null
            )
        }
    }

    @Serializable
    data class Microformat(
        val microformatDataRenderer: MicroformatDataRenderer? = null
    ) {
        @Serializable
        data class MicroformatDataRenderer(
            val urlCanonical: String? = null
        )
    }
}
