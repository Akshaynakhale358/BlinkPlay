package it.vfsfitvnm.vimusic.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.Context
import it.vfsfitvnm.innertube.models.bodies.ContinuationBody
import it.vfsfitvnm.innertube.models.bodies.SearchBody
import it.vfsfitvnm.innertube.requests.searchPage
import it.vfsfitvnm.innertube.utils.from
import it.vfsfitvnm.vimusic.LocalPlayerAwareWindowInsets
import it.vfsfitvnm.vimusic.LocalPlayerServiceBinder
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
import it.vfsfitvnm.vimusic.utils.forcePlayFromBeginning
import it.vfsfitvnm.vimusic.utils.secondary
import it.vfsfitvnm.vimusic.utils.semiBold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class HomeSongCategory(val name: String, val query: String, val podcast: Boolean = false)

private val homeSongCategories = listOf(
    HomeSongCategory("Workout", "gym workout running fitness motivation high energy songs"),
    HomeSongCategory("Feel Good", "happy uplifting positive feel good mood songs"),
    HomeSongCategory("Romance", "romantic love songs Bollywood romance"),
    HomeSongCategory("Energetic", "fast beat high energy energetic dance songs"),
    HomeSongCategory("Relax", "calm relaxing chill soft acoustic songs"),
    HomeSongCategory("Commute", "road trip driving travel commute music songs"),
    HomeSongCategory("Sad", "sad heartbreak emotional melancholy songs"),
    HomeSongCategory("Focus", "study focus concentration instrumental background music"),
    HomeSongCategory("Sleep", "sleep peaceful soft ambient lullaby music"),
    HomeSongCategory("Party", "party dance celebration upbeat hit songs")
)

private fun homeSongsDailySeed(): Int {
    val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Kolkata"))
    return calendar.get(java.util.Calendar.YEAR) * 366 + calendar.get(java.util.Calendar.DAY_OF_YEAR)
}

private fun <T> dailyRotate(items: List<T>, seed: Int): List<T> {
    if (items.size < 2) return items
    val offset = Math.floorMod(seed, items.size)
    return items.drop(offset) + items.take(offset)
}

private fun Innertube.VideoItem.toHomePodcastSong() = Innertube.SongItem(
    info = info, authors = authors, album = null, durationText = durationText, thumbnail = thumbnail
)

private suspend fun fetchHomeSongCategory(
    category: HomeSongCategory,
    continuation: String?,
    dailySeed: Int,
    refreshToken: Int = 0
): Result<Innertube.ItemsPage<Innertube.SongItem>?>? {
    val context = Context.DefaultWeb.copy(client = Context.DefaultWeb.client.copy(hl = "hi", gl = "IN"))
    // Podcast has its own video-only search rotation. Never append generic song terms:
    // that was causing the Podcast card to return ordinary songs and appear unchanged.
    val podcastRefreshTerms = listOf(
        "latest Hindi podcast episodes",
        "new Marathi podcast episodes",
        "popular Indian podcast full episodes",
        "trending interview podcast episodes"
    )
    val songRefreshTerms = listOf("latest", "new songs", "popular hits", "trending songs")
    val query = if (category.podcast) {
        "${category.query} ${podcastRefreshTerms[Math.floorMod(refreshToken, podcastRefreshTerms.size)]}"
    } else {
        "Hindi Bollywood songs ${category.query} ${songRefreshTerms[Math.floorMod(refreshToken, songRefreshTerms.size)]}"
    }
    val filter = if (category.podcast) Innertube.SearchFilter.Video.value else Innertube.SearchFilter.Song.value
    val result = if (continuation == null) {
        Innertube.searchPage(
            body = SearchBody(context = context, query = query, params = filter),
            fromMusicShelfRendererContent = { content ->
                if (category.podcast) Innertube.VideoItem.from(content)?.toHomePodcastSong()
                else Innertube.SongItem.from(content)
            }
        )
    } else {
        Innertube.searchPage(
            body = ContinuationBody(context = context, continuation = continuation),
            fromMusicShelfRendererContent = { content ->
                if (category.podcast) Innertube.VideoItem.from(content)?.toHomePodcastSong()
                else Innertube.SongItem.from(content)
            }
        )
    }
    return result?.map { page ->
        if (continuation == null && page != null) {
            page.copy(items = dailyRotate(page.items.orEmpty().distinctBy { it.key }, dailySeed))
        } else page
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalAnimationApi::class)
@Composable
fun HomeSongs(onSearchClick: () -> Unit, onPlaylistClick: (String) -> Unit) {
    val (palette, typography) = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current
    val thumbnailSizeDp = Dimensions.thumbnails.song
    val thumbnailSizePx = thumbnailSizeDp.px
    val dailySeed = homeSongsDailySeed()
    var selectedCategory by remember { mutableStateOf<HomeSongCategory?>(null) }
    var refreshToken by remember { mutableStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(refreshToken) {
        if (refreshToken > 0) {
            // Let visible cards finish their fresh search requests before ending the indicator.
            delay(1400)
            isRefreshing = false
        }
    }

    BackHandler(enabled = selectedCategory != null) { selectedCategory = null }

    fun playSong(song: Innertube.SongItem) {
        binder?.stopRadio()
        binder?.player?.forcePlay(song.asMediaItem)
        binder?.setupRadio(song.info?.endpoint)
    }

    fun playBundle(songs: List<Innertube.SongItem>) {
        val mediaItems = songs.map { it.asMediaItem }
        if (mediaItems.isNotEmpty()) {
            binder?.stopRadio()
            binder?.player?.forcePlayFromBeginning(mediaItems)
        }
    }

    if (selectedCategory != null) {
        val category = selectedCategory!!
        ItemsPage(
            tag = "home/category-songs-${category.name}",
            itemsPageProvider = { continuation -> fetchHomeSongCategory(category, continuation, dailySeed, refreshToken) },
            emptyItemsText = "No songs found for this category. Pull down to retry.",
            refreshOnEnter = true,
            headerContent = {
                Header(title = category.name.uppercase()) {
                    BasicText(
                        text = "BACK",
                        style = typography.xs.semiBold,
                        modifier = Modifier.clickable { selectedCategory = null }
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
                                NonQueuedMediaItemMenu(onDismiss = menuState::hide, mediaItem = song.asMediaItem)
                            }
                        },
                        onClick = { playSong(song) }
                    )
                )
            },
            itemPlaceholderContent = { SongItemPlaceholder(thumbnailSizeDp = thumbnailSizeDp) }
        )
        return
    }

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
      Column(modifier = Modifier.fillMaxSize().background(palette.background0)) {
        Header(title = "Songs")
        LazyColumn(contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()) {
            items(homeSongCategories, key = { it.name }) { category ->
                HomeSongCategoryCard(
                    category = category,
                    dailySeed = dailySeed,
                    refreshToken = refreshToken,
                    thumbnailSizeDp = thumbnailSizeDp,
                    thumbnailSizePx = thumbnailSizePx,
                    onViewAll = { selectedCategory = category },
                    onSongClick = ::playSong,
                    onPlayBundle = ::playBundle,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeSongCategoryCard(
    category: HomeSongCategory,
    dailySeed: Int,
    refreshToken: Int,
    thumbnailSizeDp: androidx.compose.ui.unit.Dp,
    thumbnailSizePx: Int,
    onViewAll: () -> Unit,
    onSongClick: (Innertube.SongItem) -> Unit,
    onPlayBundle: (List<Innertube.SongItem>) -> Unit,
    onSongLongClick: (Innertube.SongItem) -> Unit
) {
    val (_, typography) = LocalAppearance.current
    var songs by remember(category.name, dailySeed) { mutableStateOf<List<Innertube.SongItem>?>(null) }
    var failed by remember(category.name, dailySeed) { mutableStateOf(false) }
    var retryKey by remember(category.name, dailySeed) { mutableStateOf(0) }

    LaunchedEffect(category, dailySeed, refreshToken, retryKey) {
        failed = false
        val result = withContext(Dispatchers.IO) { fetchHomeSongCategory(category, null, dailySeed, refreshToken) }
        songs = result?.getOrNull()?.items.orEmpty().take(20)
        failed = result == null || result.isFailure
    }

    val categoryArtwork = songs.orEmpty().takeIf { it.isNotEmpty() }?.let { available ->
        // Rotate the category banner on each refresh; never reuse the first visible song artwork by default.
        val artworkIndex = Math.floorMod(dailySeed + category.name.hashCode() + refreshToken, available.size)
        available[artworkIndex].thumbnail?.size(320)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 7.dp)
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
                .clickable { songs?.firstOrNull()?.let(onSongClick) }
        ) {
            AsyncImage(
                model = categoryArtwork,
                contentDescription = category.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.48f))
                    .padding(14.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                BasicText(text = category.name.uppercase(), style = typography.s.semiBold)
                BasicText(text = "UPDATED DAILY", style = typography.xs.secondary,
                    modifier = Modifier.padding(top = 4.dp))
                BasicText(
                    text = "▶  PLAY BUNDLE",
                    style = typography.xs.semiBold,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.18f))
                        .clickable { onPlayBundle(songs.orEmpty()) }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }

        when {
            songs == null -> repeat(3) { SongItemPlaceholder(thumbnailSizeDp = 64.dp) }
            failed || songs.isNullOrEmpty() -> BasicText(
                text = "Could not load. Tap to retry.",
                style = typography.xs.secondary,
                modifier = Modifier.fillMaxWidth().clickable { retryKey += 1 }.padding(vertical = 18.dp)
            )
            else -> songs.orEmpty().take(3).forEach { song ->
                SongItem(
                    song = song,
                    thumbnailSizePx = 64.dp.px,
                    thumbnailSizeDp = 64.dp,
                    modifier = Modifier.fillMaxWidth().combinedClickable(
                        onLongClick = { onSongLongClick(song) },
                        onClick = { onSongClick(song) }
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
