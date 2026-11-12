package it.vfsfitvnm.vimusic.ui.components.themed

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import it.vfsfitvnm.vimusic.LocalPlayerBottomSheetState
import it.vfsfitvnm.vimusic.LocalPlayerServiceBinder
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
) {
    val (colorPalette) = LocalAppearance.current

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
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
) {
    val binder = LocalPlayerServiceBinder.current
    val player = binder?.player
    val playerSheetState = LocalPlayerBottomSheetState.current
    var isPlaying by remember(player) {
        mutableStateOf(player?.isPlaying == true)
    }

    DisposableEffect(player) {
        if (player == null) return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                isPlaying = isPlayingNow
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                isPlaying = playWhenReady
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isPlaying = player.isPlaying
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    PrimaryButton(
        iconId = if (isPlaying) R.drawable.pause else R.drawable.play,
        enabled = player?.currentMediaItem != null,
        transparentBackground = true,
        onClick = {
            player?.let { currentPlayer ->
                if (currentPlayer.isPlaying || currentPlayer.playWhenReady) {
                    currentPlayer.pause()
                } else {
                    if (currentPlayer.playbackState == Player.STATE_ENDED) {
                        currentPlayer.seekToDefaultPosition()
                    }
                    if (currentPlayer.playbackState == Player.STATE_IDLE) {
                        currentPlayer.prepare()
                    }
                    currentPlayer.play()
                }
            }
        },
        modifier = modifier
    )
}
