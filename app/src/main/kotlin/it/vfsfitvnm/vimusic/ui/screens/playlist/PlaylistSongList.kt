package it.vfsfitvnm.vimusic.ui.screens.playlist

import android.content.Intent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.valentinilk.shimmer.shimmer
import it.vfsfitvnm.compose.persist.persist
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.Context
import it.vfsfitvnm.innertube.models.bodies.BrowseBody
import it.vfsfitvnm.innertube.models.bodies.ContinuationBody
import it.vfsfitvnm.innertube.requests.playlistPage
import it.vfsfitvnm.vimusic.Database
import it.vfsfitvnm.vimusic.LocalPlayerAwareWindowInsets
import it.vfsfitvnm.vimusic.LocalPlayerServiceBinder
import it.vfsfitvnm.vimusic.R
import it.vfsfitvnm.vimusic.models.Playlist
import it.vfsfitvnm.vimusic.models.SongPlaylistMap
import it.vfsfitvnm.vimusic.query
import it.vfsfitvnm.vimusic.transaction
import it.vfsfitvnm.vimusic.ui.components.LocalMenuState
import it.vfsfitvnm.vimusic.ui.components.ShimmerHost
import it.vfsfitvnm.vimusic.ui.components.themed.FloatingActionsContainerWithScrollToTop
import it.vfsfitvnm.vimusic.ui.components.themed.Header
import it.vfsfitvnm.vimusic.ui.components.themed.HeaderIconButton
import it.vfsfitvnm.vimusic.ui.components.themed.HeaderPlaceholder
import it.vfsfitvnm.vimusic.ui.components.themed.LayoutWithAdaptiveThumbnail
import it.vfsfitvnm.vimusic.ui.components.themed.NonQueuedMediaItemMenu
import it.vfsfitvnm.vimusic.ui.components.themed.SecondaryTextButton
import it.vfsfitvnm.vimusic.ui.components.themed.TextFieldDialog
import it.vfsfitvnm.vimusic.ui.components.themed.adaptiveThumbnailContent
import it.vfsfitvnm.vimusic.ui.items.SongItem
import it.vfsfitvnm.vimusic.ui.items.SongItemPlaceholder
import it.vfsfitvnm.vimusic.ui.styling.Dimensions
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.ui.styling.px
import it.vfsfitvnm.vimusic.utils.asMediaItem
import it.vfsfitvnm.vimusic.utils.enqueue
import it.vfsfitvnm.vimusic.utils.forcePlayAtIndex
import it.vfsfitvnm.vimusic.utils.forcePlayFromBeginning
import it.vfsfitvnm.vimusic.utils.isLandscape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
fun PlaylistSongList(
    browseId: String,
) {
    val (colorPalette) = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current
    val context = LocalContext.current
    val menuState = LocalMenuState.current

    var playlistPage by persist<Innertube.PlaylistOrAlbumPage?>("playlist/$browseId/playlistPage")
    var playlistLoadError by rememberSaveable(browseId) { mutableStateOf(false) }
    var isLoadingPlaylist by rememberSaveable(browseId) { mutableStateOf(false) }
    var retryKey by rememberSaveable(browseId) { mutableStateOf(0) }

    LaunchedEffect(browseId, retryKey) {
        if (playlistPage?.songsPage == null) {
            isLoadingPlaylist = true
            val result = withContext(Dispatchers.IO) {
                Innertube.playlistPage(BrowseBody(browseId = browseId))
            }
            val page = result?.getOrNull()
            if (page?.songsPage != null) {
                playlistPage = page
                playlistLoadError = false
            } else {
                playlistLoadError = true
            }
            isLoadingPlaylist = false
        }
    }

    val songThumbnailSizeDp = Dimensions.thumbnails.song
    val songThumbnailSizePx = songThumbnailSizeDp.px

    var isImportingPlaylist by rememberSaveable {
        mutableStateOf(false)
    }

    if (isImportingPlaylist) {
        TextFieldDialog(
            hintText = "Enter the playlist name",
            initialTextInput = playlistPage?.title ?: "",
            onDismiss = { isImportingPlaylist = false },
            onDone = { text ->
                query {
                    transaction {
                        val playlistId = Database.insert(Playlist(name = text, browseId = browseId))

                        playlistPage?.songsPage?.items
                            ?.map(Innertube.SongItem::asMediaItem)
                            ?.onEach(Database::insert)
                            ?.mapIndexed { index, mediaItem ->
                                SongPlaylistMap(
                                    songId = mediaItem.mediaId,
                                    playlistId = playlistId,
                                    position = index
                                )
                            }?.let(Database::insertSongPlaylistMaps)
                    }
                }
            }
        )
    }

    val headerContent: @Composable () -> Unit = {
        if (playlistPage == null) {
            HeaderPlaceholder(
                modifier = Modifier
                    .shimmer()
            )
        } else {
            Header(title = playlistPage?.title ?: "Unknown") {
                SecondaryTextButton(
                    text = "Enqueue",
                    enabled = playlistPage?.songsPage?.items?.isNotEmpty() == true,
                    onClick = {
                        playlistPage?.songsPage?.items?.map(Innertube.SongItem::asMediaItem)?.let { mediaItems ->
                            binder?.player?.enqueue(mediaItems)
                        }
                    }
                )

                Spacer(
                    modifier = Modifier
                        .weight(1f)
                )

                HeaderIconButton(
                    icon = R.drawable.add,
                    color = colorPalette.text,
                    onClick = { isImportingPlaylist = true }
                )

                HeaderIconButton(
                    icon = R.drawable.share_social,
                    color = colorPalette.text,
                    onClick = {
                        (playlistPage?.url ?: "https://music.youtube.com/playlist?list=${browseId.removePrefix("VL")}").let { url ->
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, url)
                            }

                            context.startActivity(Intent.createChooser(sendIntent, null))
                        }
                    }
                )
            }
        }
    }

    val thumbnailContent = adaptiveThumbnailContent(playlistPage == null, playlistPage?.thumbnail?.url)

    val lazyListState = rememberLazyListState()

    LaunchedEffect(browseId) {
        snapshotFlow {
            Triple(
                lazyListState.layoutInfo.visibleItemsInfo.any { it.key == "playlist-loading" },
                playlistPage?.songsPage?.continuation,
                retryKey
            )
        }.collect { (isFooterVisible, continuation, _) ->
            if (!isFooterVisible || continuation == null || playlistLoadError || isLoadingPlaylist) {
                return@collect
            }

            isLoadingPlaylist = true
            val nextPage = withContext(Dispatchers.IO) {
                Innertube.playlistPage(
                    ContinuationBody(
                        context = Context.DefaultWeb,
                        continuation = continuation
                    )
                )?.getOrNull()
            }

            if (nextPage != null) {
                val currentPage = playlistPage
                val currentSongsPage = currentPage?.songsPage
                val nextContinuation = nextPage.continuation
                    ?.takeUnless { it == continuation }
                playlistPage = currentPage?.copy(
                    songsPage = Innertube.ItemsPage(
                        items = (currentSongsPage?.items.orEmpty() + nextPage.items.orEmpty())
                            .distinctBy(Innertube.SongItem::key),
                        continuation = nextContinuation
                    )
                )
                playlistLoadError = false
            } else {
                playlistLoadError = true
            }
            isLoadingPlaylist = false
        }
    }

    LayoutWithAdaptiveThumbnail(thumbnailContent = thumbnailContent) {
        Box {
            LazyColumn(
                state = lazyListState,
                contentPadding = LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Vertical + WindowInsetsSides.End).asPaddingValues(),
                modifier = Modifier
                    .background(colorPalette.background0)
                    .fillMaxSize()
            ) {
                item(
                    key = "header",
                    contentType = 0
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        headerContent()
                        if (!isLandscape) thumbnailContent()
                    }
                }

                itemsIndexed(items = playlistPage?.songsPage?.items ?: emptyList()) { index, song ->
                    SongItem(
                        song = song,
                        thumbnailSizePx = songThumbnailSizePx,
                        thumbnailSizeDp = songThumbnailSizeDp,
                        modifier = Modifier
                            .combinedClickable(
                                onLongClick = {
                                    menuState.display {
                                        NonQueuedMediaItemMenu(
                                            onDismiss = menuState::hide,
                                            mediaItem = song.asMediaItem,
                                        )
                                    }
                                },
                                onClick = {
                                    playlistPage?.songsPage?.items?.map(Innertube.SongItem::asMediaItem)?.let { mediaItems ->
                                        binder?.stopRadio()
                                        binder?.player?.forcePlayAtIndex(mediaItems, index)
                                    }
                                }
                            )
                    )
                }

                if (playlistPage == null) {
                    item(key = "playlist-loading") {
                        if (playlistLoadError) {
                            BasicText(
                                text = "Playlist could not load. Tap to retry.",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { playlistLoadError = false; retryKey += 1 }
                                    .padding(24.dp)
                            )
                        } else {
                            ShimmerHost(
                                modifier = Modifier.fillParentMaxSize()
                            ) {
                                repeat(4) {
                                    SongItemPlaceholder(thumbnailSizeDp = songThumbnailSizeDp)
                                }
                            }
                        }
                    }
                } else if (playlistPage?.songsPage?.items.isNullOrEmpty() &&
                    playlistPage?.songsPage?.continuation == null
                ) {
                    item(key = "empty-playlist") {
                        BasicText(
                            text = "No songs found in this playlist.",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        )
                    }
                }

                if (playlistPage?.songsPage?.continuation != null) {
                    item(key = "playlist-loading") {
                        if (playlistLoadError) {
                            BasicText(
                                text = "More songs could not load. Tap to retry.",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { playlistLoadError = false; retryKey += 1 }
                                    .padding(24.dp)
                            )
                        } else {
                            ShimmerHost {
                                repeat(2) {
                                    SongItemPlaceholder(thumbnailSizeDp = songThumbnailSizeDp)
                                }
                            }
                        }
                    }
                }
            }

            FloatingActionsContainerWithScrollToTop(
                lazyListState = lazyListState,
                iconId = R.drawable.shuffle,
                onClick = {
                    playlistPage?.songsPage?.items?.let { songs ->
                        if (songs.isNotEmpty()) {
                            binder?.stopRadio()
                            binder?.player?.forcePlayFromBeginning(
                                songs.shuffled().map(Innertube.SongItem::asMediaItem)
                            )
                        }
                    }
                }
            )
        }
    }
}
