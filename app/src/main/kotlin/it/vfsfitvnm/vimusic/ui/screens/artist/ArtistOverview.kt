package it.vfsfitvnm.vimusic.ui.screens.artist

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.NavigationEndpoint
import it.vfsfitvnm.innertube.models.MusicResponsiveListItemRenderer
import it.vfsfitvnm.innertube.models.MusicTwoRowItemRenderer
import it.vfsfitvnm.innertube.models.bodies.BrowseBody
import it.vfsfitvnm.innertube.models.bodies.ContinuationBody
import it.vfsfitvnm.innertube.requests.itemsPage
import it.vfsfitvnm.innertube.utils.from
import it.vfsfitvnm.vimusic.LocalPlayerAwareWindowInsets
import it.vfsfitvnm.vimusic.LocalPlayerServiceBinder
import it.vfsfitvnm.vimusic.R
import it.vfsfitvnm.vimusic.ui.components.LocalMenuState
import it.vfsfitvnm.vimusic.ui.components.ShimmerHost
import it.vfsfitvnm.vimusic.ui.components.themed.FloatingActionsContainerWithScrollToTop
import it.vfsfitvnm.vimusic.ui.components.themed.LayoutWithAdaptiveThumbnail
import it.vfsfitvnm.vimusic.ui.components.themed.NonQueuedMediaItemMenu
import it.vfsfitvnm.vimusic.ui.components.themed.SecondaryTextButton
import it.vfsfitvnm.vimusic.ui.components.themed.TextPlaceholder
import it.vfsfitvnm.vimusic.ui.items.AlbumItem
import it.vfsfitvnm.vimusic.ui.items.PlaylistItem
import it.vfsfitvnm.vimusic.ui.items.ArtistItem
import it.vfsfitvnm.vimusic.ui.items.AlbumItemPlaceholder
import it.vfsfitvnm.vimusic.ui.items.SongItem
import it.vfsfitvnm.vimusic.ui.items.SongItemPlaceholder
import it.vfsfitvnm.vimusic.ui.styling.Dimensions
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.ui.styling.px
import it.vfsfitvnm.vimusic.utils.align
import it.vfsfitvnm.vimusic.utils.asMediaItem
import it.vfsfitvnm.vimusic.utils.color
import it.vfsfitvnm.vimusic.utils.forcePlay
import it.vfsfitvnm.vimusic.utils.secondary
import it.vfsfitvnm.vimusic.utils.semiBold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
fun ArtistOverview(
    youtubeArtistPage: Innertube.ArtistPage?,
    onViewAllSongsClick: () -> Unit,
    onViewAllAlbumsClick: () -> Unit,
    onViewAllSinglesClick: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    isSubscribed: Boolean,
    onToggleSubscribed: () -> Unit,
    thumbnailContent: @Composable () -> Unit,
    headerContent: @Composable (textButton: (@Composable () -> Unit)?) -> Unit,
) {
    val (colorPalette, typography) = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current
    val windowInsets = LocalPlayerAwareWindowInsets.current

    val songThumbnailSizeDp = Dimensions.thumbnails.song
    val songThumbnailSizePx = songThumbnailSizeDp.px
    val albumThumbnailSizeDp = 108.dp
    val albumThumbnailSizePx = albumThumbnailSizeDp.px

    val endPaddingValues = windowInsets.only(WindowInsetsSides.End).asPaddingValues()

    val sectionTextModifier = Modifier
        .padding(horizontal = 16.dp)
        .padding(top = 24.dp, bottom = 8.dp)

    val scrollState = rememberScrollState()
    val artistSections = youtubeArtistPage?.sections.orEmpty()
    val sectionItems = remember(youtubeArtistPage) {
        mutableStateMapOf<String, List<Innertube.Item>>().apply {
            artistSections.forEach { put(it.title, it.items) }
        }
    }
    val sectionContinuations = remember(youtubeArtistPage) {
        mutableStateMapOf<String, String?>().apply {
            artistSections.forEach { put(it.title, it.continuation) }
        }
    }
    val sectionBrowsePending = remember(youtubeArtistPage) {
        mutableStateMapOf<String, Boolean>().apply {
            artistSections.forEach { put(it.title, it.continuation == null && it.browseEndpoint?.browseId != null) }
        }
    }
    var isLoadingMore by remember(youtubeArtistPage) { mutableStateOf(false) }
    var loadMoreFailed by remember(youtubeArtistPage) { mutableStateOf(false) }
    var retryKey by remember(youtubeArtistPage) { mutableStateOf(0) }

    fun sectionItemsFor(vararg aliases: String): List<Innertube.Item> {
        val section = artistSections.firstOrNull { shelf ->
            aliases.any { shelf.title.contains(it, ignoreCase = true) }
        } ?: return emptyList()
        return sectionItems[section.title].orEmpty()
    }

    suspend fun loadMoreArtistSections() {
        if (isLoadingMore) return
        val targets = artistSections.filter { section ->
            sectionContinuations[section.title] != null || sectionBrowsePending[section.title] == true
        }
        if (targets.isEmpty()) return
        isLoadingMore = true
        loadMoreFailed = false
        for (section in targets) {
            val token = sectionContinuations[section.title]
            val endpoint = section.browseEndpoint
            val response = withContext(Dispatchers.IO) {
                val fromResponsive = { renderer: MusicResponsiveListItemRenderer ->
                    Innertube.SongItem.from(renderer)
                }
                val fromTwoRow = { renderer: MusicTwoRowItemRenderer ->
                    Innertube.AlbumItem.from(renderer)
                        ?: Innertube.ArtistItem.from(renderer)
                        ?: Innertube.PlaylistItem.from(renderer)
                }
                if (token != null) {
                    Innertube.itemsPage<Innertube.Item>(
                        body = ContinuationBody(continuation = token),
                        fromMusicResponsiveListItemRenderer = fromResponsive,
                        fromMusicTwoRowItemRenderer = fromTwoRow
                    )
                } else {
                    endpoint?.browseId?.let { browseId ->
                        Innertube.itemsPage<Innertube.Item>(
                            body = BrowseBody(browseId = browseId, params = endpoint.params),
                            fromMusicResponsiveListItemRenderer = fromResponsive,
                            fromMusicTwoRowItemRenderer = fromTwoRow
                        )
                    }
                }
            }
            if (response == null) {
                loadMoreFailed = true
                continue
            }
            response.onSuccess { page ->
                if (page == null) {
                    sectionContinuations[section.title] = null
                    sectionBrowsePending[section.title] = false
                } else {
                    sectionItems[section.title] = (sectionItems[section.title].orEmpty() + page.items.orEmpty())
                        .distinctBy(Innertube.Item::key)
                    sectionContinuations[section.title] = page.continuation?.takeIf { it != token }
                    sectionBrowsePending[section.title] = false
                }
            }.onFailure {
                loadMoreFailed = true
            }
        }
        isLoadingMore = false
    }

    LaunchedEffect(youtubeArtistPage, retryKey) {
        snapshotFlow {
            Triple(scrollState.value, scrollState.maxValue, sectionItems.values.sumOf { it.size })
        }.distinctUntilChanged().collect { (value, maxValue, _) ->
            if (!loadMoreFailed && (maxValue == 0 || value >= maxValue - 500)) {
                loadMoreArtistSections()
            }
        }
    }

    val overviewSongs = sectionItemsFor("songs", "top tracks", "top songs").filterIsInstance<Innertube.SongItem>()
        .ifEmpty { youtubeArtistPage?.songs.orEmpty() }
    val overviewLatestRelease = sectionItemsFor("latest release", "new release").filterIsInstance<Innertube.AlbumItem>()
    val overviewAlbums = sectionItemsFor("albums", "releases", "full albums").filterIsInstance<Innertube.AlbumItem>()
        .ifEmpty { youtubeArtistPage?.albums.orEmpty() }
    val overviewSingles = sectionItemsFor("singles", "eps").filterIsInstance<Innertube.AlbumItem>()
        .ifEmpty { youtubeArtistPage?.singles.orEmpty() }
    val overviewArtists = sectionItemsFor("fans might also like", "similar artists", "related artists")
        .filterIsInstance<Innertube.ArtistItem>().ifEmpty { youtubeArtistPage?.similarArtists.orEmpty() }
    val overviewPlaylists = sectionItemsFor("playlists").filterIsInstance<Innertube.PlaylistItem>()
        .ifEmpty { youtubeArtistPage?.playlists.orEmpty() }
    val overviewFeaturedOn = sectionItemsFor("featured on", "appears on").filterIsInstance<Innertube.PlaylistItem>()
        .ifEmpty { youtubeArtistPage?.featuredOn.orEmpty() }

    LayoutWithAdaptiveThumbnail(thumbnailContent = thumbnailContent) {
        Box {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .background(colorPalette.background0)
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(
                        windowInsets
                            .only(WindowInsetsSides.Vertical)
                            .asPaddingValues()
                    )
            ) {
                // The artwork-led header replaces the old title-and-circle pairing; the shuffle
                // and radio actions ride underneath it rather than beside the title.
                ArtistHeader(
                    artistPage = youtubeArtistPage,
                    thumbnailUrl = youtubeArtistPage?.thumbnail?.url,
                    name = youtubeArtistPage?.name,
                    modifier = Modifier.padding(endPaddingValues)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        youtubeArtistPage?.shuffleEndpoint?.let { endpoint ->
                            SecondaryTextButton(
                                text = "Shuffle",
                                onClick = {
                                    binder?.stopRadio()
                                    binder?.playRadio(endpoint)
                                }
                            )
                        }

                        youtubeArtistPage?.radioEndpoint?.let { endpoint ->
                            SecondaryTextButton(
                                text = "Radio",
                                onClick = {
                                    binder?.stopRadio()
                                    binder?.playRadio(endpoint)
                                },
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }

                        SecondaryTextButton(
                            text = if (isSubscribed) "Subscribed" else "Subscribe",
                            onClick = onToggleSubscribed,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                if (youtubeArtistPage != null) {
                    overviewSongs.takeIf { it.isNotEmpty() }?.let { songs ->
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(endPaddingValues)
                        ) {
                            BasicText(
                                text = "Songs",
                                style = typography.m.semiBold.copy(color = Color.White),
                                modifier = sectionTextModifier
                            )

                            youtubeArtistPage.songsEndpoint?.let {
                                BasicText(
                                    text = "View all",
                                    style = typography.s.semiBold.copy(fontSize = typography.s.fontSize * 0.8f).copy(color = Color.White),
                                    modifier = sectionTextModifier
                                        .clickable(onClick = onViewAllSongsClick),
                                )
                            }
                        }

                        songs.forEach { song ->
                            SongItem(
                                song = song,
                                thumbnailSizeDp = songThumbnailSizeDp,
                                thumbnailSizePx = songThumbnailSizePx,
                                titleColor = Color.White,
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
                                            val mediaItem = song.asMediaItem
                                            binder?.stopRadio()
                                            binder?.player?.forcePlay(mediaItem)
                                            binder?.setupRadio(
                                                NavigationEndpoint.Endpoint.Watch(videoId = mediaItem.mediaId)
                                            )
                                        }
                                    )
                                    .padding(endPaddingValues)
                            )
                        }
                    }

                    overviewLatestRelease.takeIf { it.isNotEmpty() }?.let { albums ->
                        BasicText(
                            text = "Latest Release",
                            style = typography.m.semiBold.copy(color = Color.White),
                            modifier = sectionTextModifier
                        )
                        LazyRow(
                            contentPadding = endPaddingValues,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(albums, key = Innertube.AlbumItem::key) { album ->
                                AlbumItem(
                                    album = album,
                                    thumbnailSizePx = albumThumbnailSizePx,
                                    thumbnailSizeDp = albumThumbnailSizeDp,
                                    alternative = true,
                                    titleColor = Color.White,
                                    modifier = Modifier.clickable { onAlbumClick(album.key) }
                                )
                            }
                        }
                    }

                    overviewAlbums.takeIf { it.isNotEmpty() }?.let { albums ->
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(endPaddingValues)
                        ) {
                            BasicText(
                                text = "Albums",
                                style = typography.m.semiBold.copy(color = Color.White),
                                modifier = sectionTextModifier
                            )

                            youtubeArtistPage.albumsEndpoint?.let {
                                BasicText(
                                    text = "View all",
                                    style = typography.s.semiBold.copy(fontSize = typography.s.fontSize * 0.8f).copy(color = Color.White),
                                    modifier = sectionTextModifier
                                        .clickable(onClick = onViewAllAlbumsClick),
                                )
                            }
                        }

                        LazyRow(
                            contentPadding = endPaddingValues,
                            modifier = Modifier
                                .fillMaxWidth()
                        ) {
                            items(
                                items = albums,
                                key = Innertube.AlbumItem::key
                            ) { album ->
                                AlbumItem(
                                    album = album,
                                    thumbnailSizePx = albumThumbnailSizePx,
                                    thumbnailSizeDp = albumThumbnailSizeDp,
                                    alternative = true,
                                    titleColor = Color.White,
                                    modifier = Modifier
                                        .clickable(onClick = { onAlbumClick(album.key) })
                                )
                            }
                        }
                    }

                    overviewSingles.takeIf { it.isNotEmpty() }?.let { singles ->
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(endPaddingValues)
                        ) {
                            BasicText(
                                text = "Singles",
                                style = typography.m.semiBold.copy(color = Color.White),
                                modifier = sectionTextModifier
                            )

                            youtubeArtistPage.singlesEndpoint?.let {
                                BasicText(
                                    text = "View all",
                                    style = typography.s.semiBold.copy(fontSize = typography.s.fontSize * 0.8f).copy(color = Color.White),
                                    modifier = sectionTextModifier
                                        .clickable(onClick = onViewAllSinglesClick),
                                )
                            }
                        }

                        LazyRow(
                            contentPadding = endPaddingValues,
                            modifier = Modifier
                                .fillMaxWidth()
                        ) {
                            items(
                                items = singles,
                                key = Innertube.AlbumItem::key
                            ) { album ->
                                AlbumItem(
                                    album = album,
                                    thumbnailSizePx = albumThumbnailSizePx,
                                    thumbnailSizeDp = albumThumbnailSizeDp,
                                    alternative = true,
                                    titleColor = Color.White,
                                    modifier = Modifier
                                        .clickable(onClick = { onAlbumClick(album.key) })
                                )
                            }
                        }
                    }

                    // YouTube returns this shelf for every artist and nothing was reading it:
                    // a way sideways into artists of the same stripe, rather than only deeper
                    // into this one.
                    overviewArtists.takeIf { it.isNotEmpty() }?.let { artists ->
                        BasicText(
                            text = "Fans might also like",
                            style = typography.m.semiBold.copy(color = Color.White),
                            modifier = sectionTextModifier
                        )

                        LazyRow(
                            contentPadding = endPaddingValues,
                            modifier = Modifier
                                .fillMaxWidth()
                        ) {
                            items(
                                items = artists,
                                key = Innertube.ArtistItem::key
                            ) { artist ->
                                ArtistItem(
                                    artist = artist,
                                    thumbnailSizePx = albumThumbnailSizePx,
                                    thumbnailSizeDp = albumThumbnailSizeDp,
                                    alternative = true,
                                    modifier = Modifier
                                        .clickable(onClick = { onArtistClick(artist.key) })
                                )
                            }
                        }
                    }

                    overviewPlaylists.takeIf { it.isNotEmpty() }?.let { playlists ->
                        BasicText(
                            text = "Playlists",
                            style = typography.m.semiBold.copy(color = Color.White),
                            modifier = sectionTextModifier
                        )

                        LazyRow(
                            contentPadding = endPaddingValues,
                            modifier = Modifier
                                .fillMaxWidth()
                        ) {
                            items(
                                items = playlists,
                                key = Innertube.PlaylistItem::key
                            ) { playlist ->
                                PlaylistItem(
                                    playlist = playlist,
                                    thumbnailSizePx = albumThumbnailSizePx,
                                    thumbnailSizeDp = albumThumbnailSizeDp,
                                    alternative = true,
                                    modifier = Modifier
                                        .clickable(onClick = { onPlaylistClick(playlist.key) })
                                )
                            }
                        }
                    }

                    overviewFeaturedOn.takeIf { it.isNotEmpty() }?.let { playlists ->
                        BasicText(
                            text = "Featured On",
                            style = typography.m.semiBold.copy(color = Color.White),
                            modifier = sectionTextModifier
                        )
                        LazyRow(
                            contentPadding = endPaddingValues,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(playlists, key = Innertube.PlaylistItem::key) { playlist ->
                                PlaylistItem(
                                    playlist = playlist,
                                    thumbnailSizePx = albumThumbnailSizePx,
                                    thumbnailSizeDp = albumThumbnailSizeDp,
                                    alternative = true,
                                    modifier = Modifier.clickable { onPlaylistClick(playlist.key) }
                                )
                            }
                        }
                    }

                    youtubeArtistPage.description?.let { description ->
                        BasicText(
                            text = "About Artist",
                            style = typography.m.semiBold.copy(color = Color.White),
                            modifier = sectionTextModifier
                        )
                        val attributionsIndex = description.lastIndexOf("\n\nFrom Wikipedia")

                        Row(
                            modifier = Modifier
                                .padding(top = 16.dp)
                                .padding(vertical = 16.dp, horizontal = 8.dp)
                                .padding(endPaddingValues)
                        ) {
                            BasicText(
                                text = "“",
                                style = typography.xxl.semiBold.copy(color = Color.White),
                                modifier = Modifier
                                    .offset(y = (-8).dp)
                                    .align(Alignment.Top)
                            )

                            BasicText(
                                text = if (attributionsIndex == -1) {
                                    description
                                } else {
                                    description.substring(0, attributionsIndex)
                                },
                                style = typography.xxs.secondary.copy(color = Color.White),
                                modifier = Modifier
                                    .padding(horizontal = 8.dp)
                                    .weight(1f)
                            )

                            BasicText(
                                text = "„",
                                style = typography.xxl.semiBold.copy(color = Color.White),
                                modifier = Modifier
                                    .offset(y = 4.dp)
                                    .align(Alignment.Bottom)
                            )
                        }

                        if (attributionsIndex != -1) {
                            BasicText(
                                text = "From Wikipedia under Creative Commons Attribution CC-BY-SA 3.0",
                                style = typography.xxs.color(colorPalette.textDisabled).align(TextAlign.End).copy(color = Color.White),
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 16.dp)
                                    .padding(endPaddingValues)
                            )
                        }
                    }
                if (isLoadingMore) {
                    BasicText(
                        text = "Loading more...",
                        style = typography.s.copy(color = Color.White),
                        modifier = Modifier.padding(16.dp)
                    )
                } else if (loadMoreFailed) {
                    BasicText(
                        text = "Couldn't load more. Tap to retry.",
                        style = typography.s.copy(color = Color.White),
                        modifier = Modifier
                            .clickable {
                                loadMoreFailed = false
                                retryKey++
                            }
                            .padding(16.dp)
                    )
                }
                } else {
                    ShimmerHost {
                        TextPlaceholder(modifier = sectionTextModifier)

                        repeat(5) {
                            SongItemPlaceholder(
                                thumbnailSizeDp = songThumbnailSizeDp,
                            )
                        }

                        repeat(2) {
                            TextPlaceholder(modifier = sectionTextModifier)

                            Row {
                                repeat(2) {
                                    AlbumItemPlaceholder(
                                        thumbnailSizeDp = albumThumbnailSizeDp,
                                        alternative = true
                                    )
                                }
                            }
                        }
                    }
                }
            }

            youtubeArtistPage?.radioEndpoint?.let { endpoint ->
                FloatingActionsContainerWithScrollToTop(
                    scrollState = scrollState,
                    iconId = R.drawable.radio,
                    onClick = {
                        binder?.stopRadio()
                        binder?.playRadio(endpoint)
                    }
                )
            }
        }
    }
}
