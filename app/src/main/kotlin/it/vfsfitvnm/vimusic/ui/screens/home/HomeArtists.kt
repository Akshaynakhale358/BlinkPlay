package it.vfsfitvnm.vimusic.ui.screens.home

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.Context
import it.vfsfitvnm.innertube.models.bodies.ContinuationBody
import it.vfsfitvnm.innertube.models.bodies.SearchBody
import it.vfsfitvnm.innertube.requests.searchPage
import it.vfsfitvnm.innertube.utils.from
import it.vfsfitvnm.vimusic.R
import it.vfsfitvnm.vimusic.ui.components.ShimmerHost
import it.vfsfitvnm.vimusic.ui.components.themed.Header
import it.vfsfitvnm.vimusic.ui.components.themed.HeaderIconButton
import it.vfsfitvnm.vimusic.ui.items.ArtistItem
import it.vfsfitvnm.vimusic.ui.items.ArtistItemPlaceholder
import it.vfsfitvnm.vimusic.ui.styling.Dimensions
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.ui.styling.px
import it.vfsfitvnm.vimusic.utils.semiBold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect

@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
fun HomeArtistList(
    onArtistClick: (String) -> Unit,
    onSearchClick: () -> Unit,
) {
    val (colorPalette, typography) = LocalAppearance.current
    val categories = listOf(
        "All" to listOf("Hindi singers", "Bollywood playback singers", "Marathi singers", "Punjabi singers", "Tamil singers", "Telugu singers", "Bengali singers", "Kannada singers", "Malayalam singers", "English pop artists", "international music artists"),
        "Hindi" to listOf("Hindi singers", "Hindi music artists", "Indian playback singers"),
        "Marathi" to listOf("Marathi singers", "Marathi music artists", "Marathi playback singers"),
        "Bollywood" to listOf("Bollywood singers", "Hindi film playback singers", "Bollywood music artists"),
        "Punjabi" to listOf("Punjabi singers", "Punjabi music artists", "Punjabi playback singers"),
        "Tamil" to listOf("Tamil singers", "Tamil music artists", "Tamil playback singers"),
        "Telugu" to listOf("Telugu singers", "Telugu music artists", "Telugu playback singers"),
        "South Indian" to listOf("Tamil singers", "Telugu singers", "Malayalam singers", "Kannada singers", "South Indian music artists"),
        "English" to listOf("English pop artists", "English rock artists", "international music artists")
    )
    var selectedCategory by remember { mutableStateOf("All") }
    var refreshToken by remember { mutableStateOf(0) }
    val queries = categories.first { it.first == selectedCategory }.second
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val thumbnailSizeDp = (Dimensions.thumbnails.song * 1.35f).coerceAtLeast(64.dp)
    val thumbnailSizePx = thumbnailSizeDp.px
    val indiaContext = Context.DefaultWeb.copy(
        client = Context.DefaultWeb.client.copy(hl = "hi", gl = "IN")
    )

    var artists by remember(selectedCategory, refreshToken) { mutableStateOf<List<Innertube.ArtistItem>>(emptyList()) }
    var continuation by remember(selectedCategory, refreshToken) { mutableStateOf<String?>(null) }
    var queryIndex by remember(selectedCategory, refreshToken) { mutableStateOf(0) }
    var loading by remember(selectedCategory, refreshToken) { mutableStateOf(false) }
    var finished by remember(selectedCategory, refreshToken) { mutableStateOf(false) }

    LaunchedEffect(selectedCategory, refreshToken, gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { lastVisible ->
                if (loading || finished || lastVisible < artists.size - 4) return@collect
                loading = true
                withContext(Dispatchers.IO) {
                    var attempts = 0
                    while (attempts < 20 && !finished) {
                        val query = queries.getOrNull(queryIndex)
                        if (query == null) {
                            finished = true
                            break
                        }
                        val token = continuation
                        val result = if (token == null) {
                            Innertube.searchPage(
                                body = SearchBody(context = indiaContext, query = query, params = Innertube.SearchFilter.Artist.value),
                                fromMusicShelfRendererContent = Innertube.ArtistItem::from
                            )
                        } else {
                            Innertube.searchPage(
                                body = ContinuationBody(context = indiaContext, continuation = token),
                                fromMusicShelfRendererContent = Innertube.ArtistItem::from
                            )
                        }
                        val page = result?.getOrNull()
                        val incoming = page?.items.orEmpty()
                        val known = artists.mapTo(mutableSetOf()) { it.key }
                        val newItems = incoming.filter { known.add(it.key) }
                        if (newItems.isNotEmpty()) {
                            artists = artists + newItems
                        }

                        val nextContinuation = page?.continuation
                        if (page == null || nextContinuation == null || nextContinuation == token) {
                            // A failed, exhausted, or repeated page must not strand this category.
                            queryIndex += 1
                            continuation = null
                        } else {
                            continuation = nextContinuation
                        }
                        if (page == null && queryIndex >= queries.size) finished = true
                        attempts += 1
                        if (newItems.isNotEmpty()) break
                    }
                    if (queryIndex >= queries.size && continuation == null) finished = true
                }
                loading = false
            }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    PullToRefreshBox(
        isRefreshing = loading && artists.isEmpty(),
        onRefresh = { refreshToken += 1 },
        modifier = Modifier.fillMaxSize()
    ) {
    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
        Header(title = "Artists") {
        }

        BasicText(
            text = "Explore artists",
            style = typography.m,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
            rowItems(categories, key = { it.first }) { category ->
                BasicText(
                    text = category.first,
                    style = typography.s.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                    modifier = Modifier
                        .padding(horizontal = 4.dp, vertical = 8.dp)
                        .clickable {
                            if (selectedCategory != category.first) {
                                selectedCategory = category.first
                                scope.launch { gridState.scrollToItem(0) }
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                )
            }
        }
        if (artists.isEmpty() && !finished) {
            ShimmerHost(modifier = Modifier.fillMaxSize()) {
                repeat(9) {
                    ArtistItemPlaceholder(thumbnailSizeDp = thumbnailSizeDp, alternative = true)
                }
            }
        } else if (artists.isEmpty()) {
            BasicText(
                text = "No artists found. Try another category or Search.",
                style = typography.s,
                modifier = Modifier.padding(24.dp)
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(artists, key = Innertube.ArtistItem::key) { artist ->
                    ArtistItem(
                        artist = artist,
                        thumbnailSizePx = thumbnailSizePx,
                        thumbnailSizeDp = thumbnailSizeDp,
                        alternative = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                artist.info?.endpoint?.browseId?.let(onArtistClick)
                            }
                    )
                }
                if (loading || !finished) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        BasicText(
                            text = if (loading) "Loading more artists..." else "Scroll to load more artists",
                            style = typography.xs,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
    }
    }
}
