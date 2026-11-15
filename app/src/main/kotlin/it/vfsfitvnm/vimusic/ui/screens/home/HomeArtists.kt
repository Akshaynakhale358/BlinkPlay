package it.vfsfitvnm.vimusic.ui.screens.home

import androidx.compose.foundation.layout.offset
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect

@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
fun HomeArtistList(
    homeResetKey: Int = 0,
    onArtistClick: (String) -> Unit,
    onSearchClick: () -> Unit,
) {
    val (colorPalette, typography) = LocalAppearance.current
    val queries = listOf(
        "Hindi singers", "Bollywood playback singers", "Indian singers", "Indian music artists",
        "Marathi singers", "Punjabi singers", "Tamil singers", "Telugu singers",
        "Bengali singers", "Kannada singers", "Malayalam singers", "Assamese singers",
        "Gujarati singers", "Urdu singers", "Indian female singers", "Indian male singers",
        "Hindi music artists", "Marathi music artists", "Punjabi music artists",
        "Tamil music artists", "Telugu music artists", "Bengali music artists",
        "English pop artists", "international music artists", "world music singers",
        "pop singers", "rock bands", "hip hop artists", "independent music artists"
    )
    var artistSearchActive by remember { mutableStateOf(false) }
    var artistSearchText by remember { mutableStateOf("") }
    var artistSearchResults by remember { mutableStateOf<List<Innertube.ArtistItem>>(emptyList()) }
    var artistSearchLoading by remember { mutableStateOf(false) }
    var refreshToken by remember { mutableStateOf(0) }
    val gridState = rememberLazyGridState()
    LaunchedEffect(homeResetKey) {
        if (homeResetKey > 0) {
            gridState.scrollToItem(0)
        }
    }
    val thumbnailSizeDp = (Dimensions.thumbnails.song * 1.35f).coerceAtLeast(64.dp)
    val thumbnailSizePx = thumbnailSizeDp.px
    val indiaContext = Context.DefaultWeb.copy(
        client = Context.DefaultWeb.client.copy(hl = "hi", gl = "IN")
    )

    var artists by remember(refreshToken) { mutableStateOf<List<Innertube.ArtistItem>>(emptyList()) }
    var continuation by remember(refreshToken) { mutableStateOf<String?>(null) }
    var queryIndex by remember(refreshToken) { mutableStateOf(0) }
    var loading by remember(refreshToken) { mutableStateOf(false) }
    var finished by remember(refreshToken) { mutableStateOf(false) }

    LaunchedEffect(refreshToken, gridState) {
        // Observe both scroll position and list size: appending a page must
        // re-check the near-end condition even when the user has not scrolled.
        snapshotFlow {
            (gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) to artists.size
        }.collect { (lastVisible, artistCount) ->
                if (loading || finished || lastVisible < artistCount - 4) return@collect
                loading = true
                withContext(Dispatchers.IO) {
                    var attempts = 0
                    while (attempts < 50 && !finished) {
                        val query = queries.getOrNull(queryIndex)
                        if (query == null) {
                            finished = true
                            break
                        }
                        val token = continuation
                        val result = if (token == null) {
                            Innertube.searchPage(
                                body = SearchBody(context = indiaContext, query = query, params = Innertube.SearchFilter.Artist.value),
                                fromMusicShelfRendererContent = Innertube.ArtistItem::from,
                                fromMusicTwoRowItemRenderer = Innertube.ArtistItem::from
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
        Header(title = "Artists", titleScale = 1.404f) {
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            if (artistSearchActive) {
                BasicTextField(
                    value = artistSearchText,
                    onValueChange = { artistSearchText = it },
                    singleLine = true,
                    textStyle = typography.s.copy(color = colorPalette.text),
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        Box {
                            if (artistSearchText.isEmpty()) {
                                BasicText("Search artists", style = typography.s.copy(color = colorPalette.textDisabled))
                            }
                            innerTextField()
                        }
                    }
                )
            } else {
                BasicText(
                    text = "All Artist",
                    style = typography.s.semiBold.copy(
                        fontSize = typography.xxl.fontSize * 0.5616f,
                        textAlign = TextAlign.End
                    )
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            HeaderIconButton(
                icon = R.drawable.search,
                color = colorPalette.text,
                onClick = {
                    artistSearchActive = !artistSearchActive
                    artistSearchText = ""
                    artistSearchResults = emptyList()
                }
            )
        }

        LaunchedEffect(artistSearchText, artistSearchActive) {
            if (!artistSearchActive || artistSearchText.isBlank()) {
                artistSearchResults = emptyList()
                artistSearchLoading = false
            } else {
                delay(350)
                artistSearchLoading = true
                val result = withContext(Dispatchers.IO) {
                    Innertube.searchPage(
                        body = SearchBody(
                            context = indiaContext,
                            query = artistSearchText,
                            params = Innertube.SearchFilter.Artist.value
                        ),
                        fromMusicShelfRendererContent = Innertube.ArtistItem::from
                    )
                }
                artistSearchResults = result?.getOrNull()?.items.orEmpty().distinctBy(Innertube.ArtistItem::key)
                artistSearchLoading = false
            }
        }

        val displayedArtists = if (artistSearchActive && artistSearchText.isNotBlank()) artistSearchResults else artists
        if (artistSearchActive && artistSearchText.isNotBlank() && artistSearchLoading && displayedArtists.isEmpty()) {
            ShimmerHost(modifier = Modifier.fillMaxSize()) {
                repeat(6) { ArtistItemPlaceholder(thumbnailSizeDp = thumbnailSizeDp, alternative = true) }
            }
        } else if (displayedArtists.isEmpty() && !(artistSearchActive && artistSearchText.isNotBlank()) && !finished) {
            ShimmerHost(modifier = Modifier.fillMaxSize()) {
                repeat(9) {
                    ArtistItemPlaceholder(thumbnailSizeDp = thumbnailSizeDp, alternative = true)
                }
            }
        } else if (displayedArtists.isEmpty()) {
            BasicText(
                text = if (artistSearchActive && artistSearchText.isNotBlank()) "No artists found." else "No artists available.",
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
                items(displayedArtists, key = Innertube.ArtistItem::key) { artist ->
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
                if (!artistSearchActive && (loading || !finished)) {
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
