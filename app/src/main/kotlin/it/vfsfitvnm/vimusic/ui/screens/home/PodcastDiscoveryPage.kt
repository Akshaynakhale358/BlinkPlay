package it.vfsfitvnm.vimusic.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import it.vfsfitvnm.innertube.models.bodies.ContinuationBody
import it.vfsfitvnm.innertube.models.bodies.SearchBody
import it.vfsfitvnm.innertube.requests.searchPage
import it.vfsfitvnm.vimusic.Database
import it.vfsfitvnm.vimusic.LocalPlayerAwareWindowInsets
import it.vfsfitvnm.vimusic.LocalPlayerServiceBinder
import it.vfsfitvnm.vimusic.models.Song
import it.vfsfitvnm.vimusic.query
import it.vfsfitvnm.vimusic.ui.components.LocalMenuState
import it.vfsfitvnm.vimusic.ui.components.themed.Header
import it.vfsfitvnm.vimusic.ui.components.themed.NonQueuedMediaItemMenu
import it.vfsfitvnm.vimusic.ui.items.SongItem
import it.vfsfitvnm.vimusic.ui.items.SongItemPlaceholder
import it.vfsfitvnm.vimusic.ui.screens.searchresult.ItemsPage
import it.vfsfitvnm.vimusic.ui.styling.Dimensions
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.ui.styling.px
import it.vfsfitvnm.vimusic.utils.asMediaItem
import it.vfsfitvnm.vimusic.utils.forcePlay
import it.vfsfitvnm.vimusic.utils.secondary
import it.vfsfitvnm.vimusic.utils.semiBold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private val PodcastContext = Context.DefaultWeb.copy(
    client = Context.DefaultWeb.client.copy(hl = "hi", gl = "IN")
)

private fun Innertube.VideoItem.toPodcastSong() = Innertube.SongItem(
    info = info,
    authors = authors,
    album = null,
    durationText = durationText,
    thumbnail = thumbnail
)

private suspend fun fetchPodcastPage(
    query: String,
    continuation: String? = null
): Result<Innertube.ItemsPage<Innertube.SongItem>?>? {
    return if (continuation == null) {
        Innertube.searchPage(
            body = SearchBody(
                context = PodcastContext,
                query = query,
                params = Innertube.SearchFilter.Video.value
            ),
            fromMusicShelfRendererContent = { content ->
                Innertube.VideoItem.from(content)?.toPodcastSong()
            }
        )
    } else {
        Innertube.searchPage(
            body = ContinuationBody(context = PodcastContext, continuation = continuation),
            fromMusicShelfRendererContent = { content ->
                Innertube.VideoItem.from(content)?.toPodcastSong()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PodcastDiscoveryPage(
    categories: List<Pair<String, String>>,
    onCategorySelect: (String) -> Unit
) {
    val (palette, typography) = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current
    val thumbnailSizeDp = Dimensions.thumbnails.song
    val thumbnailSizePx = thumbnailSizeDp.px
    var refreshKey by remember { mutableStateOf(0) }
    var showAll by remember { mutableStateOf(false) }
    var historyQuery by remember { mutableStateOf("popular Indian podcasts interviews full episodes") }

    LaunchedEffect(Unit) {
        val history = withContext(Dispatchers.IO) {
            Database.trending(20).first()
        }
        val seeds = history
            .distinctBy { it.artistsText?.trim()?.lowercase()?.takeIf(String::isNotBlank) ?: it.id }
            .take(6)
        if (seeds.isNotEmpty()) {
            val listeningPattern = seeds.joinToString(" ") {
                listOfNotNull(it.title, it.artistsText).joinToString(" ")
            }.take(220)
            historyQuery = "podcasts and interviews related to $listeningPattern"
        }
    }

    fun playPodcast(song: Innertube.SongItem) {
        val mediaItem = song.asMediaItem
        binder?.stopRadio()
        binder?.player?.forcePlay(mediaItem)
        binder?.setupRadio(song.info?.endpoint)
    }

    if (showAll) {
        ItemsPage(
            tag = "home/podcast-all",
            itemsPageProvider = { continuation ->
                fetchPodcastPage(
                    query = "all podcasts India Hindi Marathi English full episodes interviews",
                    continuation = continuation
                )
            },
            emptyItemsText = "No podcasts found. Pull down to retry.",
            refreshOnEnter = true,
            headerContent = {
                Header(title = "All Podcast") {
                    androidx.compose.foundation.text.BasicText(
                        text = "BACK",
                        style = typography.s.semiBold,
                        modifier = Modifier.clickable { showAll = false }
                    )
                }
            },
            itemContent = { song ->
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
                        onClick = { playPodcast(song) }
                    )
                )
            },
            itemPlaceholderContent = {
                SongItemPlaceholder(thumbnailSizeDp = thumbnailSizeDp)
            }
        )
        return
    }

    val shelves = listOf(
        Triple("All Podcast", "podcasts India Hindi Marathi English interviews full episodes", true),
        Triple("Podcast For Me", historyQuery, false),
        Triple("New Podcast", "new latest podcast episodes India Hindi Marathi English", false),
        Triple("Top Podcast", "top most popular podcasts India Hindi Marathi English", false),
        Triple("Trending Podcast", "trending viral podcasts India this week", false)
    )

    PullToRefreshBox(
        isRefreshing = false,
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
                Column {
                    Header(title = "Podcast")
                    androidx.compose.foundation.text.BasicText(
                        text = "EXPLORE CATEGORIES",
                        style = typography.s.semiBold,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp)
                    )
                    LazyRow {
                        items(categories, key = { it.first }) { category ->
                            androidx.compose.foundation.text.BasicText(
                                text = category.first.uppercase(),
                                style = typography.s.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                                modifier = Modifier
                                    .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 12.dp)
                                    .background(
                                        if (category.first == "Podcast") palette.accent else palette.background1,
                                        androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
                                    )
                                    .clickable { onCategorySelect(category.first) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }

            items(shelves, key = { it.first }) { shelf ->
                PodcastShelf(
                    title = shelf.first,
                    query = shelf.second,
                    showViewAll = shelf.third,
                    refreshKey = refreshKey,
                    thumbnailSizeDp = thumbnailSizeDp,
                    thumbnailSizePx = thumbnailSizePx,
                    onViewAll = { showAll = true },
                    onSongClick = ::playPodcast,
                    onSongLongClick = { song ->
                        menuState.display {
                            NonQueuedMediaItemMenu(
                                onDismiss = menuState::hide,
                                mediaItem = song.asMediaItem
                            )
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PodcastShelf(
    title: String,
    query: String,
    showViewAll: Boolean,
    refreshKey: Int,
    thumbnailSizeDp: androidx.compose.ui.unit.Dp,
    thumbnailSizePx: Int,
    onViewAll: () -> Unit,
    onSongClick: (Innertube.SongItem) -> Unit,
    onSongLongClick: (Innertube.SongItem) -> Unit
) {
    val (_, typography) = LocalAppearance.current
    var songs by remember(query, refreshKey) { mutableStateOf<List<Innertube.SongItem>?>(null) }
    var failed by remember(query, refreshKey) { mutableStateOf(false) }
    var retryKey by remember(query, refreshKey) { mutableStateOf(0) }

    LaunchedEffect(query, refreshKey, retryKey) {
        failed = false
        val result = withContext(Dispatchers.IO) { fetchPodcastPage(query) }
        songs = result?.getOrNull()?.items.orEmpty()
        failed = result == null || result.isFailure
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        androidx.compose.foundation.text.BasicText(
            text = title.uppercase(),
            style = typography.s.semiBold
        )
        if (showViewAll) {
            androidx.compose.foundation.text.BasicText(
                text = "View All",
                style = typography.xs.secondary,
                modifier = Modifier.clickable(onClick = onViewAll)
            )
        }
    }

    when {
        songs == null -> {
            Column {
                repeat(5) { SongItemPlaceholder(thumbnailSizeDp = thumbnailSizeDp) }
            }
        }
        failed || songs.isNullOrEmpty() -> {
            androidx.compose.foundation.text.BasicText(
                text = if (failed) "Could not load. Tap to retry." else "No podcasts found. Tap to retry.",
                style = typography.xs.secondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { retryKey += 1 }
                    .padding(horizontal = 16.dp, vertical = 18.dp)
            )
        }
        else -> {
            androidx.compose.foundation.lazy.grid.LazyHorizontalGrid(
                rows = GridCells.Fixed(5),
                modifier = Modifier
                    .fillMaxWidth()
                    .height((thumbnailSizeDp + Dimensions.itemsVerticalPadding * 2) * 5)
            ) {
                items(songs.orEmpty(), key = Innertube.SongItem::key) { song ->
                    SongItem(
                        song = song,
                        thumbnailSizePx = thumbnailSizePx,
                        thumbnailSizeDp = thumbnailSizeDp,
                        modifier = Modifier
                            .width(300.dp)
                            .combinedClickable(
                                onLongClick = { onSongLongClick(song) },
                                onClick = { onSongClick(song) }
                            )
                    )
                }
            }
        }
    }
}
