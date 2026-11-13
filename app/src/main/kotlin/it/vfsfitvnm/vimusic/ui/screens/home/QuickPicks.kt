package it.vfsfitvnm.vimusic.ui.screens.home

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import it.vfsfitvnm.compose.persist.persist
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.Context
import it.vfsfitvnm.innertube.models.NavigationEndpoint
import it.vfsfitvnm.innertube.models.bodies.BrowseBody
import it.vfsfitvnm.innertube.models.bodies.NextBody
import it.vfsfitvnm.innertube.models.bodies.SearchBody
import it.vfsfitvnm.innertube.requests.albumPage
import it.vfsfitvnm.innertube.requests.browsePage
import it.vfsfitvnm.innertube.requests.indiaChartSongs
import it.vfsfitvnm.innertube.requests.relatedPage
import it.vfsfitvnm.innertube.requests.MusicDiscovery
import it.vfsfitvnm.innertube.requests.musicDiscovery
import it.vfsfitvnm.innertube.requests.musicExplorePage
import it.vfsfitvnm.innertube.requests.playlistPage
import it.vfsfitvnm.innertube.requests.searchPage
import it.vfsfitvnm.innertube.utils.from
import it.vfsfitvnm.vimusic.Database
import it.vfsfitvnm.vimusic.LocalPlayerAwareWindowInsets
import it.vfsfitvnm.vimusic.LocalPlayerServiceBinder
import it.vfsfitvnm.vimusic.R
import it.vfsfitvnm.vimusic.models.Song
import it.vfsfitvnm.vimusic.query
import it.vfsfitvnm.vimusic.ui.components.LocalMenuState
import it.vfsfitvnm.vimusic.ui.components.ShimmerHost
import it.vfsfitvnm.vimusic.ui.components.themed.Header
import it.vfsfitvnm.vimusic.ui.components.themed.NonQueuedMediaItemMenu
import it.vfsfitvnm.vimusic.ui.components.themed.TextPlaceholder
import it.vfsfitvnm.vimusic.ui.items.AlbumItemPlaceholder
import it.vfsfitvnm.vimusic.ui.items.ArtistItemPlaceholder
import it.vfsfitvnm.vimusic.ui.items.PlaylistItemPlaceholder
import it.vfsfitvnm.vimusic.ui.items.SongItem
import it.vfsfitvnm.vimusic.ui.items.SongItemPlaceholder
import it.vfsfitvnm.vimusic.ui.styling.Dimensions
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.ui.styling.px
import it.vfsfitvnm.vimusic.utils.DisposableListener
import it.vfsfitvnm.vimusic.utils.asMediaItem
import it.vfsfitvnm.vimusic.utils.center
import it.vfsfitvnm.vimusic.utils.forcePlay
import it.vfsfitvnm.vimusic.utils.isLandscape
import it.vfsfitvnm.vimusic.utils.secondary
import it.vfsfitvnm.vimusic.utils.semiBold
import it.vfsfitvnm.vimusic.ui.screens.browse.MusicBrowseShelves
import it.vfsfitvnm.vimusic.ui.screens.browse.browseCategoryRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
fun QuickPicks(
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onSearchClick: () -> Unit,
) {
    val (colorPalette, typography) = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current
    val windowInsets = LocalPlayerAwareWindowInsets.current

    var refreshToken by remember { mutableStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    var songsForYou by remember { mutableStateOf<List<Innertube.SongItem>>(emptyList()) }
    var songsForYouFailed by remember { mutableStateOf(false) }
    var currentVideoId by remember { mutableStateOf(binder?.player?.currentMediaItem?.mediaId) }

    binder?.player?.DisposableListener {
        object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                currentVideoId = mediaItem?.mediaId
            }
        }
    }
    var serverDiscovery by remember { mutableStateOf<MusicDiscovery?>(null) }
    var discoveryFailed by remember { mutableStateOf(false) }
    var trendingSongs by remember { mutableStateOf<List<Innertube.SongItem>>(emptyList()) }
    var topSongs by remember { mutableStateOf<List<Innertube.SongItem>>(emptyList()) }
    var newReleases by remember { mutableStateOf<List<Innertube.SongItem>>(emptyList()) }

    LaunchedEffect(refreshToken) {
        val result = withContext(Dispatchers.IO) { Innertube.musicDiscovery(forceRefresh = refreshToken > 0) }
        serverDiscovery = result?.getOrNull() ?: serverDiscovery
        discoveryFailed = serverDiscovery?.sections.isNullOrEmpty()
        isRefreshing = false
    }

    // Recommendations are based on the song currently playing, never on a history-derived seed.
    // Exclude the current track and tracks already listened to from the returned suggestions.
    LaunchedEffect(refreshToken, currentVideoId) {
        songsForYouFailed = false
        val related = if (!currentVideoId.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                Innertube.relatedPage(
                    NextBody(
                        context = Context.DefaultWeb.copy(
                            client = Context.DefaultWeb.client.copy(hl = "en", gl = "IN")
                        ),
                        videoId = currentVideoId
                    )
                )?.getOrNull()?.songs.orEmpty()
            }
        } else emptyList()

        val playedIds = withContext(Dispatchers.IO) {
            Database.trending(100).first().map { it.id }.toSet()
        }
        val recommendations = related
            .filter { it.key != currentVideoId && it.key !in playedIds }
            .distinctBy(Innertube.SongItem::key)

        val title = binder?.player?.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty()
        val artist = binder?.player?.currentMediaItem?.mediaMetadata?.artist?.toString().orEmpty()
        val relatedSearch = if (currentVideoId != null && title.isNotBlank()) {
            withContext(Dispatchers.IO) {
                val searchContext = Context.DefaultWeb.copy(
                    client = Context.DefaultWeb.client.copy(hl = "en", gl = "IN")
                )
                val queries = listOf(
                    "$title $artist similar songs",
                    "songs like $title $artist"
                )
                queries.flatMap { query ->
                    Innertube.searchPage(
                        body = SearchBody(
                            context = searchContext,
                            query = query,
                            params = Innertube.SearchFilter.Song.value
                        ),
                        fromMusicShelfRendererContent = Innertube.SongItem::from
                    )?.getOrNull()?.items.orEmpty()
                }.distinctBy(Innertube.SongItem::key)
            }
        } else emptyList()
        songsForYou = if (currentVideoId == null) fetchAutomaticSongsForYou()
            else (recommendations + relatedSearch)
                .filter { it.key != currentVideoId && it.key !in playedIds }
                .distinctBy(Innertube.SongItem::key)
        songsForYouFailed = songsForYou.isEmpty()

        val shelves = withContext(Dispatchers.IO) { fetchIndiaDirectSongShelves() }
        trendingSongs = shelves.first
        topSongs = shelves.second
        newReleases = shelves.third
        isRefreshing = false
    }

    val songThumbnailSizeDp = Dimensions.thumbnails.song
    val songThumbnailSizePx = songThumbnailSizeDp.px
    val albumThumbnailSizeDp = 108.dp
    val albumThumbnailSizePx = albumThumbnailSizeDp.px
    val artistThumbnailSizeDp = 92.dp
    val artistThumbnailSizePx = artistThumbnailSizeDp.px
    val playlistThumbnailSizeDp = 108.dp
    val playlistThumbnailSizePx = playlistThumbnailSizeDp.px

    val scrollState = rememberScrollState()
    val quickPicksLazyGridState = rememberLazyGridState()

    val endPaddingValues = windowInsets.only(WindowInsetsSides.End).asPaddingValues()

    val sectionTextModifier = Modifier
        .padding(horizontal = 16.dp)
        .padding(top = 24.dp, bottom = 8.dp)
        .padding(endPaddingValues)

    @OptIn(ExperimentalMaterial3Api::class)
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            if (!isRefreshing) {
                isRefreshing = true
                refreshToken += 1
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
    BoxWithConstraints {
        val quickPicksLazyGridItemWidthFactor = if (isLandscape && maxWidth * 0.475f >= 320.dp) {
            0.475f
        } else {
            0.9f
        }

        val itemInHorizontalGridWidth = maxWidth * quickPicksLazyGridItemWidthFactor

        Column(
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
            Header(
                title = "QUICK PICKS",
                modifier = Modifier
                    .padding(endPaddingValues)
            )



            BasicText(
                text = "SONGS FOR YOU",
                style = typography.s.semiBold,
                modifier = sectionTextModifier
            )

            if (songsForYou.isNotEmpty()) {
                LazyHorizontalGrid(
                    state = quickPicksLazyGridState,
                    rows = GridCells.Fixed(5),
                    flingBehavior = rememberSnapFlingBehavior(quickPicksLazyGridState, SnapPosition.Center),
                    contentPadding = endPaddingValues,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((songThumbnailSizeDp + Dimensions.itemsVerticalPadding * 2) * 5)
                ) {
                    items(items = songsForYou.distinctBy { it.key }, key = Innertube.SongItem::key) { song ->
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
                                                mediaItem = song.asMediaItem
                                            )
                                        }
                                    },
                                    onClick = {
                                        val mediaItem = song.asMediaItem
                                        binder?.stopRadio()
                                        binder?.player?.forcePlay(mediaItem)
                                        binder?.setupRadio(NavigationEndpoint.Endpoint.Watch(videoId = mediaItem.mediaId))
                                    }
                                )
                                .animateItem()
                                .width(itemInHorizontalGridWidth)
                        )
                    }
                }
            } else if (songsForYouFailed) {
                BasicText(
                    text = "Songs feed unavailable. Tap to retry.",
                    style = typography.s.secondary.center,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clickable { isRefreshing = true; refreshToken += 1 }
                        .padding(16.dp)
                )
            } else {
                ShimmerHost {
                    repeat(5) { SongItemPlaceholder(thumbnailSizeDp = songThumbnailSizeDp) }
                }
            }

            QuickPicksSongShelf(
                title = "TRENDING SONGS",
                songs = trendingSongs,
                thumbnailSizePx = songThumbnailSizePx,
                thumbnailSizeDp = songThumbnailSizeDp,
                itemWidth = itemInHorizontalGridWidth,
                endPaddingValues = endPaddingValues,
                onSongClick = { song ->
                    val mediaItem = song.asMediaItem
                    binder?.stopRadio()
                    binder?.player?.forcePlay(mediaItem)
                    binder?.setupRadio(NavigationEndpoint.Endpoint.Watch(videoId = mediaItem.mediaId))
                },
                onSongLongClick = { song ->
                    menuState.display {
                        NonQueuedMediaItemMenu(onDismiss = menuState::hide, mediaItem = song.asMediaItem)
                    }
                }
            )

            QuickPicksSongShelf(
                title = "TOP SONGS",
                songs = topSongs,
                thumbnailSizePx = songThumbnailSizePx,
                thumbnailSizeDp = songThumbnailSizeDp,
                itemWidth = itemInHorizontalGridWidth,
                endPaddingValues = endPaddingValues,
                onSongClick = { song ->
                    val mediaItem = song.asMediaItem
                    binder?.stopRadio()
                    binder?.player?.forcePlay(mediaItem)
                    binder?.setupRadio(NavigationEndpoint.Endpoint.Watch(videoId = mediaItem.mediaId))
                },
                onSongLongClick = { song ->
                    menuState.display {
                        NonQueuedMediaItemMenu(onDismiss = menuState::hide, mediaItem = song.asMediaItem)
                    }
                }
            )

            QuickPicksSongShelf(
                title = "NEW RELEASES",
                songs = newReleases,
                thumbnailSizePx = songThumbnailSizePx,
                thumbnailSizeDp = songThumbnailSizeDp,
                itemWidth = itemInHorizontalGridWidth,
                endPaddingValues = endPaddingValues,
                onSongClick = { song ->
                    val mediaItem = song.asMediaItem
                    binder?.stopRadio()
                    binder?.player?.forcePlay(mediaItem)
                    binder?.setupRadio(NavigationEndpoint.Endpoint.Watch(videoId = mediaItem.mediaId))
                },
                onSongLongClick = { song ->
                    menuState.display {
                        NonQueuedMediaItemMenu(onDismiss = menuState::hide, mediaItem = song.asMediaItem)
                    }
                }
            )

        when {
            serverDiscovery?.sections?.isNotEmpty() == true -> MusicBrowseShelves(
                sections = serverDiscovery!!.sections,
                sectionTextModifier = sectionTextModifier,
                onSongClick = { song ->
                    val mediaItem = song.asMediaItem
                    binder?.stopRadio()
                    binder?.player?.forcePlay(mediaItem)
                    binder?.setupRadio(NavigationEndpoint.Endpoint.Watch(videoId = mediaItem.mediaId))
                },
                onSongLongClick = { song ->
                    menuState.display {
                        NonQueuedMediaItemMenu(onDismiss = menuState::hide, mediaItem = song.asMediaItem)
                    }
                },
                onAlbumClick = onAlbumClick,
                onArtistClick = onArtistClick,
                onPlaylistClick = onPlaylistClick,
                onCategoryClick = { id, params ->
                    onPlaylistClick(browseCategoryRoute(id, params))
                }
            )
            discoveryFailed -> BasicText(
                text = "YouTube Music feed unavailable. Tap to retry.",
                style = typography.s.secondary.center,
                modifier = Modifier.align(Alignment.CenterHorizontally)
                    .clickable { isRefreshing = true; refreshToken += 1 }
                    .padding(16.dp)
            )
            else -> ShimmerHost {
                repeat(3) { TextPlaceholder(modifier = sectionTextModifier) }
                Row {
                    repeat(3) { PlaylistItemPlaceholder(thumbnailSizeDp = playlistThumbnailSizeDp, alternative = true) }
                }
            }
        }
    }
    }
    }
}




@Composable
private fun QuickPicksSongShelf(
    title: String,
    songs: List<Innertube.SongItem>,
    thumbnailSizePx: Int,
    thumbnailSizeDp: androidx.compose.ui.unit.Dp,
    itemWidth: androidx.compose.ui.unit.Dp,
    endPaddingValues: androidx.compose.foundation.layout.PaddingValues,
    onSongClick: (Innertube.SongItem) -> Unit,
    onSongLongClick: (Innertube.SongItem) -> Unit
) {
    val (_, typography) = LocalAppearance.current
    val gridState = rememberLazyGridState()

    BasicText(
        text = title,
        style = typography.s.semiBold,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(top = 24.dp, bottom = 8.dp)
            .padding(endPaddingValues)
    )

    if (songs.isEmpty()) {
        ShimmerHost {
            repeat(5) { SongItemPlaceholder(thumbnailSizeDp = thumbnailSizeDp) }
        }
    } else {
        LazyHorizontalGrid(
            state = gridState,
            rows = GridCells.Fixed(5),
            flingBehavior = rememberSnapFlingBehavior(gridState, SnapPosition.Center),
            contentPadding = endPaddingValues,
            modifier = Modifier
                .fillMaxWidth()
                .height((thumbnailSizeDp + Dimensions.itemsVerticalPadding * 2) * 5)
        ) {
            items(items = songs.distinctBy { it.key }, key = Innertube.SongItem::key) { song ->
                SongItem(
                    song = song,
                    thumbnailSizePx = thumbnailSizePx,
                    thumbnailSizeDp = thumbnailSizeDp,
                    modifier = Modifier
                        .combinedClickable(
                            onLongClick = { onSongLongClick(song) },
                            onClick = { onSongClick(song) }
                        )
                        .animateItem()
                        .width(itemWidth)
                )
            }
        }
    }
}

private suspend fun fetchAutomaticSongsForYou(): List<Innertube.SongItem> {
    val explore = Innertube.musicExplorePage()?.getOrNull()
    val exploreSongs = explore?.sections.orEmpty()
        .flatMap { it.items }
        .filterIsInstance<Innertube.SongItem>()
        .distinctBy(Innertube.SongItem::key)
    if (exploreSongs.size >= 5) return exploreSongs

    val searchFallback = Innertube.searchPage(
        body = SearchBody(
            context = Context.DefaultWeb.copy(
                client = Context.DefaultWeb.client.copy(hl = "en", gl = "IN")
            ),
            query = "popular new songs India Hindi Marathi Punjabi Tamil Telugu Kannada Malayalam",
            params = Innertube.SearchFilter.Song.value
        ),
        fromMusicShelfRendererContent = Innertube.SongItem::from
    )?.getOrNull()?.items.orEmpty()

    return (exploreSongs + searchFallback).distinctBy(Innertube.SongItem::key)
}

private val IndiaExploreContext = Context.DefaultWeb.copy(
    client = Context.DefaultWeb.client.copy(hl = "en", gl = "IN")
)

private suspend fun fetchIndiaDirectSongShelves(): Triple<
    List<Innertube.SongItem>,
    List<Innertube.SongItem>,
    List<Innertube.SongItem>
> = coroutineScope {
    val endDate = java.time.LocalDate.now(java.time.ZoneOffset.UTC)
        .minusDays(1)
        .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)

    val trendingQuery =
        "flags=MusicCharts__enable_apac_and_shorts_charts_expansion&perspective=CHART_DETAILS&chart_params_country_code=in&chart_params_chart_type=TRENDING_VIDEOS"
    val topQuery =
        "perspective=CHART_DETAILS&chart_params_country_code=in&chart_params_chart_type=TRACKS&chart_params_period_type=WEEKLY&chart_params_end_date=$endDate"

    suspend fun searchSongs(query: String): List<Innertube.SongItem> =
        Innertube.searchPage(
            body = SearchBody(
                context = IndiaExploreContext,
                query = query,
                params = Innertube.SearchFilter.Song.value
            ),
            fromMusicShelfRendererContent = Innertube.SongItem::from
        )?.getOrNull()?.items.orEmpty()

    // Prefer official direct chart roots, but treat a tiny partial response as an
    // incomplete feed too. The chart endpoint sometimes returns only one row.
    // Merge search results to fill the shelf, while keeping chart ranking first.
    val trendingDeferred = async {
        val chart = Innertube.indiaChartSongs(trendingQuery, "videos")
        if (chart.size >= 10) chart else
            (chart + searchSongs("trending popular Indian songs Hindi Marathi Punjabi Tamil Telugu"))
                .distinctBy(Innertube.SongItem::key)
    }
    val topDeferred = async {
        val chart = Innertube.indiaChartSongs(topQuery, "trackViews")
        if (chart.size >= 10) chart else
            (chart + searchSongs("India top chart hit songs Hindi Marathi Punjabi Tamil Telugu"))
                .distinctBy(Innertube.SongItem::key)
    }

    // New releases are fetched from YouTube Music's own dedicated browse root, not Explore text matching.
    val releasesDeferred = async {
        val page = Innertube.browsePage(
            BrowseBody(context = IndiaExploreContext, browseId = "FEmusic_new_releases_albums")
        )?.getOrNull()
        val albums = page?.sections.orEmpty()
            .flatMap { it.items }
            .filterIsInstance<Innertube.AlbumItem>()
            .distinctBy(Innertube.AlbumItem::key)
            .take(12)

        val songs = mutableListOf<Innertube.SongItem>()
        for (album in albums) {
            val endpoint = album.info?.endpoint ?: continue
            val id = endpoint.browseId ?: continue
            val albumPage = Innertube.albumPage(
                BrowseBody(context = IndiaExploreContext, browseId = id, params = endpoint.params)
            )?.getOrNull()
            songs += albumPage?.songsPage?.items.orEmpty()
        }
        val releases = songs.distinctBy(Innertube.SongItem::key)
        if (releases.size >= 10) releases else
            (releases + searchSongs("latest new Indian songs official releases"))
                .distinctBy(Innertube.SongItem::key)
    }

    Triple(trendingDeferred.await(), topDeferred.await(), releasesDeferred.await())
}

