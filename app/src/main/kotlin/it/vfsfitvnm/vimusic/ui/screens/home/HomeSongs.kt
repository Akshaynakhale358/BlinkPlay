package it.vfsfitvnm.vimusic.ui.screens.home

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import it.vfsfitvnm.compose.persist.persist
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.Context
import it.vfsfitvnm.innertube.models.bodies.ContinuationBody
import it.vfsfitvnm.innertube.models.bodies.SearchBody
import it.vfsfitvnm.innertube.requests.searchPage
import it.vfsfitvnm.innertube.utils.from
import it.vfsfitvnm.vimusic.LocalPlayerServiceBinder
import it.vfsfitvnm.vimusic.R
import it.vfsfitvnm.vimusic.ui.components.LocalMenuState
import it.vfsfitvnm.vimusic.ui.components.themed.Header
import it.vfsfitvnm.vimusic.ui.components.themed.HeaderIconButton
import it.vfsfitvnm.vimusic.ui.components.themed.NonQueuedMediaItemMenu
import it.vfsfitvnm.vimusic.ui.items.PlaylistItem
import it.vfsfitvnm.vimusic.ui.items.PlaylistItemPlaceholder
import it.vfsfitvnm.vimusic.ui.items.SongItem
import it.vfsfitvnm.vimusic.ui.items.SongItemPlaceholder
import it.vfsfitvnm.vimusic.ui.screens.searchresult.ItemsPage
import it.vfsfitvnm.vimusic.ui.styling.Dimensions
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.ui.styling.px
import it.vfsfitvnm.vimusic.utils.asMediaItem
import it.vfsfitvnm.vimusic.utils.forcePlay
import it.vfsfitvnm.vimusic.utils.semiBold
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
fun HomeSongs(onSearchClick: () -> Unit, onPlaylistClick: (String) -> Unit) {
    val (colorPalette, typography) = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current
    val thumbnailSizeDp = Dimensions.thumbnails.song
    val thumbnailSizePx = thumbnailSizeDp.px
    // Each shelf has a distinct intent so search results match the selected category.
    // Keep these as discovery phrases rather than generic "popular songs" queries.
    val categories = listOf(
        "Podcast" to "podcast episodes full episodes interviews conversations",
        "Workout" to "gym workout running fitness motivation high energy songs",
        "Feel Good" to "happy uplifting positive feel good mood songs",
        "Romance" to "romantic love songs Bollywood romance",
        "Energetic" to "fast beat high energy energetic dance songs",
        "Relax" to "calm relaxing chill soft acoustic songs",
        "Commute" to "road trip driving travel commute music songs",
        "Sad" to "sad heartbreak emotional melancholy songs",
        "Focus" to "study focus concentration instrumental background music",
        "Sleep" to "sleep peaceful soft ambient lullaby music",
        "Party" to "party dance celebration upbeat hit songs"
    )
    var selectedCategory by persist("home/songsCategory", "Workout")
    val selectedQuery = categories.firstOrNull { it.first == selectedCategory }?.second
        ?: "workout motivation songs"
    var categoryPlaylistRefreshKey by remember { mutableStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
    ItemsPage(
        tag = "home/category-songs-$selectedCategory",
        itemsPageProvider = { continuation ->
            // Fetch every language group independently. Keep a continuation token for
            // each group so later pages preserve Hindi -> Marathi -> other Indian -> English.
            val indiaHindiContext = Context.DefaultWeb.copy(
                client = Context.DefaultWeb.client.copy(hl = "hi", gl = "IN")
            )
            val isPodcastCategory = selectedCategory == "Podcast"
            val categoryFilter = if (isPodcastCategory) {
                Innertube.SearchFilter.Video.value
            } else {
                Innertube.SearchFilter.Song.value
            }
            val languageQueries = if (isPodcastCategory) {
                // Keep podcast discovery broad but intentional: each query targets a distinct
                // listening interest, and Video filter below prevents music-track contamination.
                listOf(
                    "Osho full discourse podcast Hindi English",
                    "spirituality meditation philosophy podcast India",
                    "motivation self improvement success podcast Hindi",
                    "Marathi podcast interviews knowledge full episode",
                    "science history business comedy storytelling podcast India"
                )
            } else {
                listOf(
                    "Hindi Bollywood songs",
                    "Marathi songs",
                    "Punjabi Tamil Telugu Bengali Kannada Malayalam Indian regional songs",
                    "English language songs international"
                )
            }
            val previousTokens = continuation?.split('\u001F')
            val nextTokens = mutableListOf<String?>()
            val combinedItems = mutableListOf<Innertube.SongItem>()

            languageQueries.forEachIndexed { index, languageQuery ->
                val token = previousTokens?.getOrNull(index)?.takeIf { it.isNotBlank() }
                // On later pages, an exhausted language group must stay exhausted;
                // do not restart its first page and append duplicate songs.
                if (continuation != null && token == null) {
                    nextTokens += null
                    return@forEachIndexed
                }
                val result = if (token == null) {
                    Innertube.searchPage(
                        body = SearchBody(
                            context = indiaHindiContext,
                            query = if (isPodcastCategory) {
                                languageQuery
                            } else {
                                "$languageQuery $selectedQuery"
                            },
                            params = categoryFilter
                        ),
                        fromMusicShelfRendererContent = { content ->
                            if (isPodcastCategory) {
                                Innertube.VideoItem.from(content)?.let { video ->
                                    Innertube.SongItem(
                                        info = video.info,
                                        authors = video.authors,
                                        album = null,
                                        durationText = video.durationText,
                                        thumbnail = video.thumbnail
                                    )
                                }
                            } else {
                                Innertube.SongItem.from(content)
                            }
                        }
                    )
                } else {
                    Innertube.searchPage(
                        body = ContinuationBody(context = indiaHindiContext, continuation = token),
                        fromMusicShelfRendererContent = { content ->
                            if (isPodcastCategory) {
                                Innertube.VideoItem.from(content)?.let { video ->
                                    Innertube.SongItem(
                                        info = video.info,
                                        authors = video.authors,
                                        album = null,
                                        durationText = video.durationText,
                                        thumbnail = video.thumbnail
                                    )
                                }
                            } else {
                                Innertube.SongItem.from(content)
                            }
                        }
                    )
                }
                val page = result?.getOrNull()
                combinedItems += page?.items.orEmpty()
                nextTokens += page?.continuation
            }

            val orderedItems = combinedItems.distinctBy { it.key }
            val nextContinuation = nextTokens
                .takeIf { tokens -> tokens.any { it != null } }
                ?.joinToString("\u001F") { it.orEmpty() }

            Result.success(
                Innertube.ItemsPage(
                    items = orderedItems,
                    continuation = nextContinuation
                )
            )
        },
        emptyItemsText = "No online songs found. Try Search.",
        refreshOnEnter = true,
        onRefresh = { categoryPlaylistRefreshKey += 1 },
        headerContent = {
            androidx.compose.foundation.layout.Column {
                Header(title = "Songs") {
                }

                BasicText(
                    text = "EXPLORE CATEGORIES",
                    style = typography.s.semiBold,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp)
                )
                LazyRow {
                    items(categories, key = { it.first }) { category ->
                        BasicText(
                            text = category.first.uppercase(),
                            style = typography.s.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                            modifier = Modifier
                                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 12.dp)
                                .background(
                                    if (selectedCategory == category.first) colorPalette.accent else colorPalette.background1,
                                    androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
                                )
                                .clickable { selectedCategory = category.first }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }
                CategoryPublicPlaylists(
                    category = selectedCategory,
                    query = selectedQuery,
                    refreshKey = categoryPlaylistRefreshKey,
                    onPlaylistClick = onPlaylistClick
                )
                BasicText(
                    text = selectedCategory.uppercase(),
                    style = typography.s.semiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
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
                    onClick = {
                        binder?.stopRadio()
                        binder?.player?.forcePlay(song.asMediaItem)
                        binder?.setupRadio(song.info?.endpoint)
                    }
                )
            )
        },
        itemPlaceholderContent = {
            SongItemPlaceholder(thumbnailSizeDp = thumbnailSizeDp)
        }
    )
    }
}


@Composable
private fun CategoryPublicPlaylists(
    category: String,
    query: String,
    refreshKey: Int,
    onPlaylistClick: (String) -> Unit
) {
    val (_, typography) = LocalAppearance.current
    val playlistThumbnailSizeDp = 108.dp
    val playlistThumbnailSizePx = playlistThumbnailSizeDp.px
    var playlists by remember(category, refreshKey) { mutableStateOf<List<Innertube.PlaylistItem>?>(null) }

    LaunchedEffect(category, query, refreshKey) {
        playlists = try {
            withContext(Dispatchers.IO) {
                Innertube.searchPage(
                    body = SearchBody(
                        context = Context.DefaultWeb.copy(
                            client = Context.DefaultWeb.client.copy(hl = "hi", gl = "IN")
                        ),
                        query = "$query $category playlists",
                        params = Innertube.SearchFilter.CommunityPlaylist.value
                    ),
                    fromMusicShelfRendererContent = Innertube.PlaylistItem::from
                )?.getOrNull()?.items.orEmpty()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
    }

    BasicText(
        text = "${category.uppercase()} PUBLIC PLAYLISTS",
        style = typography.s.semiBold,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
    )

    if (playlists == null) {
        LazyRow {
            items(3) {
                PlaylistItemPlaceholder(
                    thumbnailSizeDp = playlistThumbnailSizeDp,
                    alternative = true
                )
            }
        }
    } else {
        LazyRow {
            items(playlists.orEmpty(), key = Innertube.PlaylistItem::key) { playlist ->
                PlaylistItem(
                    playlist = playlist,
                    thumbnailSizePx = playlistThumbnailSizePx,
                    thumbnailSizeDp = playlistThumbnailSizeDp,
                    alternative = true,
                    modifier = Modifier.clickable { onPlaylistClick(playlist.key) }
                )
            }
        }
    }
}
