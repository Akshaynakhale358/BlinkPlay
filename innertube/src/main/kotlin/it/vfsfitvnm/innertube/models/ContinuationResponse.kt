package it.vfsfitvnm.innertube.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ContinuationResponse(
    val continuationContents: ContinuationContents? = null,
    val onResponseReceivedActions: List<ResponseAction>? = null,
    val onResponseReceivedEndpoints: List<ResponseAction>? = null,
    val onResponseReceivedCommands: List<ResponseAction>? = null,
) {
    @Serializable
    data class ResponseAction(
        val appendContinuationItemsAction: AppendContinuationItemsAction? = null,
        val reloadContinuationItemsCommand: AppendContinuationItemsAction? = null,
        val appendContinuationItemsCommand: AppendContinuationItemsAction? = null,
    )

    @Serializable
    data class AppendContinuationItemsAction(
        val continuationItems: List<ContinuationItem>? = null,
    )

    @Serializable
    data class ContinuationItem(
        val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer? = null,
        val musicTwoRowItemRenderer: MusicTwoRowItemRenderer? = null,
        val continuationItemRenderer: ContinuationItemRenderer? = null,
    )

    @Serializable
    data class ContinuationContents(
        val musicPlaylistShelfContinuation: MusicShelfRenderer? = null,
        val musicShelfContinuation: MusicShelfRenderer? = null,
        val musicCarouselShelfContinuation: MusicCarouselShelfRenderer? = null,
        val gridContinuation: GridRenderer? = null,
        val playlistPanelContinuation: NextResponse.MusicQueueRenderer.Content.PlaylistPanelRenderer? = null,
    )
}
