package it.vfsfitvnm.vimusic.ui.screens.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.Context
import it.vfsfitvnm.innertube.models.NavigationEndpoint
import it.vfsfitvnm.innertube.models.bodies.BrowseBody
import it.vfsfitvnm.innertube.requests.MusicBrowsePage
import it.vfsfitvnm.innertube.requests.browsePage
import it.vfsfitvnm.vimusic.LocalPlayerAwareWindowInsets
import it.vfsfitvnm.vimusic.LocalPlayerServiceBinder
import it.vfsfitvnm.vimusic.ui.components.LocalMenuState
import it.vfsfitvnm.vimusic.ui.components.ShimmerHost
import it.vfsfitvnm.vimusic.ui.components.themed.Header
import it.vfsfitvnm.vimusic.ui.components.themed.NonQueuedMediaItemMenu
import it.vfsfitvnm.vimusic.ui.components.themed.TextPlaceholder
import it.vfsfitvnm.vimusic.ui.items.PlaylistItemPlaceholder
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.ui.styling.Dimensions
import it.vfsfitvnm.vimusic.ui.styling.px
import it.vfsfitvnm.vimusic.utils.asMediaItem
import it.vfsfitvnm.vimusic.utils.forcePlay
import it.vfsfitvnm.vimusic.utils.secondary
import it.vfsfitvnm.vimusic.utils.semiBold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun BrowseCategoryScreen(
    browseId: String,
    params: String?,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
) {
    val (colorPalette, typography) = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current
    var page by remember(browseId, params) { mutableStateOf<MusicBrowsePage?>(null) }
    var failed by remember(browseId, params) { mutableStateOf(false) }
    var retryKey by remember(browseId, params) { mutableStateOf(0) }
    var loading by remember(browseId, params) { mutableStateOf(false) }

    LaunchedEffect(browseId, params, retryKey) {
        loading = true
        val context = Context.DefaultWeb.copy(
            client = Context.DefaultWeb.client.copy(hl = "en", gl = "IN")
        )
        val result = withContext(Dispatchers.IO) {
            Innertube.browsePage(
                BrowseBody(context = context, browseId = browseId, params = params)
            )
        }
        page = result?.getOrNull() ?: page
        failed = page?.sections.isNullOrEmpty()
        loading = false
    }

    Column(
        modifier = Modifier
            .background(colorPalette.background0)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(LocalPlayerAwareWindowInsets.current)
    ) {
        Header(title = page?.title ?: "BROWSE")
        page?.let { browsePage ->
            MusicBrowseShelves(
                sections = browsePage.sections,
                sectionTextModifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 24.dp, bottom = 8.dp),
                onSongClick = { song ->
                    val mediaItem = song.asMediaItem
                    binder?.stopRadio()
                    binder?.player?.forcePlay(mediaItem)
                    binder?.setupRadio(NavigationEndpoint.Endpoint.Watch(videoId = mediaItem.mediaId))
                },
                onSongLongClick = { song ->
                    menuState.display {
                        NonQueuedMediaItemMenu(
                            onDismiss = menuState::hide,
                            mediaItem = song.asMediaItem
                        )
                    }
                },
                onAlbumClick = onAlbumClick,
                onArtistClick = onArtistClick,
                onPlaylistClick = onPlaylistClick,
                onCategoryClick = { id, nextParams ->
                    onPlaylistClick(browseCategoryRoute(id, nextParams))
                }
            )
        } ?: if (loading) {
            ShimmerHost {
                repeat(3) { TextPlaceholder(modifier = Modifier.padding(16.dp)) }
                androidx.compose.foundation.layout.Row {
                    repeat(3) {
                        PlaylistItemPlaceholder(
                            thumbnailSizeDp = 108.dp,
                            alternative = true
                        )
                    }
                }
            }
        } else if (failed) {
            BasicText(
                text = "Couldn't load this YouTube Music category. Tap to retry.",
                style = typography.s.secondary,
                modifier = Modifier
                    .clickable { retryKey += 1 }
                    .padding(16.dp)
            )
        }
    }
}
