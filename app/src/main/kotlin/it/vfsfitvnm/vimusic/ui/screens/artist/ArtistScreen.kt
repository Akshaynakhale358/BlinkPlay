package it.vfsfitvnm.vimusic.ui.screens.artist

import android.content.Intent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.BasicText
import com.valentinilk.shimmer.shimmer
import it.vfsfitvnm.compose.persist.PersistMapCleanup
import it.vfsfitvnm.compose.persist.persist
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.MusicResponsiveListItemRenderer
import it.vfsfitvnm.innertube.models.MusicTwoRowItemRenderer
import it.vfsfitvnm.innertube.models.bodies.BrowseBody
import it.vfsfitvnm.innertube.models.bodies.ContinuationBody
import it.vfsfitvnm.innertube.requests.metrolistArtistPage
import it.vfsfitvnm.innertube.requests.metrolistArtistItemsPage
import it.vfsfitvnm.innertube.utils.from
import it.vfsfitvnm.compose.routing.RouteHandler
import it.vfsfitvnm.vimusic.Database
import it.vfsfitvnm.vimusic.LocalPlayerServiceBinder
import it.vfsfitvnm.vimusic.R
import it.vfsfitvnm.vimusic.models.Artist
import it.vfsfitvnm.vimusic.query
import it.vfsfitvnm.vimusic.ui.components.LocalMenuState
import it.vfsfitvnm.vimusic.ui.components.themed.Header
import it.vfsfitvnm.vimusic.ui.components.themed.HeaderIconButton
import it.vfsfitvnm.vimusic.ui.components.themed.SecondaryTextButton
import it.vfsfitvnm.vimusic.ui.components.themed.HeaderPlaceholder
import it.vfsfitvnm.vimusic.ui.components.themed.NonQueuedMediaItemMenu
import it.vfsfitvnm.vimusic.ui.components.themed.Scaffold
import it.vfsfitvnm.vimusic.ui.components.themed.adaptiveThumbnailContent
import it.vfsfitvnm.vimusic.ui.items.AlbumItem
import it.vfsfitvnm.vimusic.ui.items.ArtistItem
import it.vfsfitvnm.vimusic.ui.items.PlaylistItem
import it.vfsfitvnm.vimusic.ui.items.AlbumItemPlaceholder
import it.vfsfitvnm.vimusic.ui.items.SongItem
import it.vfsfitvnm.vimusic.ui.items.SongItemPlaceholder
import it.vfsfitvnm.vimusic.ui.screens.albumRoute
import it.vfsfitvnm.vimusic.ui.screens.artistRoute
import it.vfsfitvnm.vimusic.ui.screens.playlistRoute
import it.vfsfitvnm.vimusic.ui.screens.globalRoutes
import it.vfsfitvnm.vimusic.ui.screens.searchresult.ItemsPage
import it.vfsfitvnm.vimusic.ui.styling.Dimensions
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.ui.styling.px
import it.vfsfitvnm.vimusic.utils.artistScreenTabIndexKey
import it.vfsfitvnm.vimusic.utils.asMediaItem
import it.vfsfitvnm.vimusic.utils.semiBold
import it.vfsfitvnm.vimusic.utils.forcePlay
import it.vfsfitvnm.vimusic.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
fun ArtistScreen(browseId: String) {
    val saveableStateHolder = rememberSaveableStateHolder()

    var tabIndex by rememberPreference(artistScreenTabIndexKey, defaultValue = 0)

    PersistMapCleanup(tagPrefix = "artist/$browseId/")

    var artist by persist<Artist?>("artist/$browseId/artist")

    var artistPage by persist<Innertube.ArtistPage?>("artist/$browseId/artistPage")

    LaunchedEffect(browseId) {
        Database
            .artist(browseId)
            .distinctUntilChanged()
            .collect { currentArtist ->
                artist = currentArtist
            }
    }

    // Keep any persisted page visible, but always refresh its data when the artist is opened.
    LaunchedEffect(browseId) {
        // Do not present a persisted response as if it came from the current Metrolist request.
        artistPage = null
        withContext(Dispatchers.IO) {
            Innertube.metrolistArtistPage(BrowseBody(browseId = browseId))
                ?.onSuccess { currentArtistPage ->
                    artistPage = currentArtistPage

                    Database.upsert(
                        Artist(
                            id = browseId,
                            name = currentArtistPage.name,
                            thumbnailUrl = currentArtistPage.thumbnail?.url,
                            timestamp = System.currentTimeMillis(),
                            bookmarkedAt = artist?.bookmarkedAt
                        )
                    )
                }
        }
    }

    RouteHandler(listenToGlobalEmitter = true) {
        globalRoutes()

        host {
            val thumbnailContent =
                adaptiveThumbnailContent(
                    artist?.timestamp == null,
                    artist?.thumbnailUrl,
                    CircleShape
                )

            val headerContent: @Composable (textButton: (@Composable () -> Unit)?) -> Unit =
                { textButton ->
                    if (artist?.timestamp == null) {
                        HeaderPlaceholder(
                            modifier = Modifier
                                .shimmer()
                        )
                    } else {
                        val (colorPalette) = LocalAppearance.current
                        val context = LocalContext.current

                        Header(title = artist?.name ?: "Unknown") {
                            textButton?.invoke()

                            Spacer(
                                modifier = Modifier
                                    .weight(1f)
                            )



                            HeaderIconButton(
                                icon = R.drawable.share_social,
                                color = colorPalette.text,
                                onClick = {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        type = "text/plain"
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "https://music.youtube.com/channel/$browseId"
                                        )
                                    }

                                    context.startActivity(Intent.createChooser(sendIntent, null))
                                }
                            )
                        }
                    }
                }

            // OpenTune-style artist navigation: use the shelves YouTube Music
            // actually returns for this artist instead of a fixed Metrolist category list.
            val artistSections = artistPage?.sections.orEmpty()
                .filter { section ->
                    !section.title.contains("video", ignoreCase = true) &&
                        (section.items.isNotEmpty() || section.continuation != null || section.browseEndpoint != null)
                }
                .distinctBy { it.title.trim().lowercase() }

            Scaffold(
                topIconButtonId = R.drawable.chevron_back,
                onTopIconButtonClick = pop,
                tabIndex = tabIndex.coerceIn(0, (artistSections.size - 1).coerceAtLeast(0)),
                onTabChanged = { tabIndex = it },
                tabColumnContent = { Item ->
                    artistSections.forEachIndexed { index, section ->
                        val icon = when {
                            section.title.contains("song", ignoreCase = true) ||
                                section.title.contains("track", ignoreCase = true) -> R.drawable.musical_notes
                            section.title.contains("album", ignoreCase = true) ||
                                section.title.contains("single", ignoreCase = true) ||
                                section.title.contains("release", ignoreCase = true) ||
                                section.title.contains("ep", ignoreCase = true) -> R.drawable.disc
                            section.title.contains("artist", ignoreCase = true) ||
                                section.title.contains("fans", ignoreCase = true) -> R.drawable.person
                            else -> R.drawable.library
                        }
                        Item(index, section.title, icon)
                    }
                },
            ) { currentTabIndex ->
                val section = artistSections.getOrNull(currentTabIndex)
                if (section != null) {
                    saveableStateHolder.SaveableStateProvider(key = section.title) {
                        ArtistShelfPage(
                            title = section.title,
                            section = section,
                            headerContent = headerContent,
                            onAlbumClick = { albumRoute(it) },
                            onArtistClick = { artistRoute(it) },
                            onPlaylistClick = { playlistRoute(it) }
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            headerContent(null)
                            BasicText(
                                text = "Artist categories are not available.",
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}


private fun Innertube.ArtistPage.findArtistSection(vararg aliases: String): Innertube.ArtistSection? =
    sections.firstOrNull { section ->
        aliases.any { alias -> section.title.contains(alias, ignoreCase = true) }
    }

@Composable
private fun ArtistShelfPage(
    title: String,
    section: Innertube.ArtistSection?,
    headerContent: @Composable (textButton: (@Composable () -> Unit)?) -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
) {
    val (_, typography) = LocalAppearance.current
    val songSize = Dimensions.thumbnails.song
    val songSizePx = songSize.px
    val entitySize = 108.dp
    val entitySizePx = entitySize.px
    val binder = LocalPlayerServiceBinder.current
    val listState = rememberLazyListState()
    val shelfItems = remember(section) {
        mutableStateListOf<Innertube.Item>().apply { addAll(section?.items.orEmpty()) }
    }
    var continuation by remember(section) { mutableStateOf(section?.continuation) }
    var useBrowseEndpoint by remember(section) {
        mutableStateOf(section?.continuation == null && section?.browseEndpoint?.browseId != null)
    }
    var isLoading by remember(section) { mutableStateOf(false) }
    var loadFailed by remember(section) { mutableStateOf(false) }
    var retryKey by remember(section) { mutableStateOf(0) }

    // Every artist shelf keeps its first batch and appends subsequent batches as the user scrolls.
    LaunchedEffect(section, retryKey) {
        snapshotFlow {
            Triple(
                listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1,
                shelfItems.size,
                continuation to useBrowseEndpoint
            )
        }.distinctUntilChanged().collect { (lastVisibleIndex, loadedCount, pagingState) ->
            val loadThreshold = (loadedCount - 4).coerceAtLeast(0) + 1
            val (token, browseFallback) = pagingState
            if ((token != null || browseFallback) && !isLoading && lastVisibleIndex >= loadThreshold) {
                isLoading = true
                loadFailed = false
                val result = withContext(Dispatchers.IO) {
                    val fromMusicResponsive = { renderer: MusicResponsiveListItemRenderer ->
                        Innertube.SongItem.from(renderer)
                    }
                    val fromMusicTwoRow = { renderer: MusicTwoRowItemRenderer ->
                        Innertube.AlbumItem.from(renderer)
                            ?: Innertube.ArtistItem.from(renderer)
                            ?: Innertube.PlaylistItem.from(renderer)
                    }
                    if (token != null) {
                        Innertube.metrolistArtistItemsPage<Innertube.Item>(
                            body = ContinuationBody(continuation = token),
                            fromMusicResponsiveListItemRenderer = fromMusicResponsive,
                            fromMusicTwoRowItemRenderer = fromMusicTwoRow
                        )
                    } else {
                        section?.browseEndpoint?.let { endpoint ->
                            endpoint.browseId?.let { browseId ->
                                Innertube.metrolistArtistItemsPage<Innertube.Item>(
                                    body = BrowseBody(browseId = browseId, params = endpoint.params),
                                    fromMusicResponsiveListItemRenderer = fromMusicResponsive,
                                    fromMusicTwoRowItemRenderer = fromMusicTwoRow
                                )
                            }
                        }
                    }
                }
                if (result == null) {
                    // Keep the current token / browse fallback so a failed request can be retried.
                    loadFailed = true
                } else {
                    result.onSuccess { page ->
                        if (page == null) {
                            continuation = null
                            useBrowseEndpoint = false
                        } else {
                            val knownKeys = shelfItems.mapTo(mutableSetOf(), Innertube.Item::key)
                            page.items.orEmpty().filter { knownKeys.add(it.key) }.forEach { shelfItems.add(it) }
                            continuation = page.continuation?.takeIf { it != token }
                            useBrowseEndpoint = false
                        }
                    }.onFailure {
                        loadFailed = true
                    }
                }
                isLoading = false
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        item(key = "artist-header") {
            headerContent(null)
        }
        item(key = "artist-shelf-title") {
            BasicText(
                text = title,
                style = typography.m.semiBold.copy(color = Color.White),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
            )
        }

        if (shelfItems.isEmpty() && continuation == null) {
            item(key = "artist-shelf-empty") {
                BasicText(
                    text = "No $title available for this artist.",
                    style = typography.s.copy(color = Color.White),
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            items(
                items = shelfItems,
                key = Innertube.Item::key
            ) { item ->
                when (item) {
                    is Innertube.SongItem -> SongItem(
                        song = item,
                        thumbnailSizeDp = songSize,
                        thumbnailSizePx = songSizePx,
                        titleColor = Color.White,
                        modifier = Modifier.clickable {
                            binder?.stopRadio()
                            binder?.player?.forcePlay(item.asMediaItem)
                            binder?.setupRadio(item.info?.endpoint)
                        }
                    )
                    is Innertube.AlbumItem -> AlbumItem(
                        album = item,
                        thumbnailSizePx = entitySizePx,
                        thumbnailSizeDp = entitySize,
                        alternative = true,
                        titleColor = Color.White,
                        modifier = Modifier.clickable { onAlbumClick(item.key) }
                    )
                    is Innertube.ArtistItem -> ArtistItem(
                        artist = item,
                        thumbnailSizePx = entitySizePx,
                        thumbnailSizeDp = entitySize,
                        alternative = true,
                        modifier = Modifier.clickable { onArtistClick(item.key) }
                    )
                    is Innertube.PlaylistItem -> PlaylistItem(
                        playlist = item,
                        thumbnailSizePx = entitySizePx,
                        thumbnailSizeDp = entitySize,
                        alternative = true,
                        modifier = Modifier.clickable { onPlaylistClick(item.key) }
                    )
                    else -> Unit
                }
            }
            if (isLoading) {
                item(key = "artist-shelf-loading") {
                    BasicText(
                        text = "Loading more...",
                        style = typography.s.copy(color = Color.White),
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else if (loadFailed && (continuation != null || section?.browseEndpoint?.browseId != null)) {
                item(key = "artist-shelf-retry") {
                    BasicText(
                        text = "Couldn't load more. Tap to retry.",
                        style = typography.s.copy(color = Color.White),
                        modifier = Modifier
                            .clickable {
                                if (continuation == null) {
                                    useBrowseEndpoint = section?.browseEndpoint?.browseId != null
                                }
                                retryKey++
                            }
                            .padding(16.dp)
                    )
                }
            }
        }
    }
}
