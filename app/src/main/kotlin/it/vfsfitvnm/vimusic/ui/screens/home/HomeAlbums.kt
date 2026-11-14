package it.vfsfitvnm.vimusic.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.Context
import it.vfsfitvnm.innertube.models.bodies.BrowseBody
import it.vfsfitvnm.innertube.models.bodies.ContinuationBody
import it.vfsfitvnm.innertube.models.bodies.SearchBody
import it.vfsfitvnm.innertube.requests.albumPage
import it.vfsfitvnm.innertube.requests.searchPage
import it.vfsfitvnm.innertube.utils.from
import it.vfsfitvnm.vimusic.LocalPlayerServiceBinder
import it.vfsfitvnm.vimusic.R
import it.vfsfitvnm.vimusic.ui.components.themed.Header
import it.vfsfitvnm.vimusic.ui.items.AlbumItem
import it.vfsfitvnm.vimusic.ui.items.AlbumItemPlaceholder
import it.vfsfitvnm.vimusic.ui.screens.searchresult.ItemsPage
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.ui.styling.px
import it.vfsfitvnm.vimusic.utils.asMediaItem
import it.vfsfitvnm.vimusic.utils.forcePlayFromBeginning
import it.vfsfitvnm.vimusic.utils.semiBold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private data class AlbumShelf(
    val title: String,
    val query: String,
    val key: String
)

@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
fun HomeAlbums(
    homeResetKey: Int = 0,
    onAlbumClick: (String) -> Unit,
    onSearchClick: () -> Unit,
) {
    val (colorPalette, typography) = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current
    val thumbnailSizeDp = 108.dp
    val thumbnailSizePx = thumbnailSizeDp.px
    val releasePeriod = LocalDate.now().month.name.lowercase().replaceFirstChar { it.uppercase() } +
        " " + LocalDate.now().year
    val shelves = listOf(
        AlbumShelf("ALBUMS", "popular music albums", "popular"),
        AlbumShelf("NEW RELEASES", "newly released music albums $releasePeriod", "new"),
        AlbumShelf("TRENDING ALBUMS", "trending music albums this week", "trending"),
        AlbumShelf("TOP ALBUMS", "top chart most popular music albums", "top")
    )
    var refreshKey by remember { mutableStateOf(0) }
    var shelfAlbums by remember { mutableStateOf<Map<String, List<Innertube.AlbumItem>>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var selectedShelf by remember { mutableStateOf<AlbumShelf?>(null) }
    LaunchedEffect(homeResetKey) { if (homeResetKey > 0) selectedShelf = null }
    val scope = rememberCoroutineScope()

    LaunchedEffect(refreshKey) {
        loading = true
        shelfAlbums = emptyMap()
        val context = Context.DefaultWeb.copy(
            client = Context.DefaultWeb.client.copy(hl = "hi", gl = "IN")
        )
        val languageQueries = listOf(
            "Hindi Bollywood music albums",
            "Marathi music albums",
            "Punjabi Tamil Telugu Bengali Kannada Malayalam Indian regional music albums",
            "English international music albums"
        )
        val loaded = withContext(Dispatchers.IO) {
            shelves.map { shelf ->
                val results = languageQueries.map { languageQuery ->
                    runCatching {
                        Innertube.searchPage(
                            body = SearchBody(
                                context = context,
                                query = "$languageQuery ${shelf.query}",
                                params = Innertube.SearchFilter.Album.value
                            ),
                            fromMusicShelfRendererContent = Innertube.AlbumItem::from
                        )?.getOrNull()?.items.orEmpty()
                    }.getOrDefault(emptyList())
                }.flatten().distinctBy { it.key }.take(15)
                shelf.key to results
            }.toMap()
        }
        shelfAlbums = loaded
        loading = false
    }

    BackHandler(enabled = selectedShelf != null) { selectedShelf = null }

    if (selectedShelf != null) {
        val shelf = selectedShelf!!
        ItemsPage(
            tag = "home/online-albums-${shelf.key}",
            itemsPageProvider = { continuation ->
                val context = Context.DefaultWeb.copy(
                    client = Context.DefaultWeb.client.copy(hl = "hi", gl = "IN")
                )
                val languageQueries = listOf(
                    "Hindi Bollywood music albums",
                    "Marathi music albums",
                    "Punjabi Tamil Telugu Bengali Kannada Malayalam Indian regional music albums",
                    "English international music albums"
                )
                val previousTokens = continuation?.split("\\u001F")
                val nextTokens = mutableListOf<String?>()
                val combined = mutableListOf<Innertube.AlbumItem>()
                languageQueries.forEachIndexed { index, languageQuery ->
                    val token = previousTokens?.getOrNull(index)?.takeIf { it.isNotBlank() }
                    if (continuation != null && token == null) {
                        nextTokens += null
                        return@forEachIndexed
                    }
                    val page = if (token == null) {
                        Innertube.searchPage(
                            body = SearchBody(
                                context = context,
                                query = "$languageQuery ${shelf.query}",
                                params = Innertube.SearchFilter.Album.value
                            ),
                            fromMusicShelfRendererContent = Innertube.AlbumItem::from
                        )
                    } else {
                        Innertube.searchPage(
                            body = ContinuationBody(context = context, continuation = token),
                            fromMusicShelfRendererContent = Innertube.AlbumItem::from
                        )
                    }?.getOrNull()
                    combined += page?.items.orEmpty()
                    nextTokens += page?.continuation
                }
                val next = nextTokens.takeIf { it.any { token -> token != null } }
                    ?.joinToString("\\u001F") { it.orEmpty() }
                Result.success(Innertube.ItemsPage(combined.distinctBy { it.key }, next))
            },
            emptyItemsText = "No online albums found. Try Search.",
            refreshOnEnter = true,
            headerContent = {
                Column {
                    Header(title = "Albums") {}
                    BasicText(
                        text = shelf.title.replaceFirstChar { it.uppercase() },
                        style = typography.m.semiBold.copy(
                            fontSize = typography.xxl.fontSize * 0.78f,
                            textAlign = TextAlign.End
                        ),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    BasicText(
                        text = "All Albums",
                        style = typography.s.semiBold.copy(
                            fontSize = typography.xxl.fontSize * 0.468f,
                            textAlign = TextAlign.End
                        ),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            },
            itemContent = { album ->
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AlbumItem(
                        album = album,
                        thumbnailSizePx = 108.dp.px,
                        thumbnailSizeDp = 108.dp,
                        modifier = Modifier.weight(1f).clickable {
                            album.info?.endpoint?.browseId?.let(onAlbumClick)
                        }
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(end = 16.dp).size(38.dp)
                            .clip(CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                            .background(Color.White.copy(alpha = 0.14f))
                            .clickable {
                                val albumId = album.info?.endpoint?.browseId
                                if (albumId != null) {
                                    scope.launch {
                                        val songs = withContext(Dispatchers.IO) {
                                            Innertube.albumPage(BrowseBody(browseId = albumId))
                                                ?.getOrNull()?.songsPage?.items.orEmpty()
                                        }
                                        val mediaItems = songs.map(Innertube.SongItem::asMediaItem)
                                        if (mediaItems.isNotEmpty()) {
                                            binder?.stopRadio()
                                            binder?.player?.forcePlayFromBeginning(mediaItems)
                                        }
                                    }
                                }
                            }
                    ) {
                        Image(
                            painter = painterResource(R.drawable.play),
                            contentDescription = "Play album",
                            colorFilter = ColorFilter.tint(Color.White),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            },
            itemPlaceholderContent = { AlbumItemPlaceholder(thumbnailSizeDp = 108.dp) }
        )
        return
    }

    @OptIn(ExperimentalMaterial3Api::class)
    PullToRefreshBox(
        isRefreshing = loading,
        onRefresh = { refreshKey++ },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            item {
                Header(title = "Albums") {}
            }
            items(shelves, key = { it.key }) { shelf ->
                val albums = shelfAlbums[shelf.key].orEmpty()
                Column(modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        BasicText(text = shelf.title, style = typography.s.semiBold, modifier = Modifier.weight(1f))
                        BasicText(
                            text = "VIEW ALL",
                            style = typography.xs.semiBold,
                            modifier = Modifier.clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.14f))
                                .clickable {
                                    selectedShelf = shelf
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                    if (albums.isEmpty() && loading) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            repeat(5) {
                                AlbumItemPlaceholder(
                                    thumbnailSizeDp = 72.dp,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            albums.take(5).forEach { album ->
                                AlbumItem(
                                    album = album,
                                    thumbnailSizePx = 72.dp.px,
                                    thumbnailSizeDp = 72.dp,
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        album.info?.endpoint?.browseId?.let(onAlbumClick)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
