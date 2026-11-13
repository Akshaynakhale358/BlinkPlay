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
import kotlinx.coroutines.withContext

/** How many of the most-played songs Quick Picks may seed itself from. */
private const val SeedPoolSize = 20

/** Used when nothing has been played yet, so the page is not empty on a fresh install. */
private const val DefaultSeedVideoId = "J7p4bzqLvCw"
// India-localized context is used only for the personalized Songs for You shelf.
private val IndiaMusicContext = Context.DefaultWeb.copy(
    client = Context.DefaultWeb.client.copy(hl = "hi", gl = "IN")
)

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

    var trending by persist<Song?>("home/trending")

    var relatedPageResult by persist<Result<Innertube.RelatedPage?>?>(tag = "home/relatedPageResult")
    var refreshToken by remember { mutableStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    var hasLoadedFreshRecommendations by remember { mutableStateOf(false) }
    var listeningSeeds by remember { mutableStateOf<List<Song>>(emptyList()) }
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

    // Read the real India Explore shelves supplied by YouTube Music.
    // This avoids presenting ordinary search results as official charts.
    LaunchedEffect(refreshToken) {
        val shelves = withContext(Dispatchers.IO) { fetchIndiaDirectSongShelves() }
        trendingSongs = shelves.first
        topSongs = shelves.second
        newReleases = shelves.third
    }

    LaunchedEffect(refreshToken) {
        if (refreshToken > 0) {
            relatedPageResult = null
            relatedPageResult = loadLanguageOrderedRelatedPage(trending, listeningSeeds)
            isRefreshing = false
        }
    }

    LaunchedEffect(Unit) {
        Database.trending(SeedPoolSize).distinctUntilChanged().collect { candidates ->
            // Build a multi-artist profile from listening history. A single heavily
            // replayed artist must not dominate every recommendation query.
            val diverseCandidates = candidates.distinctBy {
                it.artistsText?.trim()?.lowercase()?.takeIf { artist -> artist.isNotBlank() } ?: it.id
            }
            listeningSeeds = diverseCandidates.take(8)

            // A seed is drawn at random from the most-played rather than always being the single
            // top song, so one album in heavy rotation cannot own the page indefinitely.
            //
            // Once drawn it is kept for as long as it remains in the pool: this flow re-emits on
            // every play event, and re-drawing each time would reshuffle the screen underneath
            // whoever is reading it. Leaving home clears the persisted value, so the next visit
            // draws again.
            val current = trending
            val seed = current?.takeIf { song -> diverseCandidates.any { it.id == song.id } }
                ?: diverseCandidates.randomOrNull()

            if (!hasLoadedFreshRecommendations || seed?.id != current?.id) {
                // Discard recommendations persisted by older builds; they may start with English.
                relatedPageResult = null
                relatedPageResult = loadLanguageOrderedRelatedPage(seed, listeningSeeds)
                hasLoadedFreshRecommendations = true
            }
            trending = seed
        }
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

            relatedPageResult?.getOrNull()?.let { related ->
                LazyHorizontalGrid(
                    state = quickPicksLazyGridState,
                    rows = GridCells.Fixed(5),
                    flingBehavior = rememberSnapFlingBehavior(quickPicksLazyGridState, SnapPosition.Center),
                    contentPadding = endPaddingValues,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((songThumbnailSizeDp + Dimensions.itemsVerticalPadding * 2) * 5)
                ) {
                    trending?.let { song ->
                        item {
                            SongItem(
                                song = song,
                                thumbnailSizePx = songThumbnailSizePx,
                                thumbnailSizeDp = songThumbnailSizeDp,
                                trailingContent = {
                                    Image(
                                        painter = painterResource(R.drawable.star),
                                        contentDescription = null,
                                        colorFilter = ColorFilter.tint(colorPalette.accent),
                                        modifier = Modifier
                                            .size(16.dp)
                                    )
                                },
                                modifier = Modifier
                                    .combinedClickable(
                                        onLongClick = {
                                            menuState.display {
                                                NonQueuedMediaItemMenu(
                                                    onDismiss = menuState::hide,
                                                    mediaItem = song.asMediaItem,
                                                    onRemoveFromQuickPicks = {
                                                        query {
                                                            Database.clearEventsFor(song.id)
                                                        }
                                                    }
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
                                    .animateItem()
                                    .width(itemInHorizontalGridWidth)
                            )
                        }
                    }

                    items(
                        items = related.songs.orEmpty().let { songs ->
                            // The seed is rendered in its own starred tile above. Only remove
                            // a trailing duplicate when the API actually returned that seed.
                            if (trending != null && songs.lastOrNull()?.key == trending?.id) {
                                songs.dropLast(1)
                            } else {
                                songs
                            }
                        },
                        key = Innertube.SongItem::key
                    ) { song ->
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
                                        binder?.setupRadio(
                                            NavigationEndpoint.Endpoint.Watch(videoId = mediaItem.mediaId)
                                        )
                                    }
                                )
                                .animateItem()
                                .width(itemInHorizontalGridWidth)
                        )
                    }
                }

                Unit
            } ?: relatedPageResult?.exceptionOrNull()?.let {
                BasicText(
                    text = "Recommendations failed. Tap to retry.",
                    style = typography.s.secondary.center,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clickable {
                            isRefreshing = true
                            refreshToken += 1
                        }
                        .padding(all = 16.dp)
                )
            } ?: ShimmerHost {
                repeat(5) {
                    SongItemPlaceholder(
                        thumbnailSizeDp = songThumbnailSizeDp,
                    )
                }

                TextPlaceholder(modifier = sectionTextModifier)

                Row {
                    repeat(2) {
                        AlbumItemPlaceholder(
                            thumbnailSizeDp = albumThumbnailSizeDp,
                            alternative = true
                        )
                    }
                }

                TextPlaceholder(modifier = sectionTextModifier)

                Row {
                    repeat(2) {
                        ArtistItemPlaceholder(
                            thumbnailSizeDp = albumThumbnailSizeDp,
                            alternative = true
                        )
                    }
                }

                TextPlaceholder(modifier = sectionTextModifier)

                Row {
                    repeat(2) {
                        PlaylistItemPlaceholder(
                            thumbnailSizeDp = albumThumbnailSizeDp,
                            alternative = true
                        )
                    }
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

    // Prefer official direct chart roots; fall back to BlinkPlay's proven Music Search path.
    val trendingDeferred = async {
        Innertube.indiaChartSongs(trendingQuery, "videos")
            .ifEmpty { searchSongs("trending popular Indian songs Hindi Marathi Punjabi Tamil Telugu") }
    }
    val topDeferred = async {
        Innertube.indiaChartSongs(topQuery, "trackViews")
            .ifEmpty { searchSongs("India top chart hit songs Hindi Marathi Punjabi Tamil Telugu") }
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
        songs.distinctBy(Innertube.SongItem::key)
            .ifEmpty { searchSongs("latest new Indian songs official releases") }
    }

    Triple(trendingDeferred.await(), topDeferred.await(), releasesDeferred.await())
}

private suspend fun loadLanguageOrderedRelatedPage(
    seed: Song?,
    historySeeds: List<Song> = listOfNotNull(seed)
): Result<Innertube.RelatedPage?> = coroutineScope {
    // Related-page metadata is optional: a failure must not block personalized song search.
    val baseDeferred = async {
        Innertube.relatedPage(
            NextBody(videoId = seed?.id ?: DefaultSeedVideoId)
        )?.getOrNull()
    }

    val seedContext = historySeeds
        .take(6)
        .joinToString(" ") { historySong ->
            listOfNotNull(historySong.title, historySong.artistsText).joinToString(" ")
        }
        .take(240)
        .ifBlank {
            listOfNotNull(seed?.title, seed?.artistsText).joinToString(" ").ifBlank { "popular" }
        }

    suspend fun fetchRelatedSongs(languageQuery: String) = Innertube.searchPage(
        body = SearchBody(
            context = IndiaMusicContext,
            query = "$languageQuery similar to $seedContext",
            params = Innertube.SearchFilter.Song.value
        ),
        fromMusicShelfRendererContent = Innertube.SongItem::from
    )?.getOrNull()?.items.orEmpty()

    // Run language searches together, then restore the requested Hindi-first ordering.
    val languageResults = listOf(
        async { fetchRelatedSongs("Hindi Bollywood songs") },
        async { fetchRelatedSongs("Marathi songs") },
        async {
            fetchRelatedSongs(
                "Punjabi Tamil Telugu Bengali Kannada Malayalam Indian regional songs"
            )
        },
        async { fetchRelatedSongs("English international songs") }
    ).awaitAll()

    val hindi = languageResults[0]
    val marathi = languageResults[1]
        .filterNot { song -> hindi.any { it.key == song.key } }
    val otherIndianLanguages = languageResults[2]
    val indianSongs = (hindi + marathi + otherIndianLanguages).distinctBy { it.key }
    val english = languageResults[3]
        .filterNot { song -> indianSongs.any { it.key == song.key } }

    val orderedSongs = (indianSongs + english).distinctBy { song ->
        // Limit each recommendation batch to one result per lead artist.
        song.authors
            ?.firstOrNull()
            ?.name
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
            ?: song.key
    }

    val base = baseDeferred.await() ?: Innertube.RelatedPage()
    Result.success(base.copy(songs = orderedSongs))
}
