package it.vfsfitvnm.vimusic.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.vimusic.LocalPlayerAwareWindowInsets
import it.vfsfitvnm.vimusic.LocalPlayerServiceBinder
import it.vfsfitvnm.vimusic.ui.components.LocalMenuState
import it.vfsfitvnm.vimusic.ui.components.themed.Header
import it.vfsfitvnm.vimusic.ui.components.themed.NonQueuedMediaItemMenu
import it.vfsfitvnm.vimusic.ui.items.SongItem
import it.vfsfitvnm.vimusic.ui.items.SongItemPlaceholder
import it.vfsfitvnm.vimusic.ui.styling.Dimensions
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.ui.styling.px
import it.vfsfitvnm.vimusic.utils.asMediaItem
import it.vfsfitvnm.vimusic.utils.forcePlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PodcastDiscoveryPage(
    categories: List<Pair<String, String>>,
    onCategorySelect: (String) -> Unit
) {
    val (palette, _) = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current
    val thumbnailSizeDp = Dimensions.thumbnails.song
    val thumbnailSizePx = thumbnailSizeDp.px
    var refreshKey by remember { mutableStateOf(0) }
    var songs by remember { mutableStateOf<List<Innertube.SongItem>?>(null) }
    var loading by remember { mutableStateOf(true) }

    // This page deliberately reuses the exact same two-root Podcast feed as Home.
    // No secondary categories or extra keyword searches are introduced.
    LaunchedEffect(refreshKey) {
        loading = true
        songs = withContext(Dispatchers.IO) {
            runCatching { fetchQuickPicksPodcastSongs(refreshKey) }.getOrDefault(emptyList())
        }
        loading = false
    }

    PullToRefreshBox(
        isRefreshing = loading && songs != null,
        onRefresh = { refreshKey += 1 },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            contentPadding = LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Vertical + WindowInsetsSides.End).asPaddingValues(),
            modifier = Modifier
                .fillMaxSize()
                .background(palette.background0)
        ) {
            item(key = "podcast-header") {
                Header(title = "Podcast")
            }

            if (loading && songs == null) {
                items(8) {
                    SongItemPlaceholder(thumbnailSizeDp = thumbnailSizeDp)
                }
            } else if (songs.isNullOrEmpty()) {
                item(key = "podcast-empty") {
                    androidx.compose.foundation.text.BasicText(
                        text = "No podcast episodes found. Pull down to retry.",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                items(songs.orEmpty(), key = Innertube.SongItem::key) { song ->
                    SongItem(
                        song = song,
                        thumbnailSizePx = thumbnailSizePx,
                        thumbnailSizeDp = thumbnailSizeDp,
                        modifier = Modifier.combinedClickable(
                            onLongClick = {
                                menuState.display {
                                    NonQueuedMediaItemMenu(
                                        onDismiss = menuState::hide,
                                        mediaItem = song.asMediaItem
                                    )
                                }
                            },
                            onClick = {
                                val mediaItem = song.asMediaItem
                                binder?.stopRadio()
                                binder?.player?.forcePlay(mediaItem)
                                binder?.setupRadio(song.info?.endpoint)
                            }
                        )
                    )
                }
            }
        }
    }
}
