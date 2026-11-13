package it.vfsfitvnm.vimusic.ui.screens.home

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
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
import it.vfsfitvnm.vimusic.utils.forcePlayFromBeginning
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
    var discoverySongs by remember { mutableStateOf<Map<String, List<Innertube.SongItem>>>(emptyMap()) }
    var calmSongs by remember { mutableStateOf<List<Innertube.SongItem>>(emptyList()) }
    var selectedDiscoveryCategory by remember { mutableStateOf<DiscoverySubcategory?>(null) }
    var discoveryDailyToken by remember { mutableStateOf(0) }

    // Refresh only the three discovery bundles at Indian midnight so their songs
    // rotate daily without changing the other Quick Picks shelves.
    LaunchedEffect(Unit) {
        while (true) {
            val dayMillis = 86_400_000L
            val indiaOffsetMillis = 19_800_000L
            val indiaNow = System.currentTimeMillis() + indiaOffsetMillis
            delay(dayMillis - (indiaNow % dayMillis))
            discoveryDailyToken += 1
        }
    }

    // Use the existing Song Search + Related Songs roots; keep every valid result.
    LaunchedEffect(refreshToken, discoveryDailyToken) {
        discoverySongs = withContext(Dispatchers.IO) { fetchQuickPickDiscoveryCategories() }
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

    // Calm listening rail: focused searches for lo-fi, sleep and meditation.
    LaunchedEffect(refreshToken, discoveryDailyToken) {
        calmSongs = withContext(Dispatchers.IO) {
            val fetchedCalmSongs = fetchSearchAndRelatedBundle(
                listOf("lofi music", "sleep music relaxing", "meditation music instrumental", "ambient relaxing music")
            )
            personalizeAndDiversify(fetchedCalmSongs).take(40)
        }
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
            if (selectedDiscoveryCategory == null) {
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


            discoveryBundles().forEach { bundle ->
                BasicText(
                    text = bundle.title.uppercase(),
                    style = typography.s.semiBold,
                    modifier = sectionTextModifier
                )
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    lazyRowItems(items = bundle.categories, key = DiscoverySubcategory::key) { category ->
                        QuickPicksDiscoveryShelf(
                            category = category,
                            songs = discoverySongs[category.key].orEmpty(),
                            onSongClick = { song ->
                                val mediaItem = song.asMediaItem
                                binder?.stopRadio()
                                binder?.player?.forcePlay(mediaItem)
                                binder?.setupRadio(NavigationEndpoint.Endpoint.Watch(videoId = mediaItem.mediaId))
                            },
                            onViewAll = { selectedDiscoveryCategory = category },
                            onPlayBundle = {
                                val bundleMediaItems = discoverySongs[category.key]
                                    .orEmpty()
                                    .map(Innertube.SongItem::asMediaItem)
                                if (bundleMediaItems.isNotEmpty()) {
                                    binder?.stopRadio()
                                    binder?.player?.forcePlayFromBeginning(bundleMediaItems)
                                }
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

            QuickPicksSimpleSongRail(
                title = "LO-FI · SLEEP · MEDITATION",
                songs = calmSongs,
                thumbnailSizePx = songThumbnailSizePx,
                thumbnailSizeDp = songThumbnailSizeDp,
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
            else -> ShimmerHost {
                repeat(3) { TextPlaceholder(modifier = sectionTextModifier) }
                Row {
                    repeat(3) { PlaylistItemPlaceholder(thumbnailSizeDp = playlistThumbnailSizeDp, alternative = true) }
                }
            }
            }
            } else {
                val category = selectedDiscoveryCategory!!
                BasicText(
                    text = "‹  BACK TO QUICK PICKS",
                    style = typography.s.semiBold,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .clickable { selectedDiscoveryCategory = null }
                )
                Header(title = category.parentTitle.uppercase() + " · " + category.title.uppercase())
                QuickPicksSongShelf(
                    title = category.title.uppercase() + " SONGS",
                    songs = discoverySongs[category.key].orEmpty(),
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
private fun QuickPicksSimpleSongRail(
    title: String,
    songs: List<Innertube.SongItem>,
    thumbnailSizePx: Int,
    thumbnailSizeDp: androidx.compose.ui.unit.Dp,
    onSongClick: (Innertube.SongItem) -> Unit,
    onSongLongClick: (Innertube.SongItem) -> Unit
) {
    val (_, typography) = LocalAppearance.current
    BasicText(text = title, style = typography.s.semiBold,
        modifier = Modifier.padding(horizontal = 16.dp).padding(top = 24.dp, bottom = 8.dp))
    if (songs.isEmpty()) {
        ShimmerHost { repeat(4) { SongItemPlaceholder(thumbnailSizeDp = thumbnailSizeDp) } }
    } else {
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            lazyRowItems(items = songs.distinctBy(Innertube.SongItem::key), key = Innertube.SongItem::key) { song ->
                SongItem(
                    song = song,
                    thumbnailSizePx = thumbnailSizePx,
                    thumbnailSizeDp = thumbnailSizeDp,
                    modifier = Modifier.width(260.dp).combinedClickable(
                        onClick = { onSongClick(song) },
                        onLongClick = { onSongLongClick(song) }
                    )
                )
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


private data class DiscoverySubcategory(
    val key: String,
    val parentTitle: String,
    val title: String,
    val kind: String,
    val query: String
)

private data class DiscoveryBundle(
    val title: String,
    val categories: List<DiscoverySubcategory>
)

private fun discoveryBundles(): List<DiscoveryBundle> {
    val day = ((System.currentTimeMillis() + 19_800_000L) / 86_400_000L).toInt()
    fun rotate(items: List<DiscoverySubcategory>): List<DiscoverySubcategory> {
        val offset = day % items.size
        return (items.drop(offset) + items.take(offset)).take(5)
    }

    val mix = listOf(
        DiscoverySubcategory("mix_taste", "Mix for You", "For Your Taste", "personal", ""),
        DiscoverySubcategory("mix_hindi", "Mix for You", "Hindi Mix", "mix", "personalized Hindi hit songs India"),
        DiscoverySubcategory("mix_marathi", "Mix for You", "Marathi Mix", "mix", "popular Marathi songs"),
        DiscoverySubcategory("mix_punjabi", "Mix for You", "Punjabi Mix", "mix", "popular Punjabi songs"),
        DiscoverySubcategory("mix_regional", "Mix for You", "Regional Mix", "mix", "popular Tamil Telugu Kannada Malayalam songs"),
        DiscoverySubcategory("mix_romantic", "Mix for You", "Romantic Mix", "mix", "romantic Hindi Marathi songs"),
        DiscoverySubcategory("mix_chill", "Mix for You", "Chill Mix", "mix", "chill relaxing Indian songs")
    )
    val trending = listOf(
        DiscoverySubcategory("trend_india", "Trending Community", "Trending India", "trending", "trending songs India"),
        DiscoverySubcategory("trend_hindi", "Trending Community", "Hindi Viral", "trending", "viral trending Hindi songs"),
        DiscoverySubcategory("trend_marathi", "Trending Community", "Marathi Viral", "trending", "viral trending Marathi songs"),
        DiscoverySubcategory("trend_reels", "Trending Community", "Reels Hits", "trending", "Instagram reels trending songs India"),
        DiscoverySubcategory("trend_pop", "Trending Community", "Pop Trending", "trending", "trending Indian pop songs"),
        DiscoverySubcategory("trend_regional", "Trending Community", "Regional Trending", "trending", "trending Tamil Telugu Punjabi songs"),
        DiscoverySubcategory("trend_new", "Trending Community", "Fresh Trending", "trending", "latest trending new songs India")
    )
    val together = listOf(
        DiscoverySubcategory("together_party", "Listen Together", "Party Mix", "together", "party songs friends India"),
        DiscoverySubcategory("together_duet", "Listen Together", "Duet & Sing Along", "together", "popular duet sing along songs Hindi Marathi"),
        DiscoverySubcategory("together_romance", "Listen Together", "Romantic Duets", "together", "romantic duet songs India"),
        DiscoverySubcategory("together_chill", "Listen Together", "Chill Together", "together", "chill songs to listen with friends"),
        DiscoverySubcategory("together_classics", "Listen Together", "Evergreen Together", "together", "evergreen group sing along songs India"),
        DiscoverySubcategory("together_dance", "Listen Together", "Dance Together", "together", "dance party songs Hindi Punjabi"),
        DiscoverySubcategory("together_friends", "Listen Together", "Friends' Favorites", "together", "popular friendship songs India")
    )
    return listOf(
        DiscoveryBundle("Mix for You", rotate(mix)),
        DiscoveryBundle("Trending Community", rotate(trending)),
        DiscoveryBundle("Listen Together", rotate(together))
    )
}

@Composable
private fun QuickPicksDiscoveryShelf(
    category: DiscoverySubcategory,
    songs: List<Innertube.SongItem>,
    onSongClick: (Innertube.SongItem) -> Unit,
    onViewAll: () -> Unit,
    onPlayBundle: () -> Unit,
    onSongLongClick: (Innertube.SongItem) -> Unit
) {
    val (_, typography) = LocalAppearance.current
    val firstSong = songs.firstOrNull()

    Column(
        modifier = Modifier
            .width(276.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF29231B))
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(128.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF40372C))
                .clickable { firstSong?.let(onSongClick) }
        ) {
            AsyncImage(
                model = firstSong?.thumbnail?.size(320),
                contentDescription = category.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.48f))
                    .padding(14.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Bottom
            ) {
                BasicText(text = category.title, style = typography.s.semiBold)
                BasicText(
                    text = "▶  PLAY BUNDLE",
                    style = typography.xs.semiBold,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.18f))
                        .clickable(onClick = onPlayBundle)
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }

        if (songs.isEmpty()) {
            ShimmerHost { repeat(3) { SongItemPlaceholder(thumbnailSizeDp = 64.dp) } }
        } else {
            songs.take(3).forEach { song ->
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

        BasicText(
            text = "VIEW ALL  ›",
            style = typography.xs.semiBold,
            modifier = Modifier
                .align(Alignment.End)
                .padding(top = 8.dp, end = 4.dp, bottom = 2.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.14f))
                .clickable(onClick = onViewAll)
                .padding(horizontal = 14.dp, vertical = 9.dp)
        )
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

private suspend fun fetchQuickPickDiscoveryCategories(): Map<String, List<Innertube.SongItem>> = coroutineScope {
    discoveryBundles().flatMap { it.categories }.map { category ->
        async {
            // Every card uses both roots: category Song Search -> Related Songs.
            // For the taste card, merge those candidates with listening-history recommendations.
            val categoryFeed = if (category.kind == "personal") {
                fetchSearchAndRelatedBundle(
                    listOf("popular Indian songs Hindi Marathi Punjabi Tamil Telugu Kannada Malayalam")
                )
            } else {
                fetchSearchAndRelatedBundle(listOf(category.query))
            }
            val historyFeed = if (category.kind == "personal" || category.kind == "mix") {
                fetchPersonalizedSongsForYou()
            } else {
                emptyList()
            }
            val songs = (categoryFeed + historyFeed).distinctBy(Innertube.SongItem::key)
            category.key to rotateDiscoverySongsDaily(personalizeAndDiversify(songs))
        }
    }.awaitAll().toMap()
}

/**
 * YouTube Music-inspired Mix for You:
 * 1) Song Search first limits candidates to the requested language/mood/genre.
 * 2) Existing listening history and favourites personalize the ordering.
 * 3) Related Songs expands only category-search seeds, so the shelf stays on-topic.
 * No third fetch root is introduced.
 */
private suspend fun fetchPersonalizedMixCategory(query: String): List<Innertube.SongItem> {
    val categoryCandidates = fetchSearchAndRelatedBundle(listOf(query))
    val historyCandidates = fetchPersonalizedSongsForYou()
    return personalizeAndDiversify((categoryCandidates + historyCandidates).distinctBy(Innertube.SongItem::key))
}

private suspend fun fetchSearchAndRelatedBundle(queries: List<String>): List<Innertube.SongItem> = coroutineScope {
    // Retry empty shelves with a simpler query using the same Song Search root.
    val searchResults = queries.map { query ->
        async { fetchSongsByCategoryWithFallback(query) }
    }.awaitAll().flatten().distinctBy(Innertube.SongItem::key)

    val relatedResults = searchResults.take(5).map { seed ->
        async {
            runCatching {
                Innertube.relatedPage(
                    NextBody(context = IndiaExploreContext, videoId = seed.key)
                )?.getOrNull()?.songs.orEmpty()
            }.getOrDefault(emptyList())
        }
    }.awaitAll().flatten()

    (searchResults + relatedResults).distinctBy(Innertube.SongItem::key)
}

private suspend fun personalizeAndDiversify(
    candidates: List<Innertube.SongItem>
): List<Innertube.SongItem> {
    if (candidates.isEmpty()) return emptyList()
    val personalized = runCatching {
        personalizeSongLists(listOf(candidates)).firstOrNull().orEmpty()
    }.getOrDefault(candidates)
    val rank = personalized.distinctBy(Innertube.SongItem::key)
        .mapIndexed { index, song -> song.key to index }.toMap()
    val ranked = candidates.distinctBy(Innertube.SongItem::key)
        .sortedBy { rank[it.key] ?: Int.MAX_VALUE }

    val selected = mutableListOf<Innertube.SongItem>()
    val remaining = ranked.toMutableList()
    val artists = mutableSetOf<String>()
    while (remaining.isNotEmpty()) {
        val index = remaining.indexOfFirst { song ->
            val artist = song.authors?.firstOrNull()?.name?.trim()?.lowercase().orEmpty()
            artist.isBlank() || artist !in artists
        }.let { if (it < 0) 0 else it }
        val song = remaining.removeAt(index)
        selected += song
        song.authors?.firstOrNull()?.name?.trim()?.lowercase()
            ?.takeIf { it.isNotBlank() }?.let(artists::add)
    }
    return selected
}

private fun rotateDiscoverySongsDaily(
    songs: List<Innertube.SongItem>
): List<Innertube.SongItem> {
    if (songs.size < 2) return songs
    val indiaDay = (System.currentTimeMillis() + 19_800_000L) / 86_400_000L
    val offset = (indiaDay % songs.size).toInt()
    return songs.drop(offset) + songs.take(offset)
}

private suspend fun fetchSongsByCategoryWithFallback(query: String): List<Innertube.SongItem> {
    val cleaned = query
        .replace(Regex("\\b(popular|trending|viral|latest|new|India|Indian|songs|song)\\b", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    val attempts = listOf(query, cleaned.takeIf { it.isNotBlank() }?.let { "$it songs" })
        .filterNotNull().distinct()
    val collected = mutableListOf<Innertube.SongItem>()
    for (attempt in attempts) {
        collected += runCatching { fetchSongsByCategory(attempt) }.getOrDefault(emptyList())
        if (collected.distinctBy(Innertube.SongItem::key).size >= 8) break
    }
    return collected.distinctBy(Innertube.SongItem::key)
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

