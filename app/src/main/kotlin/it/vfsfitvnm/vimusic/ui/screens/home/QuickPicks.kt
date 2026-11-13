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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyRowItems
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import it.vfsfitvnm.compose.persist.persist
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.Context
import it.vfsfitvnm.innertube.models.bodies.SearchBody
import it.vfsfitvnm.innertube.models.bodies.NextBody
import it.vfsfitvnm.innertube.models.NavigationEndpoint
import it.vfsfitvnm.innertube.requests.indiaChartSongs
import it.vfsfitvnm.innertube.requests.searchPage
import it.vfsfitvnm.innertube.requests.relatedPage
import it.vfsfitvnm.innertube.requests.MusicDiscovery
import it.vfsfitvnm.innertube.requests.musicDiscovery
import it.vfsfitvnm.innertube.requests.playlistPage
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
import it.vfsfitvnm.vimusic.ui.screens.browse.personalizeSongLists
import it.vfsfitvnm.vimusic.ui.screens.browse.browseCategoryRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

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
    var serverDiscovery by remember { mutableStateOf<MusicDiscovery?>(null) }
    var trendingSongs by remember { mutableStateOf<List<Innertube.SongItem>>(emptyList()) }
    var topSongs by remember { mutableStateOf<List<Innertube.SongItem>>(emptyList()) }
    var newReleases by remember { mutableStateOf<List<Innertube.SongItem>>(emptyList()) }
    var longListenSongs by remember { mutableStateOf<Map<String, List<Innertube.SongItem>>>(emptyMap()) }
    var longListenDailyToken by remember { mutableStateOf(0) }
    var selectedLongListenCategory by remember { mutableStateOf<LongListenCategory?>(null) }

    // Refresh only Long Listen at Indian midnight, without changing other Quick Picks shelves.
    LaunchedEffect(Unit) {
        while (true) {
            val dayMillis = 86_400_000L
            val indiaOffsetMillis = 19_800_000L
            val indiaNow = System.currentTimeMillis() + indiaOffsetMillis
            delay(dayMillis - (indiaNow % dayMillis))
            longListenDailyToken += 1
        }
    }

    LaunchedEffect(refreshToken, longListenDailyToken) {
        longListenSongs = withContext(Dispatchers.IO) { fetchLongListenCategories() }
        isRefreshing = false
    }

    LaunchedEffect(refreshToken) {
        val discovery = withContext(Dispatchers.IO) {
            Innertube.musicDiscovery(forceRefresh = refreshToken > 0)
                ?.getOrNull()
        }
        serverDiscovery = discovery ?: serverDiscovery
        isRefreshing = false
    }

    // Songs For You and New Releases use the same YouTube Music song-search
    // endpoint. Only the category query changes, so both shelves share one parser.
    LaunchedEffect(refreshToken) {
        val (recommendations, fetchedShelves) = withContext(Dispatchers.IO) {
            coroutineScope {
                val forYou = async { fetchPersonalizedSongsForYou() }
                val shelvesDeferred = async { fetchIndiaDirectSongShelves() }
                val (forYouSongs, shelves) = forYou.await() to shelvesDeferred.await()
                forYouSongs to listOf(shelves.first, shelves.second, shelves.third)
            }
        }
        songsForYou = recommendations.distinctBy(Innertube.SongItem::key)
        // Personalize each shelf independently so its original category stays intact:
        // trending remains trending, top songs remain chart-ranked candidates, and
        // new releases remain sourced only from the new-release search.
        val personalizedShelves = withContext(Dispatchers.IO) {
            personalizeSongLists(fetchedShelves)
        }
        trendingSongs = personalizedShelves[0].distinctBy(Innertube.SongItem::key)
        topSongs = personalizedShelves[1].distinctBy(Innertube.SongItem::key)
        newReleases = personalizedShelves[2].distinctBy(Innertube.SongItem::key)
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
            if (selectedLongListenCategory == null) {
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


            BasicText(
                text = "LONG LISTEN",
                style = typography.s.semiBold,
                modifier = sectionTextModifier
            )

            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                lazyRowItems(items = dailyLongListenCategories(), key = LongListenCategory::title) { category ->
                    QuickPicksLongListenShelf(
                        category = category,
                        songs = longListenSongs[category.title].orEmpty().take(5),
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
                }
            }

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
            else -> ShimmerHost {
                repeat(3) { TextPlaceholder(modifier = sectionTextModifier) }
                Row {
                    repeat(3) { PlaylistItemPlaceholder(thumbnailSizeDp = playlistThumbnailSizeDp, alternative = true) }
                }
            }
            }
            } else {
                val category = selectedLongListenCategory!!
                BasicText(
                    text = "‹  BACK TO QUICK PICKS",
                    style = typography.s.semiBold,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .clickable { selectedLongListenCategory = null }
                )
                Header(title = category.title.uppercase())
                QuickPicksSongShelf(
                    title = "ALL " + category.title.uppercase() + " SONGS",
                    songs = longListenSongs[category.title].orEmpty(),
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


private data class LongListenCategory(val title: String, val query: String)

private val LongListenCategoryPool = listOf(
    LongListenCategory("Long Drive Mix", "long drive road trip journey full songs Hindi Marathi"),
    LongListenCategory("Romantic Long Mix", "romantic love full songs Hindi Marathi"),
    LongListenCategory("Peaceful Melody Mix", "peaceful relaxing melody full songs India"),
    LongListenCategory("Emotional Long Mix", "emotional sad heartbreak full songs Hindi Marathi"),
    LongListenCategory("Dance Long Mix", "dance party full songs Hindi Indian"),
    LongListenCategory("Evergreen Classics Mix", "evergreen old classic full songs Hindi"),
    LongListenCategory("Workout Energy Mix", "workout gym energetic full songs"),
    LongListenCategory("Chill & Lofi Mix", "chill lofi mellow full songs"),
    LongListenCategory("Marathi Long Mix", "Marathi full songs long melody"),
    LongListenCategory("Bollywood Melody Mix", "Bollywood full melody songs"),
    LongListenCategory("Sufi Soul Mix", "Sufi soulful full songs Hindi"),
    LongListenCategory("Indian Indie Mix", "Indian indie independent full songs"),
    LongListenCategory("Monsoon Mix", "rain monsoon baarish full songs Hindi"),
    LongListenCategory("Friendship Mix", "friendship dosti yaari full songs"),
    LongListenCategory("Tamil Melody Mix", "Tamil full melody songs"),
    LongListenCategory("Telugu Melody Mix", "Telugu full melody songs"),
    LongListenCategory("90s Nostalgia Mix", "90s Hindi evergreen full songs"),
    LongListenCategory("2000s Bollywood Mix", "2000s Bollywood full songs"),
    LongListenCategory("English Pop Mix", "English pop full songs"),
    LongListenCategory("Live Performance Mix", "live concert full song performances"),
    LongListenCategory("Acoustic Melody Mix", "acoustic guitar full songs Indian"),
    LongListenCategory("Feel Good Mix", "feel good happy full songs Hindi Indian"),
    LongListenCategory("Heartbreak Mix", "breakup heartbreak full songs Hindi"),
    LongListenCategory("Bollywood Duets Mix", "Bollywood duet full songs"),
    LongListenCategory("Retro Remix Mix", "retro remix full songs Hindi"),
    LongListenCategory("Female Vocal Mix", "female vocal full songs Indian"),
    LongListenCategory("Male Vocal Mix", "male vocal full songs Indian"),
    LongListenCategory("Travel Memories Mix", "travel musafir safar full songs Hindi"),
    LongListenCategory("Soft Melody Mix", "soft romantic melody full songs India"),
    LongListenCategory("Festival Party Mix", "Indian festival celebration dance full songs")
)

private fun dailyLongListenCategories(): List<LongListenCategory> {
    val indiaDay = (System.currentTimeMillis() + 19_800_000L) / 86_400_000L
    val start = ((indiaDay * 10L) % LongListenCategoryPool.size).toInt()
    return (0 until 10).map { index ->
        LongListenCategoryPool[(start + index) % LongListenCategoryPool.size]
    }
}

@Composable
private fun QuickPicksLongListenShelf(
    category: LongListenCategory,
    songs: List<Innertube.SongItem>,
    onSongClick: (Innertube.SongItem) -> Unit,
    onSongLongClick: (Innertube.SongItem) -> Unit
) {
    val (_, typography) = LocalAppearance.current
    Column(
        modifier = Modifier
            .width(340.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF29231B))
            .padding(16.dp)
    ) {
        BasicText(
            text = category.title,
            style = typography.l.semiBold,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        if (songs.isEmpty()) {
            ShimmerHost { repeat(5) { SongItemPlaceholder(thumbnailSizeDp = 64.dp) } }
        } else {
            songs.take(5).forEach { song ->
                SongItem(
                    song = song,
                    thumbnailSizePx = 64.dp.px,
                    thumbnailSizeDp = 64.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onSongClick(song) },
                            onLongClick = { onSongLongClick(song) }
                        )
                )
            }
        }
    }
}

private fun String?.toDurationSeconds(): Int {
    if (this.isNullOrBlank()) return 0
    val parts = trim().split(":").mapNotNull { it.toIntOrNull() }
    if (parts.isEmpty()) return 0
    return parts.fold(0) { total, part -> total * 60 + part }
}

private suspend fun fetchTopTenSongs(): List<Innertube.SongItem> {
    val chartQuery =
        "chart_params_type=WEEK&perspective=CHART&flags=viral_video_chart&selected_chart=TRACKS&chart_params_id=weekly:0:0:IN"
    val chartSongs = Innertube.indiaChartSongs(chartQuery, "trackTypes", 0, "trackViews")
        .distinctBy(Innertube.SongItem::key)
        .sortedByDescending { it.durationText.toDurationSeconds() }

    // Rotate ten long tracks each day in India time; the source remains the India Top Songs chart.
    val indiaDay = (System.currentTimeMillis() + 19_800_000L) / 86_400_000L
    if (chartSongs.isEmpty()) return emptyList()
    val offset = ((indiaDay * 10L) % chartSongs.size).toInt()
    val dailyOrder = chartSongs.drop(offset) + chartSongs.take(offset)
    return dailyOrder.take(10)
}

private suspend fun fetchLongListenCategories(): Map<String, List<Innertube.SongItem>> = coroutineScope {
    val indiaDay = (System.currentTimeMillis() + 19_800_000L) / 86_400_000L

    dailyLongListenCategories().map { category ->
        async {
            // Search within the requested music category. Do not apply broad title/artist
            // keyword filters here: those were discarding valid results and leaving empty rows.
            val primary = fetchSongsByCategory("${category.query} 30 minute long mix full audio")
            val fallback = if (primary.size < 5) {
                fetchSongsByCategory("${category.query} 30 minutes extended full mix")
            } else {
                emptyList()
            }

            val candidates = (primary + fallback)
                .distinctBy(Innertube.SongItem::key)

            val personalized = runCatching {
                personalizeSongLists(listOf(candidates)).firstOrNull().orEmpty()
            }.getOrDefault(candidates)

            val durationRanked = personalized.distinctBy(Innertube.SongItem::key)
                .sortedByDescending { it.durationText.toDurationSeconds() }

            // Strict minimum requested: show only tracks whose individual duration is 30+ minutes.
            // Do not substitute shorter tracks when the source cannot find enough long audio.
            val thirtyMinuteTracks = durationRanked.filter {
                it.durationText.toDurationSeconds() >= 1800
            }
            val pool = thirtyMinuteTracks.take(40)

            // Rotate five-song selections daily so both songs and the ten bunches refresh.
            val offset = if (pool.isEmpty()) 0 else ((indiaDay * 5L) % pool.size).toInt()
            val rotated = if (pool.isEmpty()) emptyList() else pool.drop(offset) + pool.take(offset)
            category.title to rotated.take(5)
        }
    }.awaitAll().toMap()
}

private suspend fun fetchSongsByCategory(query: String): List<Innertube.SongItem> =
    Innertube.searchPage(
        body = SearchBody(
            context = IndiaExploreContext,
            query = query,
            params = Innertube.SearchFilter.Song.value
        ),
        fromMusicShelfRendererContent = Innertube.SongItem::from
    )?.getOrNull()?.items.orEmpty()
        .distinctBy(Innertube.SongItem::key)

/**
 * Personal recommendations use the listener's most-played and liked tracks.
 * Four distinct history seeds are queried together so one song or artist cannot dominate.
 * YouTube Music's related feed matches language, artist, genre and musical style.
 */
private suspend fun fetchPersonalizedSongsForYou(): List<Innertube.SongItem> = coroutineScope {
    val weightedHistory = Database.trending(30).first()
    val playTimeHistory = Database.songsByPlayTimeDesc().first()
    val history = (weightedHistory + playTimeHistory).distinctBy { it.id }
    val favorites = Database.favorites().first()
    val seeds = (history.take(3) + favorites.take(2)).distinctBy { it.id }.take(4)
    val listenedIds = (history + favorites).map { it.id }.toSet()

    // Cold start: until listening history exists, start with an India-wide discovery feed.
    if (seeds.isEmpty()) {
        return@coroutineScope fetchSongsByCategory(
            "popular Indian songs Hindi Marathi Punjabi Tamil Telugu Kannada Malayalam"
        )
    }

    val relatedFeeds = seeds.map { seed ->
        async {
            runCatching {
                Innertube.relatedPage(NextBody(context = IndiaExploreContext, videoId = seed.id))
                    ?.getOrNull()?.songs.orEmpty()
            }.getOrDefault(emptyList())
        }
    }.awaitAll()

    val related = relatedFeeds.flatten()
        .filter { it.key !in listenedIds }
        .distinctBy(Innertube.SongItem::key)

    // When related feeds are sparse, search from the actual listened-to songs, not generic hits.
    val candidates = if (related.size >= 12) related else {
        val seedSearches = seeds.map { seed ->
            async {
                val artist = seed.artistsText.orEmpty().takeIf { it.isNotBlank() }.orEmpty()
                val query = listOf(seed.title, artist, "similar songs")
                    .filter { it.isNotBlank() }.joinToString(" ")
                fetchSongsByCategory(query)
            }
        }.awaitAll().flatten()
        (related + seedSearches).filter { it.key !in listenedIds }
            .distinctBy(Innertube.SongItem::key)
    }

    // Interleave artist buckets to prevent one singer filling the shelf.
    val artistBuckets = candidates.groupBy { song ->
        song.authors?.firstOrNull()?.name?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: song.key
    }.values.toList()
    val diversified = mutableListOf<Innertube.SongItem>()
    for (round in 0 until 3) artistBuckets.forEach { bucket -> bucket.getOrNull(round)?.let(diversified::add) }
    diversified.distinctBy(Innertube.SongItem::key).take(50)
}
private val IndiaExploreContext = Context.DefaultWeb.copy(
    client = Context.DefaultWeb.client.copy(hl = "en", gl = "IN")
)

private suspend fun fetchIndiaDirectSongShelves(): Triple<
    List<Innertube.SongItem>,
    List<Innertube.SongItem>,
    List<Innertube.SongItem>
> = coroutineScope {
    // YouTube Charts WEB_MUSIC_ANALYTICS query used for India's weekly chart.
    // The response contains both Top Videos and Trending Videos groups, plus Top Songs.
    val chartQuery =
        "chart_params_type=WEEK&perspective=CHART&flags=viral_video_chart&selected_chart=TRACKS&chart_params_id=weekly:0:0:IN"

    // Prefer official direct chart roots, but treat a tiny partial response as an
    // incomplete feed too. The chart endpoint sometimes returns only one row.
    // Merge search results to fill the shelf, while keeping chart ranking first.
    val trendingDeferred = async {
        Innertube.indiaChartSongs(chartQuery, "videos", 1, "videoViews")
    }
    val topDeferred = async {
        Innertube.indiaChartSongs(chartQuery, "trackTypes", 0, "trackViews")
    }

    // New Releases uses the same song-search endpoint and song-only filter.
    // Its query is category-specific; no album expansion or cross-category merge.
    val releasesDeferred = async {
        fetchSongsByCategory(
            "latest new release songs India 2026 Hindi Marathi Punjabi Tamil Telugu Kannada Malayalam"
        )
    }

    Triple(trendingDeferred.await(), topDeferred.await(), releasesDeferred.await())
}

