package it.vfsfitvnm.vimusic.ui.components.themed

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.media3.common.Player as Media3Player
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.Context
import it.vfsfitvnm.innertube.models.NavigationEndpoint
import it.vfsfitvnm.innertube.models.bodies.SearchBody
import it.vfsfitvnm.innertube.requests.searchPage
import it.vfsfitvnm.innertube.utils.from
import it.vfsfitvnm.vimusic.Database
import it.vfsfitvnm.vimusic.LocalPlayerServiceBinder
import it.vfsfitvnm.vimusic.utils.asMediaItem
import it.vfsfitvnm.vimusic.utils.forcePlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import it.vfsfitvnm.vimusic.LocalPlayerBottomSheetState
import it.vfsfitvnm.vimusic.R
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.ui.styling.primaryButton

@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    @DrawableRes iconId: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    transparentBackground: Boolean = false,
    circle: Boolean = false,
) {
    val (colorPalette) = LocalAppearance.current

    Box(
        modifier = modifier
            .clip(if (circle) CircleShape else RoundedCornerShape(16.dp))
            .then(if (circle && iconId == R.drawable.play) Modifier.border(1.dp, colorPalette.text.copy(alpha = 0.7f), CircleShape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .then(if (transparentBackground) Modifier else Modifier.background(colorPalette.primaryButton))
            .size(62.dp)
    ) {
        Image(
            painter = painterResource(iconId),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colorPalette.text),
            modifier = Modifier
                .align(Alignment.Center)
                .size(20.dp)
        )
    }
}

@Composable
fun MiniPlayerActionButton(
    modifier: Modifier = Modifier,
    circle: Boolean = false,
) {
    val playerSheetState = LocalPlayerBottomSheetState.current
    val binder = LocalPlayerServiceBinder.current
    val coroutineScope = rememberCoroutineScope()

    // Show the launcher only while the player sheet is dismissed.
    if (playerSheetState?.isDismissed != true) return

    PrimaryButton(
        iconId = R.drawable.play,
        enabled = true,
        transparentBackground = true,
        circle = circle,
        onClick = {
            val player = binder?.player
            if (player == null) {
                playerSheetState.expandSoft()
                return@PrimaryButton
            }

            // Prefer the active item, then recover the current item from the queue.
            // A paused player can briefly expose a null currentMediaItem while its timeline
            // still contains the paused song; do not start a random song in that case.
            val queuedItem = if (player.mediaItemCount > 0) {
                val index = player.currentMediaItemIndex
                    .takeIf { it in 0 until player.mediaItemCount } ?: 0
                player.getMediaItemAt(index)
            } else {
                null
            }
            val currentItem = player.currentMediaItem ?: queuedItem

            if (currentItem != null) {
                if (!player.isPlaying) {
                    if (player.currentMediaItem == null && player.mediaItemCount > 0) {
                        val index = player.currentMediaItemIndex
                            .takeIf { it in 0 until player.mediaItemCount } ?: 0
                        player.seekToDefaultPosition(index)
                    }
                    if (player.playbackState == Media3Player.STATE_IDLE ||
                        player.playbackState == Media3Player.STATE_ENDED
                    ) player.prepare()
                    player.play()
                }
                playerSheetState.expandSoft()
            } else {
                // Only when the queue is truly empty, choose a random song.
                coroutineScope.launch {
                    val mediaItem = withContext(Dispatchers.IO) {
                        Database.trending(20).first().randomOrNull()?.asMediaItem
                    } ?: withContext(Dispatchers.IO) {
                        val indiaContext = Context.DefaultWeb.copy(
                            client = Context.DefaultWeb.client.copy(hl = "hi", gl = "IN")
                        )
                        Innertube.searchPage(
                            body = SearchBody(
                                context = indiaContext,
                                query = "Hindi Bollywood songs",
                                params = Innertube.SearchFilter.Song.value
                            ),
                            fromMusicShelfRendererContent = Innertube.SongItem::from
                        )?.getOrNull()?.items?.randomOrNull()?.asMediaItem
                    }

                    if (mediaItem != null) {
                        binder?.stopRadio()
                        player.forcePlay(mediaItem)
                        binder?.setupRadio(
                            NavigationEndpoint.Endpoint.Watch(
                                videoId = mediaItem.mediaId
                            )
                        )
                        playerSheetState.expandSoft()
                    } else {
                        playerSheetState.expandSoft()
                    }
                }
            }
        },
        modifier = modifier
    )
}
