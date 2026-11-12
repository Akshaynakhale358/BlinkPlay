package it.vfsfitvnm.vimusic.ui.components.themed

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
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
    circle: Boolean = false,
) {
    val (colorPalette) = LocalAppearance.current

    Box(
        modifier = modifier
            .clip(if (circle) CircleShape else RoundedCornerShape(16.dp))
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
    val binder = LocalPlayerServiceBinder.current
    val player = binder?.player
    val playerSheetState = LocalPlayerBottomSheetState.current

    // The rail button is only an entry point. Once the player is expanded,
    // hide this button so a duplicate play control is not shown over the player.
    if (player?.currentMediaItem == null || playerSheetState.isExpanded) return

    PrimaryButton(
        iconId = R.drawable.play,
        enabled = true,
        transparentBackground = true,
        circle = circle,
        onClick = { playerSheetState.expandSoft() },
        modifier = modifier
    )
}
