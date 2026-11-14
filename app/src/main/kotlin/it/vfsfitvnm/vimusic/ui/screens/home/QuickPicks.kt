package it.vfsfitvnm.vimusic.ui.screens.home

import androidx.activity.compose.BackHandler

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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.snapshotFlow
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
import it.vfsfitvnm.innertube.models.bodies.BrowseBody
import it.vfsfitvnm.innertube.models.bodies.ContinuationBody
import it.vfsfitvnm.innertube.models.bodies.NextBody
import it.vfsfitvnm.innertube.models.NavigationEndpoint
import it.vfsfitvnm.innertube.requests.indiaChartSongs
import it.vfsfitvnm.innertube.requests.searchPage
import it.vfsfitvnm.innertube.requests.playlistPage
import it.vfsfitvnm.innertube.requests.searchPodcastPage
import it.vfsfitvnm.innertube.requests.relatedPage
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
import it.vfsfitvnm.vimusic.ui.screens.browse.personalizeSongLists
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope


private suspend fun fetchQuickPicksPodcastSongs(
    batchIndex: Int
): List<Innertube.SongItem> = coroutineScope {
    // Keep the two existing podcast discovery roots, but search only the
    // single broad term requested: "podcast". No extra topic keywords.
    val context = Context.DefaultWeb.copy(
        client = Context.DefaultWeb.client.copy(hl = "en", gl = "IN")
    )
    suspend fun searchRoot(): List<Innertube.PlaylistItem> =
        runCatching {
            Innertube.searchPodcastPage(
                SearchBody(
                    context = context,
                    query = "podcast",
                    params = Innertube.SearchFilter.Podcast.value
                )
            )?.getOrNull().orEmpty()
        }.getOrDefault(emptyList())

    // Search both original roots with the same broad Podcast query.
    val showsFromBothRoots = awaitAll(
        async(Dispatchers.IO) { searchRoot() },
        async(Dispatchers.IO) { searchRoot() }
    ).flatten().distinctBy(Innertube.PlaylistItem::key)

    // Fetch each returned show's complete episode feed, following continuation
    // tokens until the source has no more pages. Never apply song filters here.
    val episodeFeeds = showsFromBothRoots.map { show ->
        async(Dispatchers.IO) {
            runCatching {
                val episodes = mutableListOf<Innertube.SongItem>()
                var page = Innertube.playlistPage(
                    BrowseBody(context = context, browseId = show.key)
                )?.getOrNull()?.songsPage
                val seenContinuations = mutableSetOf<String>()
                while (page != null) {
                    episodes += page.items.orEmpty()
                    val token = page.continuation?.takeIf { it.isNotBlank() } ?: break
                    if (!seenContinuations.add(token)) break
                    page = Innertube.playlistPage(
                        ContinuationBody(context = context, continuation = token)
                    )?.getOrNull()?.let { continuationPage ->
                        Innertube.ItemsPage(
                            items = continuationPage.items,
                            continuation = continuationPage.continuation
                        )
                    }
                }
                episodes.distinctBy(Innertube.SongItem::key)
            }.getOrDefault(emptyList())
        }
    }.awaitAll().flatten()

    episodeFeeds.distinctBy(Innertube.SongItem::key)
}
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
    var completedRefreshTasks by remember { mutableStateOf(emptySet<String>()) }
    fun markRefreshTaskComplete(task: String) {
        val completed = completedRefreshTasks + task
        completedRefreshTasks = completed
        if (completed.containsAll(setOf("discovery", "calm", "podcast", "long_listen", "songs"))) {
            isRefreshing = false
        }
    }
    var songsForYou by remember { mutableStateOf<List<Innertube.SongItem>>(emptyList()) }
    var isLoadingSongsForYou by remember { mutableStateOf(true) }
    var trendingSongs by remember { mutableStateOf<List<Innertube.SongItem>>(emptyList()) }
    var topSongs by remember { mutableStateOf<List<Innertube.SongItem>>(emptyList()) }
    var newReleases by remember { mutableStateOf<List<Innertube.SongItem>>(emptyList()) }
    var discoverySongs by remember { mutableStateOf<Map<String, List<Innertube.SongItem>>>(emptyMap()) }
    var calmCategorySongs by remember { mutableStateOf<Map<String, List<Innertube.SongItem>>>(emptyMap()) }
    var podcastSongs by remember { mutableStateOf<List<Innertube.SongItem>>(emptyList()) }
    var isLoadingPodcasts by remember { mutableStateOf(true) }
    var podcastBatchIndex by remember { mutableStateOf(0) }
    var isLoadingMorePodcasts by remember { mutableStateOf(false) }
    var emptyPodcastBatches by remember { mutableStateOf(0) }
    var hasMorePodcastResults by remember { mutableStateOf(true) }
    var longListenSongs by remember { mutableStateOf<List<Innertube.SongItem>>(emptyList()) }
    var isLoadingLongListen by remember { mutableStateOf(true) }
    var selectedDiscoveryCategory by remember { mutableStateOf<DiscoverySubcategory?>(null) }
    var isLoadingMoreDiscovery by remember { mutableStateOf(false) }
    var discoveryBatchIndex by remember { mutableStateOf(0) }
    var emptyDiscoveryBatches by remember { mutableStateOf(0) }
    var hasMoreDiscoveryResults by remember { mutableStateOf(true) }
    var selectedPodcastPage by remember { mutableStateOf(false) }
    var selectedQuickPicksShelf by remember { mutableStateOf<String?>(null) }
    var pendingShelfLoad by remember { mutableStateOf<String?>(null) }
    var loadingShelfKeys by remember { mutableStateOf(setOf<String>()) }
    var shelfBatchIndexes by remember { mutableStateOf(emptyMap<String, Int>()) }

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

    // Publish each category as soon as its filtered Song Search results arrive.
    // Related-song enrichment is added afterward; category filters stay unchanged.
    LaunchedEffect(refreshToken, discoveryDailyToken) {
        discoverySongs = emptyMap()
        withContext(Dispatchers.IO) {
            fetchQuickPickDiscoveryCategories(refreshToken + discoveryDailyToken) { key, songs ->
                withContext(Dispatchers.Main.immediate) {
                    discoverySongs = discoverySongs + (key to songs)
                }
            }
        }
        markRefreshTaskComplete("discovery")
    }

    // Calm category cards stay in a fixed order; only their song lists rotate daily.
    LaunchedEffect(refreshToken, discoveryDailyToken) {
        calmCategorySongs = withContext(Dispatchers.IO) { fetchCalmMusicCategories(refreshToken + discoveryDailyToken) }
        markRefreshTaskComplete("calm")
    }

    // Load the first podcast batch on refresh; more batches are fetched on scroll.
    LaunchedEffect(refreshToken, discoveryDailyToken) {
        isLoadingPodcasts = true
        podcastBatchIndex = 0
        emptyPodcastBatches = 0
        isLoadingMorePodcasts = false
        hasMorePodcastResults = true
        var loadedPodcasts = emptyList<Innertube.SongItem>()
        var lastPodcastBatch = 0
        withContext(Dispatchers.IO) {
            // Load one batch immediately; the View All page fetches later batches on scroll.
            for (batch in 0 until 1) {
                lastPodcastBatch = batch
                val next = runCatching { fetchQuickPicksPodcastSongs(batch + refreshToken + discoveryDailyToken) }.getOrDefault(emptyList())
                loadedPodcasts = (loadedPodcasts + next).distinctBy(Innertube.SongItem::key)
                if (loadedPodcasts.size >= 15) break
            }
        }
        podcastSongs = loadedPodcasts
        podcastBatchIndex = lastPodcastBatch
        isLoadingPodcasts = false
        markRefreshTaskComplete("podcast")
    }


    // Long Listen refreshes on pull-to-refresh and automatically at Indian midnight.
    LaunchedEffect(refreshToken, discoveryDailyToken) {
        isLoadingLongListen = true
        var loadedLongListen = emptyList<Innertube.SongItem>()
        try {
            withContext(Dispatchers.IO) {
                for (batch in 0 until 1) {
                    val next = runCatching { fetchLongListenSongs(batch + refreshToken * 2) }.getOrDefault(emptyList())
                    loadedLongListen = (loadedLongListen + next).distinctBy(Innertube.SongItem::key)
                    if (loadedLongListen.size >= 15) break
                }
            }
            longListenSongs = loadedLongListen
        } finally {
            isLoadingLongListen = false
            markRefreshTaskComplete("long_listen")
        }
    }

    // Each top-level shelf paints independently from its own first search/chart
    // response. Related-song enrichment runs afterward without blocking first paint.
    LaunchedEffect(refreshToken) {
        isLoadingSongsForYou = true
        trendingSongs = emptyList()
        topSongs = emptyList()
        newReleases = emptyList()
        coroutineScope {
            val forYouJob = launch {
                val firstPaint = withContext(Dispatchers.IO) {
                    fetchPersonalizedSongsForYouFirstPaint(refreshToken)
                }
                songsForYou = firstPaint.distinctBy(Innertube.SongItem::key)
                isLoadingSongsForYou = false

                // Continue with the complete history/related/search pipeline after
                // the first personalized candidates are already visible.
                val recommendations = withContext(Dispatchers.IO) {
                    fetchPersonalizedSongsForYou(refreshToken)
                }
                songsForYou = recommendations.distinctBy(Innertube.SongItem::key)
            }
            val chartShelvesJob = launch {
                withContext(Dispatchers.IO) {
                    fetchIndiaDirectSongShelvesProgressive(refreshToken) { shelfKey, songs ->
                        withContext(Dispatchers.Main.immediate) {
                            when (shelfKey) {
                                "trending" -> trendingSongs = songs
                                "top" -> topSongs = songs
                                "new" -> newReleases = songs
                            }
                        }
                    }
                }
            }
            forYouJob.join()
            chartShelvesJob.join()
        }
        markRefreshTaskComplete("songs")
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
    val scope = rememberCoroutineScope()
    val longListenLazyListState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Keep one pagination collector alive while the Podcast page is open.
    // Do not key this effect on scrollState.value: doing so cancels an in-flight
    // network request on every scroll event and can leave the loading flag stuck.
    LaunchedEffect(selectedPodcastPage) {
        if (!selectedPodcastPage) return@LaunchedEffect

        snapshotFlow {
            scrollState.value >= (scrollState.maxValue - 500).coerceAtLeast(0)
        }.distinctUntilChanged().collect { nearBottom ->
            if (nearBottom) {
                // If the current list is shorter than the screen, keep requesting
                // batches until it can scroll or the source is genuinely exhausted.
                while (
                    selectedPodcastPage &&
                    hasMorePodcastResults &&
                    !isLoadingPodcasts &&
                    !isLoadingMorePodcasts &&
                    scrollState.value >= (scrollState.maxValue - 500).coerceAtLeast(0)
                ) {
                    isLoadingMorePodcasts = true
                    try {
                        val nextBatch = podcastBatchIndex + 1
                        val moreSongs = withContext(Dispatchers.IO) {
                            runCatching { fetchQuickPicksPodcastSongs(nextBatch + refreshToken + discoveryDailyToken) }
                                .getOrDefault(emptyList())
                        }
                        val existingKeys = podcastSongs.map(Innertube.SongItem::key).toHashSet()
                        val uniqueMore = moreSongs.filter { it.key !in existingKeys }
                        if (uniqueMore.isNotEmpty()) {
                            podcastSongs = podcastSongs + uniqueMore
                            emptyPodcastBatches = 0
                        } else {
                            emptyPodcastBatches += 1
                            if (emptyPodcastBatches >= 6) hasMorePodcastResults = false
                        }
                        podcastBatchIndex = nextBatch
                    } finally {
                        isLoadingMorePodcasts = false
                    }
                }
            }
        }
    }
    // Built-in shelf detail pages keep loading batches as the vertical list is scrolled.
    LaunchedEffect(selectedQuickPicksShelf) {
        val shelfKey = selectedQuickPicksShelf ?: return@LaunchedEffect
        snapshotFlow {
            scrollState.value >= (scrollState.maxValue - 500).coerceAtLeast(0)
        }.distinctUntilChanged().collect { nearBottom ->
            if (nearBottom && selectedQuickPicksShelf == shelfKey && pendingShelfLoad == null && shelfKey !in loadingShelfKeys) {
                pendingShelfLoad = shelfKey
            }
        }
    }

    // Category detail pages also need continuous pagination, not a fixed first batch.
    // Keep this collector stable while the page is open so scroll events never cancel fetches.
    LaunchedEffect(selectedDiscoveryCategory) {
        val category = selectedDiscoveryCategory ?: return@LaunchedEffect
        discoveryBatchIndex = 0
        emptyDiscoveryBatches = 0
        hasMoreDiscoveryResults = true
        isLoadingMoreDiscovery = false

        snapshotFlow {
            scrollState.value >= (scrollState.maxValue - 500).coerceAtLeast(0)
        }.distinctUntilChanged().collect { nearBottom ->
            if (nearBottom) {
                while (
                    selectedDiscoveryCategory?.key == category.key &&
                    hasMoreDiscoveryResults &&
                    !isLoadingMoreDiscovery &&
                    scrollState.value >= (scrollState.maxValue - 500).coerceAtLeast(0)
                ) {
                    isLoadingMoreDiscovery = true
                    try {
                        val currentSongs = discoverySongs[category.key]
                            ?: calmCategorySongs[category.key].orEmpty()
                        val nextBatch = discoveryBatchIndex + 1
                        val moreSongs = withContext(Dispatchers.IO) {
                            runCatching {
                                fetchDiscoveryCategoryMore(category, currentSongs, nextBatch)
                            }.getOrDefault(emptyList())
                        }
                        val existingKeys = currentSongs.map(Innertube.SongItem::key).toHashSet()
                        val uniqueMore = moreSongs.filter { it.key !in existingKeys }
                        if (uniqueMore.isNotEmpty()) {
                            if (category.key.startsWith("calm_")) {
                                calmCategorySongs = calmCategorySongs +
                                    (category.key to (currentSongs + uniqueMore))
                            } else {
                                discoverySongs = discoverySongs +
                                    (category.key to (currentSongs + uniqueMore))
                            }
                            emptyDiscoveryBatches = 0
                        } else {
                            emptyDiscoveryBatches += 1
                            // This is an exhaustion guard, not a song-count cap.
                            // Different seed groups and rotated category searches are tried first.
                            if (emptyDiscoveryBatches >= 8) hasMoreDiscoveryResults = false
                        }
                        discoveryBatchIndex = nextBatch
                    } finally {
                        isLoadingMoreDiscovery = false
                    }
                }
            }
        }
    }

    // Fetch another batch when a non-card music shelf reaches its end.
    LaunchedEffect(pendingShelfLoad) {
        val shelfKey = pendingShelfLoad ?: return@LaunchedEffect
        if (shelfKey in loadingShelfKeys) return@LaunchedEffect
        loadingShelfKeys = loadingShelfKeys + shelfKey
        try {
            val current = when (shelfKey) {
                "for_you" -> songsForYou
                "trending" -> trendingSongs
                "top" -> topSongs
                "new" -> newReleases
                "long_listen" -> longListenSongs
                else -> emptyList()
            }
            val nextBatch = (shelfBatchIndexes[shelfKey] ?: 0) + 1
            val more = withContext(Dispatchers.IO) { fetchMoreQuickPicksShelf(shelfKey, current, nextBatch) }
            val existing = current.map(Innertube.SongItem::key).toHashSet()
            val unique = more.filter { it.key !in existing }
            if (unique.isNotEmpty()) {
                when (shelfKey) {
                    "for_you" -> songsForYou = songsForYou + unique
                    "trending" -> trendingSongs = trendingSongs + unique
                    "top" -> topSongs = topSongs + unique
                    "new" -> newReleases = newReleases + unique
                    "long_listen" -> longListenSongs = longListenSongs + unique
                }
            }
            shelfBatchIndexes = shelfBatchIndexes + (shelfKey to nextBatch)
        } finally {
            loadingShelfKeys = loadingShelfKeys - shelfKey
            pendingShelfLoad = null
        }
    }

    var savedHomeScrollOffset by remember { mutableStateOf(0) }
    var restoreHomeScroll by remember { mutableStateOf(false) }

    // Keep the opened category at the top, then restore the exact Home scroll
    // position when Back returns from that category.
    BackHandler(enabled = selectedDiscoveryCategory != null || selectedPodcastPage || selectedQuickPicksShelf != null) {
        if (selectedQuickPicksShelf != null) {
            selectedQuickPicksShelf = null
            restoreHomeScroll = true
        } else if (selectedPodcastPage) {
            selectedPodcastPage = false
            restoreHomeScroll = true
        } else {
            restoreHomeScroll = true
            selectedDiscoveryCategory = null
        }
    }
    LaunchedEffect(selectedDiscoveryCategory, selectedPodcastPage, selectedQuickPicksShelf) {
        if (selectedDiscoveryCategory != null || selectedPodcastPage || selectedQuickPicksShelf != null) {
            scrollState.scrollTo(0)
        } else if (restoreHomeScroll) {
            scrollState.scrollTo(savedHomeScrollOffset)
            restoreHomeScroll = false
        }
    }

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
                completedRefreshTasks = emptySet()
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
            if (selectedDiscoveryCategory == null && !selectedPodcastPage && selectedQuickPicksShelf == null) {
            Header(
                title = "QUICK PICKS",
                modifier = Modifier
                    .padding(endPaddingValues)
            )



            QuickPicksSectionHeader(
                title = "SONGS FOR YOU",
                modifier = sectionTextModifier,
                onPlay = {
                    val items = songsForYou.take(15).map(Innertube.SongItem::asMediaItem)
                    if (items.isNotEmpty()) { binder?.stopRadio(); binder?.player?.forcePlayFromBeginning(items) }
                },
                onViewAll = { savedHomeScrollOffset = scrollState.value; restoreHomeScroll = false; selectedQuickPicksShelf = "for_you" }
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
                    items(items = songsForYou.distinctBy { it.key }.take(15), key = Innertube.SongItem::key) { song ->
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
            } else if (isLoadingSongsForYou) {
                ShimmerHost {
                    repeat(5) { SongItemPlaceholder(thumbnailSizeDp = songThumbnailSizeDp) }
                }
            } else {
                BasicText(
                    text = "Play a few songs or like tracks to build your personal mix. Pull down to retry.",
                    style = typography.xs.secondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            QuickPicksSongShelf(
                title = "TRENDING SONGS",
                songs = trendingSongs,
                onPlay = { val items = trendingSongs.take(15).map(Innertube.SongItem::asMediaItem); if (items.isNotEmpty()) { binder?.stopRadio(); binder?.player?.forcePlayFromBeginning(items) } },
                onViewAll = { savedHomeScrollOffset = scrollState.value; restoreHomeScroll = false; selectedQuickPicksShelf = "trending" },
                thumbnailSizePx = songThumbnailSizePx,
                thumbnailSizeDp = songThumbnailSizeDp,
                itemWidth = itemInHorizontalGridWidth,
                endPaddingValues = endPaddingValues,
                verticalList = false,
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
                onPlay = { val items = topSongs.take(15).map(Innertube.SongItem::asMediaItem); if (items.isNotEmpty()) { binder?.stopRadio(); binder?.player?.forcePlayFromBeginning(items) } },
                onViewAll = { savedHomeScrollOffset = scrollState.value; restoreHomeScroll = false; selectedQuickPicksShelf = "top" },
                thumbnailSizePx = songThumbnailSizePx,
                thumbnailSizeDp = songThumbnailSizeDp,
                itemWidth = itemInHorizontalGridWidth,
                endPaddingValues = endPaddingValues,
                verticalList = false,
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
                onPlay = { val items = newReleases.take(15).map(Innertube.SongItem::asMediaItem); if (items.isNotEmpty()) { binder?.stopRadio(); binder?.player?.forcePlayFromBeginning(items) } },
                onViewAll = { savedHomeScrollOffset = scrollState.value; restoreHomeScroll = false; selectedQuickPicksShelf = "new" },
                thumbnailSizePx = songThumbnailSizePx,
                thumbnailSizeDp = songThumbnailSizeDp,
                itemWidth = itemInHorizontalGridWidth,
                endPaddingValues = endPaddingValues,
                verticalList = false,
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
                            refreshToken = refreshToken + discoveryDailyToken,
                            onSongClick = { song ->
                                val mediaItem = song.asMediaItem
                                binder?.stopRadio()
                                binder?.player?.forcePlay(mediaItem)
                                binder?.setupRadio(NavigationEndpoint.Endpoint.Watch(videoId = mediaItem.mediaId))
                            },
                            onViewAll = { savedHomeScrollOffset = scrollState.value; restoreHomeScroll = false; selectedDiscoveryCategory = category },
                            onPlayBundle = {
                                val bundleMediaItems = (discoverySongs[category.key] ?: calmCategorySongs[category.key]).orEmpty()
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

            BasicText(
                text = "CALM",
                style = typography.s.semiBold,
                modifier = sectionTextModifier
            )
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                lazyRowItems(items = calmMusicCategories(), key = DiscoverySubcategory::key) { category ->
                    QuickPicksDiscoveryShelf(
                        category = category,
                        songs = calmCategorySongs[category.key].orEmpty(),
                        refreshToken = refreshToken + discoveryDailyToken,
                        onSongClick = { song ->
                            val mediaItem = song.asMediaItem
                            binder?.stopRadio()
                            binder?.player?.forcePlay(mediaItem)
                            binder?.setupRadio(NavigationEndpoint.Endpoint.Watch(videoId = mediaItem.mediaId))
                        },
                        onViewAll = { savedHomeScrollOffset = scrollState.value; restoreHomeScroll = false; selectedDiscoveryCategory = category },
                        onPlayBundle = {
                            val mediaItems = calmCategorySongs[category.key].orEmpty()
                                .map(Innertube.SongItem::asMediaItem)
                            if (mediaItems.isNotEmpty()) {
                                binder?.stopRadio()
                                binder?.player?.forcePlayFromBeginning(mediaItems)
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

            QuickPicksPodcastShelf(
                songs = podcastSongs,
                isLoading = isLoadingPodcasts,
                onViewAll = {
                    savedHomeScrollOffset = scrollState.value
                    selectedPodcastPage = true
                },
                onSongClick = { song ->
                    val mediaItem = song.asMediaItem
                    binder?.stopRadio()
                    binder?.player?.forcePlay(mediaItem)
                    binder?.setupRadio(NavigationEndpoint.Endpoint.Watch(videoId = mediaItem.mediaId))
                },
                onPlayBundle = {
                    val mediaItems = podcastSongs.map(Innertube.SongItem::asMediaItem)
                    if (mediaItems.isNotEmpty()) {
                        binder?.stopRadio()
                        binder?.player?.forcePlayFromBeginning(mediaItems)
                    }
                },
                onSongLongClick = { song ->
                    menuState.display {
                        NonQueuedMediaItemMenu(onDismiss = menuState::hide, mediaItem = song.asMediaItem)
                    }
                }
            )

            QuickPicksSectionHeader(
                title = "LONG LISTEN",
                modifier = sectionTextModifier,
                onPlay = { val items = longListenSongs.take(15).map(Innertube.SongItem::asMediaItem); if (items.isNotEmpty()) { binder?.stopRadio(); binder?.player?.forcePlayFromBeginning(items) } },
                onViewAll = { savedHomeScrollOffset = scrollState.value; restoreHomeScroll = false; selectedQuickPicksShelf = "long_listen" }
            )
            if (longListenSongs.isEmpty() && isLoadingLongListen) {
                ShimmerHost {
                    repeat(3) { SongItemPlaceholder(thumbnailSizeDp = 64.dp) }
                }
            } else if (longListenSongs.isEmpty()) {
                BasicText(
                    text = "Finding long-form music. Pull down to retry.",
                    style = typography.xs.secondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            } else {
                LazyRow(
                    state = longListenLazyListState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    lazyRowItems(
                        items = longListenSongs.take(15).chunked(5),
                        key = { column -> column.firstOrNull()?.key.orEmpty() }
                    ) { songColumn ->
                        Column(
                            modifier = Modifier.width(itemInHorizontalGridWidth),
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            songColumn.forEach { song ->
                                SongItem(
                                    song = song,
                                    thumbnailSizePx = 64.dp.px,
                                    thumbnailSizeDp = 64.dp,
                                    modifier = Modifier.fillMaxWidth().combinedClickable(
                                        onClick = {
                                            val mediaItem = song.asMediaItem
                                            binder?.stopRadio()
                                            binder?.player?.forcePlay(mediaItem)
                                            binder?.setupRadio(NavigationEndpoint.Endpoint.Watch(videoId = mediaItem.mediaId))
                                        },
                                        onLongClick = {
                                            menuState.display {
                                                NonQueuedMediaItemMenu(
                                                    onDismiss = menuState::hide,
                                                    mediaItem = song.asMediaItem
                                                )
                                            }
                                        }
                                    )
                                )
                            }
                        }
                    }
                }
            }

            } else if (selectedQuickPicksShelf != null) {
                val shelfKey = selectedQuickPicksShelf!!
                val shelfTitle = when (shelfKey) {
                    "for_you" -> "SONGS FOR YOU"
                    "trending" -> "TRENDING SONGS"
                    "top" -> "TOP SONGS"
                    "new" -> "NEW RELEASES"
                    else -> "LONG LISTEN"
                }
                val shelfSongs = when (shelfKey) {
                    "for_you" -> songsForYou
                    "trending" -> trendingSongs
                    "top" -> topSongs
                    "new" -> newReleases
                    else -> longListenSongs
                }
                BasicText(
                    text = "‹  BACK TO QUICK PICKS",
                    style = typography.s.semiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        .clickable { selectedQuickPicksShelf = null; restoreHomeScroll = true }
                )
                Header(title = shelfTitle)
                QuickPicksSongShelf(
                    title = shelfTitle + " · ALL SONGS",
                    songs = shelfSongs,
                    thumbnailSizePx = songThumbnailSizePx,
                    thumbnailSizeDp = songThumbnailSizeDp,
                    itemWidth = itemInHorizontalGridWidth,
                    endPaddingValues = endPaddingValues,
                    verticalList = true,
                    onSongClick = { song ->
                        val mediaItem = song.asMediaItem
                        binder?.stopRadio()
                        binder?.player?.forcePlay(mediaItem)
                        binder?.setupRadio(NavigationEndpoint.Endpoint.Watch(videoId = mediaItem.mediaId))
                    },
                    onSongLongClick = { song ->
                        menuState.display { NonQueuedMediaItemMenu(onDismiss = menuState::hide, mediaItem = song.asMediaItem) }
                    }
                )
                if (shelfKey in loadingShelfKeys || pendingShelfLoad == shelfKey) {
                    ShimmerHost { repeat(2) { SongItemPlaceholder(thumbnailSizeDp = songThumbnailSizeDp) } }
                }
            } else if (selectedPodcastPage) {
                BasicText(
                    text = "‹  BACK TO QUICK PICKS",
                    style = typography.s.semiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        .clickable { selectedPodcastPage = false; restoreHomeScroll = true }
                )
                Header(title = "PODCASTS")
                QuickPicksSongShelf(
                    title = "ALL PODCAST EPISODES",
                    songs = podcastSongs,
                    thumbnailSizePx = songThumbnailSizePx,
                    thumbnailSizeDp = songThumbnailSizeDp,
                    itemWidth = itemInHorizontalGridWidth,
                    endPaddingValues = endPaddingValues,
                    verticalList = true,
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
                if (isLoadingMorePodcasts) {
                    ShimmerHost { repeat(2) { SongItemPlaceholder(thumbnailSizeDp = songThumbnailSizeDp) } }
                }
            } else {
                val category = selectedDiscoveryCategory!!
                BasicText(
                    text = "‹  BACK TO QUICK PICKS",
                    style = typography.s.semiBold,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .clickable { restoreHomeScroll = true; selectedDiscoveryCategory = null }
                )
                Header(title = category.parentTitle.uppercase() + " · " + category.title.uppercase())
                QuickPicksSongShelf(
                    title = category.title.uppercase() + " SONGS",
                    songs = discoverySongs[category.key] ?: calmCategorySongs[category.key].orEmpty(),
                    thumbnailSizePx = songThumbnailSizePx,
                    thumbnailSizeDp = songThumbnailSizeDp,
                    itemWidth = itemInHorizontalGridWidth,
                    endPaddingValues = endPaddingValues,
                    verticalList = true,
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
                if (isLoadingMoreDiscovery) {
                    ShimmerHost { repeat(2) { SongItemPlaceholder(thumbnailSizeDp = songThumbnailSizeDp) } }
                }
            }
        }
        if (scrollState.value > 300) {
            BasicText(
                text = "↑",
                style = typography.s.semiBold.copy(color = Color.White),
                modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp)
                    .clip(RoundedCornerShape(50)).background(Color(0xFF343434))
                    .clickable { scope.launch { scrollState.animateScrollTo(0) } }
                    .padding(horizontal = 15.dp, vertical = 10.dp)
            )
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
private fun QuickPicksSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    onPlay: () -> Unit,
    onViewAll: () -> Unit
) {
    val (_, typography) = LocalAppearance.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
    ) {
        BasicText(text = title, style = typography.s.semiBold, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(text = "PLAY", style = typography.xs.semiBold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.14f))
                    .clickable(onClick = onPlay).padding(horizontal = 9.dp, vertical = 7.dp))
            BasicText(text = "VIEW ALL", style = typography.xs.semiBold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.14f))
                    .clickable(onClick = onViewAll).padding(horizontal = 9.dp, vertical = 7.dp))
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
    verticalList: Boolean,
    onLoadMore: (() -> Unit)? = null,
    onPlay: (() -> Unit)? = null,
    onViewAll: (() -> Unit)? = null,
    onSongClick: (Innertube.SongItem) -> Unit,
    onSongLongClick: (Innertube.SongItem) -> Unit
) {
    val (_, typography) = LocalAppearance.current
    val shelfGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    LaunchedEffect(shelfGridState, songs.size) {
        snapshotFlow {
            val info = shelfGridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 6
        }.distinctUntilChanged().collect { nearEnd ->
            if (nearEnd && songs.isNotEmpty()) onLoadMore?.invoke()
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 24.dp, bottom = 8.dp).padding(endPaddingValues),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
    ) {
        BasicText(text = title, style = typography.s.semiBold, modifier = Modifier.weight(1f))
        if (onPlay != null || onViewAll != null) {
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                onPlay?.let { action ->
                    BasicText(text = "PLAY", style = typography.xs.semiBold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.14f))
                            .clickable(onClick = action).padding(horizontal = 9.dp, vertical = 7.dp))
                }
                onViewAll?.let { action ->
                    BasicText(text = "VIEW ALL", style = typography.xs.semiBold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.14f))
                            .clickable(onClick = action).padding(horizontal = 9.dp, vertical = 7.dp))
                }
            }
        }
    }

    if (songs.isEmpty()) {
        if (verticalList) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) { repeat(5) { SongItemPlaceholder(thumbnailSizeDp = thumbnailSizeDp) } }
        } else {
            LazyHorizontalGrid(
                rows = GridCells.Fixed(5),
                contentPadding = endPaddingValues,
                modifier = Modifier.fillMaxWidth().height((thumbnailSizeDp + Dimensions.itemsVerticalPadding * 2) * 5)
            ) { items(5) { SongItemPlaceholder(thumbnailSizeDp = thumbnailSizeDp) } }
        }
    } else if (verticalList) {
        // Only the opened category detail page uses a vertical song list.
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            songs.distinctBy(Innertube.SongItem::key).forEach { song ->
                SongItem(
                    song = song,
                    thumbnailSizePx = thumbnailSizePx,
                    thumbnailSizeDp = thumbnailSizeDp,
                    modifier = Modifier.fillMaxWidth().combinedClickable(
                        onLongClick = { onSongLongClick(song) },
                        onClick = { onSongClick(song) }
                    )
                )
            }
        }
    } else {
        // Keep the Home screen's original five-row horizontally scrolling shelves.
        LazyHorizontalGrid(
            state = shelfGridState,
            rows = GridCells.Fixed(5),
            contentPadding = endPaddingValues,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().height((thumbnailSizeDp + Dimensions.itemsVerticalPadding * 2) * 5)
        ) {
            items(items = songs.distinctBy(Innertube.SongItem::key).take(if (verticalList) Int.MAX_VALUE else 15), key = Innertube.SongItem::key) { song ->
                SongItem(
                    song = song,
                    thumbnailSizePx = thumbnailSizePx,
                    thumbnailSizeDp = thumbnailSizeDp,
                    modifier = Modifier.width(itemWidth).combinedClickable(
                        onLongClick = { onSongLongClick(song) },
                        onClick = { onSongClick(song) }
                    )
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

/** Fixed Calm card order. Daily rotation applies to songs inside each card, not cards. */
private fun calmMusicCategories(): List<DiscoverySubcategory> = listOf(
    DiscoverySubcategory("calm_alpha", "Calm", "Alpha Music", "calm", "alpha waves music 8hz relaxing focus"),
    DiscoverySubcategory("calm_beta", "Calm", "Beta Music", "calm", "beta waves music focus concentration"),
    DiscoverySubcategory("calm_gamma", "Calm", "Gama Music", "calm", "gamma waves music meditation focus"),
    DiscoverySubcategory("calm_meditation", "Calm", "Meditation Music", "calm", "meditation music peaceful instrumental")
)

private suspend fun fetchCalmMusicCategories(refreshCycle: Int): Map<String, List<Innertube.SongItem>> = coroutineScope {
    calmMusicCategories().map { category ->
        async {
            val categoryQueries = cycleCategoryQueries(listOf(category.query, "${category.title} instrumental relaxing music"), refreshCycle, 1)
            val categorySongs = fetchSearchAndRelatedBundle(categoryQueries)
            val filteredSongs = filterDiscoverySongs(category, categorySongs)
            category.key to rotateDiscoverySongsDaily(personalizeAndDiversify(prioritizeHindiSongs(filteredSongs, category.key)))
        }
    }.awaitAll().toMap()
}

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
private fun QuickPicksPodcastShelf(
    songs: List<Innertube.SongItem>,
    isLoading: Boolean,
    onViewAll: () -> Unit,
    onSongClick: (Innertube.SongItem) -> Unit,
    onPlayBundle: () -> Unit,
    onSongLongClick: (Innertube.SongItem) -> Unit
) {
    val (_, typography) = LocalAppearance.current
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 24.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
        ) {
            BasicText(text = "PODCAST", style = typography.s.semiBold, modifier = Modifier.weight(1f))
            Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    text = "PLAY",
                    style = typography.xs.semiBold,
                    modifier = Modifier.clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.14f))
                        .clickable(onClick = onPlayBundle)
                        .padding(horizontal = 9.dp, vertical = 7.dp)
                )
                BasicText(
                    text = "VIEW ALL",
                    style = typography.xs.semiBold,
                    modifier = Modifier.clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.14f))
                        .clickable(onClick = onViewAll)
                        .padding(horizontal = 9.dp, vertical = 7.dp)
                )
            }
        }
        if (songs.isNotEmpty()) {
            LazyHorizontalGrid(
                rows = GridCells.Fixed(5),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height((64.dp + Dimensions.itemsVerticalPadding * 2) * 5)
            ) {
                items(items = songs.distinctBy(Innertube.SongItem::key).take(3), key = Innertube.SongItem::key) { song ->
                    SongItem(
                        song = song,
                        thumbnailSizePx = 64.dp.px,
                        thumbnailSizeDp = 64.dp,
                        modifier = Modifier.width(220.dp).combinedClickable(
                            onClick = { onSongClick(song) },
                            onLongClick = { onSongLongClick(song) }
                        )
                    )
                }
            }
        } else if (isLoading) {
            LazyHorizontalGrid(
                rows = GridCells.Fixed(5),
                modifier = Modifier.fillMaxWidth().height((64.dp + Dimensions.itemsVerticalPadding * 2) * 5)
            ) {
                items(5) { SongItemPlaceholder(thumbnailSizeDp = 64.dp) }
            }
        } else if (songs.isEmpty()) {
            BasicText(
                text = "No podcast episodes found yet. Pull down to refresh.",
                style = typography.xs.secondary,
                modifier = Modifier.padding(vertical = 14.dp)
            )
        }
    }
}

@Composable
private fun QuickPicksDiscoveryShelf(
    category: DiscoverySubcategory,
    songs: List<Innertube.SongItem>,
    refreshToken: Int,
    onSongClick: (Innertube.SongItem) -> Unit,
    onViewAll: () -> Unit,
    onPlayBundle: () -> Unit,
    onSongLongClick: (Innertube.SongItem) -> Unit
) {
    val (_, typography) = LocalAppearance.current
    val artworkSong = songs.getOrNull(if (songs.isEmpty()) 0 else Math.floorMod(category.title.hashCode() + refreshToken + songs.size / 2, songs.size))

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
                .clickable { artworkSong?.let(onSongClick) }
        ) {
            AsyncImage(
                model = artworkSong?.thumbnail?.size(320),
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
    return dailyOrder
}

private suspend fun fetchIndiaDirectSongShelvesProgressive(
    refreshCycle: Int,
    onShelfLoaded: suspend (String, List<Innertube.SongItem>) -> Unit
) = coroutineScope {
    val chartQuery =
        "chart_params_type=WEEK&perspective=CHART&flags=viral_video_chart&selected_chart=TRACKS&chart_params_id=weekly:0:0:IN"
    val trendingQueries = cycleCategoryQueries(
        listOf("India trending viral songs this week Hindi Marathi", "latest trending Indian hit songs chart", "viral Hindi Marathi songs India this week", "trending Indian music videos songs"),
        refreshCycle, 1
    )
    val topQueries = cycleCategoryQueries(
        listOf("India top chart songs Hindi Marathi", "most popular Indian songs chart", "top Hindi Bollywood songs India chart", "India top Punjabi Tamil Telugu songs"),
        refreshCycle, 2
    )
    val releaseQueries = cycleCategoryQueries(
        listOf("latest new release songs India 2026 Hindi Marathi", "new Bollywood songs official release this week", "latest Punjabi Tamil Telugu Kannada Malayalam songs", "new Hindi Marathi music releases official audio", "fresh Indian songs released recently"),
        refreshCycle, 2
    )

    suspend fun loadShelf(
        key: String,
        category: DiscoverySubcategory,
        queries: List<String>,
        chart: suspend () -> List<Innertube.SongItem>
    ) {
        val chartSongs = chart()
        val searchSongs = fetchSearchOnlyBundle(queries)
        val firstPage = (chartSongs + searchSongs).distinctBy(Innertube.SongItem::key)
        val firstFiltered = filterDiscoverySongs(category, firstPage)
        onShelfLoaded(
            key,
            prioritizeHindiSongs(personalizeAndDiversify(firstFiltered), category.key)
        )

        val related = searchSongs.take(10).map { seed ->
            async(Dispatchers.IO) {
                runCatching {
                    Innertube.relatedPage(
                        NextBody(context = IndiaExploreContext, videoId = seed.key)
                    )?.getOrNull()?.songs.orEmpty()
                }.getOrDefault(emptyList())
            }
        }.awaitAll().flatten()
        val complete = (firstPage + related).distinctBy(Innertube.SongItem::key)
        val filtered = filterDiscoverySongs(category, complete)
        onShelfLoaded(
            key,
            prioritizeHindiSongs(personalizeAndDiversify(filtered), category.key)
        )
    }

    awaitAll(
        async {
            loadShelf(
                "trending",
                DiscoverySubcategory("trend_india", "Trending", "Trending India", "trending", ""),
                trendingQueries
            ) { Innertube.indiaChartSongs(chartQuery, "videos", 1, "videoViews") }
        },
        async {
            loadShelf(
                "top",
                DiscoverySubcategory("top_india", "Top Songs", "Top Songs", "top", ""),
                topQueries
            ) { Innertube.indiaChartSongs(chartQuery, "trackTypes", 0, "trackViews") }
        },
        async {
            loadShelf(
                "new",
                DiscoverySubcategory("trend_new", "New Releases", "New Releases", "trending", ""),
                releaseQueries
            ) { emptyList() }
        }
    )
}


private suspend fun fetchLongListenSongs(batchIndex: Int = 0): List<Innertube.SongItem> = coroutineScope {
    // Long Listen uses long-form video search, not the regular Song Search shelf.
    // Four independent queries per batch prevent one sparse query limiting results.
    val queries = listOf(
        "30 minute extended music full performance",
        "45 minute live music concert full performance",
        "one hour instrumental music long play",
        "extended rock electronic jazz live session",
        "one hour Indian classical instrumental performance",
        "long live acoustic music performance",
        "60 minute ambient music full mix",
        "long form jazz live session full performance",
        "30 minutes Bollywood unplugged live songs",
        "45 minute Indian music concert",
        "one hour relaxing instrumental music",
        "long form world music live session"
    )
    val batchSize = 4
    val start = Math.floorMod(batchIndex * batchSize, queries.size)
    val batchQueries = (0 until batchSize).map { queries[(start + it) % queries.size] }
    val context = Context.DefaultWeb.copy(client = Context.DefaultWeb.client.copy(hl = "hi", gl = "IN"))
    val videoResults = batchQueries.map { query ->
        async(Dispatchers.IO) {
            runCatching {
                Innertube.searchPage(
                    body = SearchBody(context = context, query = query, params = Innertube.SearchFilter.Video.value),
                    fromMusicShelfRendererContent = { content ->
                        Innertube.VideoItem.from(content)?.let { video ->
                            Innertube.SongItem(
                                info = video.info,
                                authors = video.authors,
                                album = null,
                                durationText = video.durationText,
                                thumbnail = video.thumbnail
                            )
                        }
                    }
                )?.getOrNull()?.items.orEmpty()
            }.getOrDefault(emptyList())
        }
    }.awaitAll().flatten().distinctBy(Innertube.SongItem::key)

    val longVideos = videoResults.filter { it.durationText.toDurationSeconds() >= 15 * 60 }
    val relevant = filterDiscoverySongs(
        DiscoverySubcategory("long_listen", "Long Listen", "Long Listen", "long", ""),
        longVideos
    )
    rotateDiscoverySongsDaily(personalizeAndDiversify(relevant))
}


private suspend fun fetchMoreQuickPicksShelf(
    shelfKey: String,
    currentSongs: List<Innertube.SongItem>,
    batchIndex: Int
): List<Innertube.SongItem> {
    if (shelfKey == "long_listen") return fetchLongListenSongs(batchIndex)
    if (shelfKey == "for_you") {
        val seeds = currentSongs.takeLast(10)
        val related = coroutineScope {
            seeds.map { seed ->
                async {
                    runCatching {
                        Innertube.relatedPage(NextBody(context = IndiaExploreContext, videoId = seed.key))
                            ?.getOrNull()?.songs.orEmpty()
                    }.getOrDefault(emptyList())
                }
            }.awaitAll().flatten()
        }
        val searches = seeds.take(5).map { seed ->
            listOf(seed.info?.name.orEmpty(), seed.authors?.firstOrNull()?.name.orEmpty(), "similar songs")
                .filter { it.isNotBlank() }.joinToString(" ")
        }.filter { it.isNotBlank() }
        val searched = searches.map { fetchSongsByCategoryWithFallback(it) }.flatten()
        return personalizeAndDiversify((related + searched).distinctBy(Innertube.SongItem::key))
    }
    val queries = when (shelfKey) {
        "trending" -> listOf(
            "India trending viral songs this week Hindi",
            "latest trending Indian hit songs chart",
            "viral Hindi Marathi songs India",
            "trending Indian music videos songs"
        )
        "top" -> listOf(
            "India top chart songs Hindi Marathi",
            "most popular Indian songs all time chart",
            "top Hindi Bollywood songs India chart",
            "India top Punjabi Tamil Telugu songs"
        )
        "new" -> listOf(
            "new Hindi Marathi song releases this week",
            "latest Indian music releases new songs",
            "new Bollywood songs official release",
            "new Punjabi and regional Indian songs"
        )
        else -> emptyList()
    }
    if (queries.isEmpty()) return emptyList()
    val query = queries[Math.floorMod(batchIndex - 1, queries.size)]
    val category = when (shelfKey) {
        "trending" -> DiscoverySubcategory("trend_india", "Trending", "Trending India", "trending", query)
        "new" -> DiscoverySubcategory("trend_new", "New Releases", "New Releases", "trending", query)
        else -> DiscoverySubcategory("top_india", "Top Songs", "Top Songs", "top", query)
    }
    val candidates = fetchSearchAndRelatedBundle(listOf(query))
    // The query itself is category-targeted. Avoid a second title-keyword gate:
    // legitimate songs often omit words such as "trending", "top" or "new".
    val relevant = filterDiscoverySongs(category, candidates)
    return personalizeAndDiversify(prioritizeHindiSongs(relevant, category.key))
}

private suspend fun fetchQuickPickDiscoveryCategories(
    refreshCycle: Int,
    onCategoryLoaded: suspend (String, List<Innertube.SongItem>) -> Unit
): Map<String, List<Innertube.SongItem>> = coroutineScope {
    discoveryBundles().flatMap { it.categories }.map { category ->
        async {
            val isPersonal = category.kind == "personal"
            val queries = if (isPersonal) emptyList() else
                cycleCategoryQueries(discoverySearchQueries(category), refreshCycle, 1)

            // First paint: show filtered Song Search/history results immediately.
            val searchFeed = if (isPersonal) {
                fetchPersonalizedSongsForYou(refreshCycle)
            } else {
                fetchSearchOnlyBundle(queries)
            }
            val initialFiltered = filterDiscoverySongs(category, searchFeed)
            val initialRanked = prioritizeHindiSongs(initialFiltered, category.key)
            val initialSongs = rotateDiscoverySongsDaily(personalizeAndDiversify(initialRanked))
            if (initialSongs.isNotEmpty()) onCategoryLoaded(category.key, initialSongs)

            // Enrich non-language-specific shelves in the background using the same
            // Related Songs source and the exact same category filter as before.
            val completeFeed = if (isPersonal || category.key in strictLanguageCategoryKeys) {
                searchFeed
            } else {
                val related = searchFeed.take(10).map { seed ->
                    async(Dispatchers.IO) {
                        runCatching {
                            Innertube.relatedPage(
                                NextBody(context = IndiaExploreContext, videoId = seed.key)
                            )?.getOrNull()?.songs.orEmpty()
                        }.getOrDefault(emptyList())
                    }
                }.awaitAll().flatten()
                (searchFeed + related).distinctBy(Innertube.SongItem::key)
            }

            val categoryFiltered = filterDiscoverySongs(category, completeFeed)
            val prioritized = prioritizeHindiSongs(categoryFiltered, category.key)
            val finalSongs = rotateDiscoverySongsDaily(personalizeAndDiversify(prioritized))
            onCategoryLoaded(category.key, finalSongs)
            category.key to finalSongs
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

/**
 * Apply language boundaries after both Song Search and Related Songs have been merged.
 * Related Songs can drift into another language even when the seed query is correct.
 * Use title + artist metadata so transliterated tracks remain eligible while scripts
 * that clearly belong to another language are excluded.
 */
/**
 * Category-specific search expansion plus a shared relevance guard.
 * Related Songs can drift far from the requested shelf, so filter after merging.
 */
private fun discoverySearchQueries(category: DiscoverySubcategory): List<String> {
    val targeted = when (category.key) {
        "mix_hindi" -> listOf("popular Hindi hit songs Bollywood", "latest Hindi film songs")
        "mix_marathi" -> listOf("popular Marathi film songs", "latest Marathi hit songs", "Marathi romantic songs")
        "mix_punjabi" -> listOf("popular Punjabi pop songs", "latest Punjabi hit songs")
        "mix_regional" -> listOf("popular Tamil Telugu Kannada Malayalam film songs", "latest South Indian songs")
        "mix_romantic" -> listOf("romantic Hindi love songs", "romantic Marathi film songs", "Bollywood love duets")
        "mix_chill" -> listOf("chill Hindi acoustic songs", "soft Marathi songs", "relaxing Indian indie music")
        "trend_india" -> listOf("India trending popular film songs", "viral Indian music hits")
        "trend_hindi" -> listOf("viral trending Hindi film songs", "Hindi chart hits")
        "trend_marathi" -> listOf("trending Marathi film songs", "viral Marathi songs")
        "trend_reels" -> listOf("viral Instagram reels songs India", "trending reels Hindi Marathi songs")
        "trend_pop" -> listOf("trending Indian pop songs", "latest Hindi indie pop hits")
        "trend_regional" -> listOf("trending Tamil Telugu Punjabi songs", "viral South Indian songs")
        "trend_new" -> listOf("latest new Hindi songs 2026", "new Marathi songs 2026", "latest Indian music releases")
        "together_party" -> listOf("Hindi Bollywood party dance hits", "Punjabi party songs")
        "together_duet" -> listOf("Hindi male female duet songs", "popular Bollywood duet songs", "Marathi duet songs")
        "together_romance" -> listOf("romantic duet songs Hindi Bollywood male female", "best romantic Bollywood duets", "romantic Marathi duet songs")
        "together_chill" -> listOf("chill Hindi songs", "relaxing Indian indie acoustic songs", "soft Marathi songs")
        "together_classics" -> listOf("evergreen Hindi film songs", "classic Bollywood duets", "old Hindi hit songs")
        "together_dance" -> listOf("Hindi Bollywood dance hits", "Punjabi dance party songs")
        "together_friends" -> listOf("Hindi friendship songs", "Bollywood friendship songs", "popular friends songs India")
        "calm_alpha" -> listOf("alpha waves focus instrumental music", "8hz alpha relaxation instrumental")
        "calm_beta" -> listOf("beta waves focus instrumental music", "concentration instrumental music")
        "calm_gamma" -> listOf("gamma waves meditation instrumental music", "gamma focus ambient music")
        "calm_meditation" -> listOf("peaceful meditation instrumental music", "calm mindfulness music")
        else -> listOf(category.query).filter { it.isNotBlank() }
    }
    return targeted.distinct()
}

private fun filterDiscoverySongs(
    category: DiscoverySubcategory,
    candidates: List<Innertube.SongItem>
): List<Innertube.SongItem> {
    // Devotional content is valid only for the personal taste shelf if the listener
    // actually listens to it. All named entertainment/mood shelves exclude it.
    val devotionalTerms = listOf(
        "hanuman chalisa", "hanuman", "chalisa", "bhajan", "bhakti", "aarti", "arti",
        "abhang", "abhanga", "kirtan", "kirtana", "haripath", "gondhal", "bharud",
        "vitthal", "vithal", "pandurang", "mauli", "wari", "pandharpur", "ganpati",
        "ganesh aarti", "bappa", "sukhkarta", "dukhharta", "jogwa", "jagar",
        "tulja bhavani", "sai baba", "sai ram", "ram dhun", "ram raksha",
        "shiv bhajan", "shankar", "mahadev", "krishna bhajan", "mata bhajan",
        "durga", "stotra", "mantra", "devotional", "sankirtan", "namdev",
        "तुकाराम", "ज्ञानेश्वर", "माऊली", "वारी", "पंढरपूर", "साईबाबा",
        "भजन", "अभंग", "आरती", "कीर्तन", "हरिपाठ", "गोंधळ", "भारूड",
        "विठ्ठल", "पांडुरंग", "गणपती", "गणेश", "चालीसा", "स्तोत्र", "मंत्र",
        "सुखकर्ता", "दुःखहर्ता", "जोगवा", "जागर", "तुळजाभवानी", "रामरक्षा"
    )
    fun containsScript(value: String, first: Int, last: Int): Boolean =
        value.any { it.code in first..last }

    val filtered = candidates.distinctBy(Innertube.SongItem::key).filter { song ->
        val title = song.info?.name.orEmpty().lowercase()
        val metadata = (title + " " +
            song.authors.orEmpty().joinToString(" ") { it.name.orEmpty() }).lowercase()
        val isPersonalTaste = category.kind == "personal"
        if (!isPersonalTaste && devotionalTerms.any(metadata::contains)) return@filter false

        val hasTamil = containsScript(metadata, 0x0B80, 0x0BFF)
        val hasTelugu = containsScript(metadata, 0x0C00, 0x0C7F)
        val hasKannada = containsScript(metadata, 0x0C80, 0x0CFF)
        val hasMalayalam = containsScript(metadata, 0x0D00, 0x0D7F)
        val hasGurmukhi = containsScript(metadata, 0x0A00, 0x0A7F)
        val hasDevanagari = containsScript(metadata, 0x0900, 0x097F)

        val languageMatches = when (category.key) {
            "mix_hindi", "trend_hindi" ->
                !hasTamil && !hasTelugu && !hasKannada && !hasMalayalam && !hasGurmukhi
            "mix_marathi", "trend_marathi" ->
                !hasTamil && !hasTelugu && !hasKannada && !hasMalayalam && !hasGurmukhi
            "mix_punjabi" ->
                !hasTamil && !hasTelugu && !hasKannada && !hasMalayalam && !hasDevanagari
            "mix_regional", "trend_regional" ->
                hasTamil || hasTelugu || hasKannada || hasMalayalam || metadata.contains("tamil") ||
                    metadata.contains("telugu") || metadata.contains("kannada") || metadata.contains("malayalam")
            else -> true
        }

        // Search roots already target the requested category. Do not require
        // category words in every song title: many valid tracks have no mood/language
        // keywords in metadata. Keep only clear negative exclusions below.
        val categoryMatches = when (category.key) {
            "mix_romantic", "together_romance" ->
                listOf("party", "remix", "dj mix", "devotional", "qawwali").none(metadata::contains)
            "mix_chill", "together_chill" ->
                listOf("dj remix", "party mix", "hardstyle", "devotional").none(metadata::contains)
            "together_party", "together_dance" ->
                listOf("bhajan", "aarti", "sad songs", "sleep music", "meditation").none(metadata::contains)
            "together_classics" ->
                listOf("new release", "latest 2026", "remix").none(metadata::contains)
            "together_friends" ->
                listOf("bhajan", "aarti", "devotional", "sad songs").none(metadata::contains)
            else -> true
        }
        languageMatches && categoryMatches
    }

    // Keep every valid fetched song, but put stronger category matches first.
    // This is ranking only: it does not impose a result-count limit.
    val affinityTerms = when (category.key) {
        "mix_hindi", "trend_hindi" -> listOf("hindi", "bollywood", "हिंदी", "हिन्दी")
        "trend_india" -> listOf("india", "indian", "hindi", "bollywood", "marathi", "punjabi", "viral", "trending")
        "trend_regional" -> listOf("tamil", "telugu", "kannada", "malayalam", "punjabi", "regional")
        "mix_marathi", "trend_marathi" -> listOf("marathi", "मराठी")
        "mix_punjabi" -> listOf("punjabi", "ਪੰਜਾਬੀ")
        "mix_regional", "trend_regional" -> listOf("tamil", "telugu", "kannada", "malayalam")
        "mix_romantic", "together_romance" -> listOf("romantic", "love", "ishq", "pyaar", "प्रेम", "प्यार", "लव")
        "mix_chill", "together_chill" -> listOf("chill", "acoustic", "soft", "relax", "lofi", "lo-fi", "calm")
        "trend_reels" -> listOf("reels", "viral", "instagram", "shorts", "trending")
        "trend_pop" -> listOf("pop", "indie", "viral", "trending")
        "trend_new" -> listOf("2026", "latest", "new", "new release")
        "together_party", "together_dance" -> listOf("party", "dance", "club", "dj", "bhangra", "remix")
        "together_duet" -> listOf("duet", "male female", "singer", "duo")
        "together_classics" -> listOf("classic", "evergreen", "old", "retro", "90s", "80s")
        "together_friends" -> listOf("friend", "friendship", "yaari", "dosti")
        "calm_alpha" -> listOf("alpha", "8hz", "focus", "brain waves")
        "calm_beta" -> listOf("beta", "focus", "concentration")
        "calm_gamma" -> listOf("gamma", "focus", "ambient")
        "calm_meditation" -> listOf("meditation", "mindfulness", "peaceful", "mantra")
        else -> emptyList()
    }
    return filtered.sortedByDescending { song ->
        val metadata = (song.info?.name.orEmpty() + " " +
            song.authors.orEmpty().joinToString(" ") { it.name.orEmpty() }).lowercase()
        affinityTerms.count(metadata::contains)
    }
}

private suspend fun fetchDiscoveryCategoryMore(
    category: DiscoverySubcategory,
    currentSongs: List<Innertube.SongItem>,
    batchIndex: Int
): List<Innertube.SongItem> = coroutineScope {
    val queries = discoverySearchQueries(category)
    if (queries.isEmpty()) return@coroutineScope emptyList()

    // Rotate category-specific Song Search queries for every page of results.
    val query = queries[Math.floorMod(batchIndex - 1, queries.size)]
    val searchSeeds = fetchSongsByCategoryWithFallback(query)
    if (category.key in strictLanguageCategoryKeys) {
        val relevant = filterDiscoverySongs(category, searchSeeds)
        return@coroutineScope personalizeAndDiversify(prioritizeHindiSongs(relevant, category.key))
    }

    // Other mixed shelves may expand from related tracks, then re-apply their filters.
    val freshSeeds = currentSongs.takeLast(10).ifEmpty { searchSeeds.take(10) }
    val related = freshSeeds.map { seed ->
        async {
            runCatching {
                Innertube.relatedPage(
                    NextBody(context = IndiaExploreContext, videoId = seed.key)
                )?.getOrNull()?.songs.orEmpty()
            }.getOrDefault(emptyList())
        }
    }.awaitAll().flatten()

    val candidates = (searchSeeds + related).distinctBy(Innertube.SongItem::key)
    val relevant = filterDiscoverySongs(category, candidates)
    val prioritized = prioritizeHindiSongs(relevant, category.key)
    personalizeAndDiversify(prioritized)
}

private val strictLanguageCategoryKeys = setOf(
    "mix_hindi", "mix_marathi", "mix_punjabi", "mix_regional",
    "trend_hindi", "trend_marathi", "trend_regional"
)

private suspend fun fetchSearchOnlyBundle(queries: List<String>): List<Innertube.SongItem> = coroutineScope {
    queries.map { query ->
        async { fetchSongsByCategoryWithFallback(query) }
    }.awaitAll().flatten().distinctBy(Innertube.SongItem::key)
}

private suspend fun fetchSearchAndRelatedBundle(queries: List<String>): List<Innertube.SongItem> = coroutineScope {
    // Retry empty shelves with a simpler query using the same Song Search root.
    val searchResults = queries.map { query ->
        async { fetchSongsByCategoryWithFallback(query) }
    }.awaitAll().flatten().distinctBy(Innertube.SongItem::key)

    val relatedResults = searchResults.take(10).map { seed ->
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

/** Hindi-first ordering for mixed shelves; language-specific shelves keep their own language. */
private fun prioritizeHindiSongs(songs: List<Innertube.SongItem>, categoryKey: String? = null): List<Innertube.SongItem> {
    val languageSpecific = categoryKey in setOf("mix_marathi", "trend_marathi", "mix_punjabi", "mix_regional", "trend_regional")
    if (languageSpecific || songs.isEmpty()) return songs
    fun isHindiCandidate(song: Innertube.SongItem): Boolean {
        val metadata = (song.info?.name.orEmpty() + " " + song.authors.orEmpty().joinToString(" ") { it.name.orEmpty() }).lowercase()
        val explicitHindi = listOf("hindi", "bollywood", "हिंदी", "हिन्दी", "बॉलीवुड").any(metadata::contains)
        val hasDevanagari = metadata.any { it.code in 0x0900..0x097F }
        return explicitHindi || hasDevanagari
    }
    return songs.withIndex().sortedWith(compareByDescending<IndexedValue<Innertube.SongItem>> { isHindiCandidate(it.value) }.thenBy { it.index }).map { it.value }
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

// Rotate category-specific searches on each refresh; keep every query on-topic.
private fun cycleCategoryQueries(
    queries: List<String>,
    refreshCycle: Int,
    count: Int
): List<String> {
    if (queries.isEmpty()) return emptyList()
    val start = Math.floorMod(refreshCycle * count, queries.size)
    val picked = (0 until minOf(count, queries.size)).map { queries[(start + it) % queries.size] }
    val freshness = when (Math.floorMod(refreshCycle, 4)) {
        1 -> "latest uploads"
        2 -> "official audio"
        3 -> "recent popular"
        else -> "new songs"
    }
    return picked.map { "$it $freshness" }.distinct()
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
    }
    return collected.distinctBy(Innertube.SongItem::key)
}

private suspend fun fetchSongsByCategory(query: String): List<Innertube.SongItem> = coroutineScope {
    // Primary source: YouTube Music's dedicated Song filter.
    val songResults = runCatching {
        Innertube.searchPage(
            body = SearchBody(
                context = IndiaExploreContext,
                query = query,
                params = Innertube.SearchFilter.Song.value
            ),
            fromMusicShelfRendererContent = Innertube.SongItem::from
        )?.getOrNull()?.items.orEmpty()
    }.getOrDefault(emptyList())

    // YouTube Music sometimes returns an empty/incomplete Song tab while its
    // Video tab still contains the official music results. Long Listen already
    // uses this endpoint successfully, so use it as a bounded fallback for the
    // normal song shelves instead of silently leaving every card empty.
    val videoResults = if (songResults.size < 8) {
        runCatching {
            Innertube.searchPage(
                body = SearchBody(
                    context = IndiaExploreContext,
                    query = query,
                    params = Innertube.SearchFilter.Video.value
                ),
                fromMusicShelfRendererContent = { content ->
                    Innertube.VideoItem.from(content)?.let { video ->
                        val title = video.info?.name.orEmpty().lowercase()
                        val excluded = listOf(
                            "podcast", "episode", "shorts", "#shorts", "status video",
                            "whatsapp status", "teaser", "trailer", "promo", "highlights",
                            "lyrics", "lyric video", "karaoke", "reaction", "review"
                        ).any(title::contains)
                        val duration = video.durationText.toDurationSeconds()
                        if (excluded || duration < 45L) null
                        else Innertube.SongItem(
                            info = video.info,
                            authors = video.authors,
                            album = null,
                            durationText = video.durationText,
                            thumbnail = video.thumbnail
                        )
                    }
                }
            )?.getOrNull()?.items.orEmpty()
        }.getOrDefault(emptyList())
    } else emptyList()

    (songResults + videoResults)
        .distinctBy(Innertube.SongItem::key)
}

/**
 * Personal recommendations use the listener's most-played and liked tracks.
 * Four distinct history seeds are queried together so one song or artist cannot dominate.
 * YouTube Music's related feed matches language, artist, genre and musical style.
 */
private suspend fun fetchPersonalizedSongsForYouFirstPaint(
    refreshCycle: Int
): List<Innertube.SongItem> = coroutineScope {
    val weightedHistory = Database.trending(30).first()
    val playTimeHistory = Database.songsByPlayTimeDesc().first()
    val favorites = Database.favorites().first()
    val seedPool = (weightedHistory + playTimeHistory + favorites).distinctBy { it.id }
    val offset = if (seedPool.isEmpty()) 0 else Math.floorMod(refreshCycle * 2, seedPool.size)
    val seeds = (seedPool.drop(offset) + seedPool.take(offset)).take(2)
    val listenedIds = seedPool.map { it.id }.toSet()

    val queries = if (seeds.isEmpty()) {
        cycleCategoryQueries(
            listOf("popular Hindi songs India", "popular Marathi songs India", "latest Hindi hit songs India"),
            refreshCycle, 1
        )
    } else {
        seeds.map { seed ->
            listOf(seed.title, seed.artistsText.orEmpty(), "similar songs")
                .filter { it.isNotBlank() }.joinToString(" ")
        }
    }
    val candidates = fetchSearchOnlyBundle(queries)
        .filter { it.key !in listenedIds }
        .distinctBy(Innertube.SongItem::key)
    personalizeAndDiversify(candidates)
}


private suspend fun fetchPersonalizedSongsForYou(refreshCycle: Int = 0): List<Innertube.SongItem> = coroutineScope {
    val weightedHistory = Database.trending(30).first()
    val playTimeHistory = Database.songsByPlayTimeDesc().first()
    val history = (weightedHistory + playTimeHistory).distinctBy { it.id }
    val favorites = Database.favorites().first()
    val seedPool = (history + favorites).distinctBy { it.id }
    val seedOffset = if (seedPool.isEmpty()) 0 else Math.floorMod(refreshCycle * 3, seedPool.size)
    val rotatedSeedPool = seedPool.drop(seedOffset) + seedPool.take(seedOffset)
    val seeds = rotatedSeedPool.take(4)
    val listenedIds = (history + favorites).map { it.id }.toSet()

    // With no listening history yet, populate from Indian discovery; taste ranking
    // will become personal automatically after the listener starts playing tracks.
    if (seeds.isEmpty()) {
        return@coroutineScope fetchSearchAndRelatedBundle(
            cycleCategoryQueries(
                listOf("popular Hindi songs India", "popular Marathi songs India", "latest Hindi hit songs India", "popular Indian indie songs", "viral Bollywood songs India", "new Marathi hit songs"),
                refreshCycle, 1
            )
        ).distinctBy(Innertube.SongItem::key)
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
    val candidates = if (related.size >= 15) related else {
        val seedSearches = seeds.map { seed ->
            async {
                val artist = seed.artistsText.orEmpty().takeIf { it.isNotBlank() }.orEmpty()
                val query = listOf(seed.title, artist, "similar songs")
                    .filter { it.isNotBlank() }.joinToString(" ")
                fetchSongsByCategoryWithFallback(query)
            }
        }.awaitAll().flatten()
        val personalizedCandidates = (related + seedSearches).filter { it.key !in listenedIds }
            .distinctBy(Innertube.SongItem::key)
        if (personalizedCandidates.size >= 15) personalizedCandidates else {
            val broadFallback = fetchSearchAndRelatedBundle(
                cycleCategoryQueries(
                    listOf("popular Hindi songs India", "popular Marathi songs India", "latest Bollywood hits India", "viral Indian songs this week", "popular Punjabi songs India", "new Hindi indie songs"),
                    refreshCycle, 3
                )
            ).filter { it.key !in listenedIds }
            (personalizedCandidates + broadFallback).distinctBy(Innertube.SongItem::key)
        }
    }

    // Interleave artist buckets to prevent one singer filling the shelf.
    val artistBuckets = candidates.groupBy { song ->
        song.authors?.firstOrNull()?.name?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: song.key
    }.values.toList()
    val diversified = mutableListOf<Innertube.SongItem>()
    val maximumArtistBucketSize = artistBuckets.maxOfOrNull { it.size } ?: 0
    for (round in 0 until maximumArtistBucketSize) {
        artistBuckets.forEach { bucket -> bucket.getOrNull(round)?.let(diversified::add) }
    }
    diversified.distinctBy(Innertube.SongItem::key)
}
private val IndiaExploreContext = Context.DefaultWeb.copy(
    client = Context.DefaultWeb.client.copy(hl = "en", gl = "IN")
)

private suspend fun fetchIndiaDirectSongShelves(refreshCycle: Int): Triple<
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
    val trendingQueries = cycleCategoryQueries(
        listOf("India trending viral songs this week Hindi Marathi", "latest trending Indian hit songs chart", "viral Hindi Marathi songs India this week", "trending Indian music videos songs"),
        refreshCycle, 1
    )
    val topQueries = cycleCategoryQueries(
        listOf("India top chart songs Hindi Marathi", "most popular Indian songs chart", "top Hindi Bollywood songs India chart", "India top Punjabi Tamil Telugu songs"),
        refreshCycle, 2
    )
    val releaseQueries = cycleCategoryQueries(
        listOf("latest new release songs India 2026 Hindi Marathi", "new Bollywood songs official release this week", "latest Punjabi Tamil Telugu Kannada Malayalam songs", "new Hindi Marathi music releases official audio", "fresh Indian songs released recently"),
        refreshCycle, 2
    )

    val trendingDeferred = async {
        val chart = Innertube.indiaChartSongs(chartQuery, "videos", 1, "videoViews")
        (chart + fetchSearchAndRelatedBundle(trendingQueries)).distinctBy(Innertube.SongItem::key)
    }
    val topDeferred = async {
        val chart = Innertube.indiaChartSongs(chartQuery, "trackTypes", 0, "trackViews")
        (chart + fetchSearchAndRelatedBundle(topQueries)).distinctBy(Innertube.SongItem::key)
    }
    val releasesDeferred = async { fetchSearchAndRelatedBundle(releaseQueries) }

    Triple(trendingDeferred.await(), topDeferred.await(), releasesDeferred.await())
}

