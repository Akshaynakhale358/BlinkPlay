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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
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
import it.vfsfitvnm.innertube.models.NavigationEndpoint
import it.vfsfitvnm.innertube.models.bodies.NextBody
import it.vfsfitvnm.innertube.models.bodies.SearchBody
import it.vfsfitvnm.innertube.requests.relatedPage
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
import it.vfsfitvnm.vimusic.ui.components.themed.PrimaryButton
import it.vfsfitvnm.vimusic.ui.components.themed.Header
import it.vfsfitvnm.vimusic.ui.components.themed.NonQueuedMediaItemMenu
import it.vfsfitvnm.vimusic.ui.components.themed.TextPlaceholder
import it.vfsfitvnm.vimusic.ui.items.AlbumItem
import it.vfsfitvnm.vimusic.ui.items.AlbumItemPlaceholder
import it.vfsfitvnm.vimusic.ui.items.ArtistItem
import it.vfsfitvnm.vimusic.ui.items.ArtistItemPlaceholder
import it.vfsfitvnm.vimusic.ui.items.PlaylistItem
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** How many of the most-played songs Quick Picks may seed itself from. */
private const val SeedPoolSize = 20

/** Used when nothing has been played yet, so the page is not empty on a fresh install. */
private const val DefaultSeedVideoId = "J7p4bzqLvCw"

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

    LaunchedEffect(refreshToken) {
        if (refreshToken > 0) {
            relatedPageResult = null
            relatedPageResult = loadLanguageOrderedRelatedPage(trending)
            isRefreshing = false
        }
    }

    LaunchedEffect(Unit) {
        Database.trending(SeedPoolSize).distinctUntilChanged().collect { candidates ->
            // A seed is drawn at random from the most-played rather than always being the single
            // top song, so one album in heavy rotation cannot own the page indefinitely.
            //
            // Once drawn it is kept for as long as it remains in the pool: this flow re-emits on
            // every play event, and re-drawing each time would reshuffle the screen underneath
            // whoever is reading it. Leaving home clears the persisted value, so the next visit
            // draws again.
            val current = trending
            val seed = current?.takeIf { song -> candidates.any { it.id == song.id } }
                ?: candidates.randomOrNull()

            if (!hasLoadedFreshRecommendations || seed?.id != current?.id) {
                // Discard recommendations persisted by older builds; they may start with English.
                relatedPageResult = null
                relatedPageResult = loadLanguageOrderedRelatedPage(seed)
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
                text = "SONG FOR ME",
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
                    text = "An error has occurred",
                    style = typography.s.secondary.center,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
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

        // One combined shelf: merge top-chart and trending discovery intent.
        // YouTube Music search does not expose a guaranteed live chart rank here.
        OnlineSongShelf(
            title = "TRENDING SONG",
            query = "trending top chart popular hit songs India Hindi Marathi Punjabi Tamil Telugu Bengali Kannada Malayalam English 2026",
            thumbnailSizeDp = songThumbnailSizeDp,
            thumbnailSizePx = songThumbnailSizePx,
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
            sectionTextModifier = sectionTextModifier,
            refreshKey = refreshToken
        )

        OnlineSongShelf(
            title = "NEW RELEASE",
            query = "new Hindi Marathi Indian regional English music releases",
            thumbnailSizeDp = songThumbnailSizeDp,
            thumbnailSizePx = songThumbnailSizePx,
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
            sectionTextModifier = sectionTextModifier,
            refreshKey = refreshToken
        )

        OnlinePlaylistShelf(
            title = "MIX FOR ME",
            query = "${trending?.artistsText.orEmpty()} Hindi Marathi Indian regional language personalized music mix",
            filter = Innertube.SearchFilter.FeaturedPlaylist.value,
            thumbnailSizeDp = playlistThumbnailSizeDp,
            thumbnailSizePx = playlistThumbnailSizePx,
            onPlaylistClick = onPlaylistClick,
            sectionTextModifier = sectionTextModifier,
            refreshKey = refreshToken
        )
        relatedPageResult?.getOrNull()?.let { related ->
            related.playlists?.let { playlists ->
                    BasicText(
                        text = "PLAYLISTS YOU MIGHT LIKE",
                        style = typography.s.semiBold,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(top = 24.dp, bottom = 8.dp)
                    )

                    LazyRow(contentPadding = endPaddingValues) {
                        items(
                            items = playlists,
                            key = Innertube.PlaylistItem::key,
                        ) { playlist ->
                            PlaylistItem(
                                playlist = playlist,
                                thumbnailSizePx = playlistThumbnailSizePx,
                                thumbnailSizeDp = playlistThumbnailSizeDp,
                                alternative = true,
                                modifier = Modifier
                                    .clickable(onClick = { onPlaylistClick(playlist.key) })
                            )
                        }
                    }
                }


                related.albums?.let { albums ->
                    BasicText(
                        text = "RELATED ALBUMS",
                        style = typography.s.semiBold,
                        modifier = sectionTextModifier
                    )

                    LazyRow(contentPadding = endPaddingValues) {
                        items(
                            items = albums,
                            key = Innertube.AlbumItem::key
                        ) { album ->
                            AlbumItem(
                                album = album,
                                thumbnailSizePx = albumThumbnailSizePx,
                                thumbnailSizeDp = albumThumbnailSizeDp,
                                alternative = true,
                                modifier = Modifier
                                    .clickable(onClick = { onAlbumClick(album.key) })
                            )
                        }
                    }
                }

                related.artists?.let { artists ->
                    BasicText(
                        text = "SIMILAR ARTISTS",
                        style = typography.s.semiBold,
                        modifier = sectionTextModifier
                    )

                    LazyRow(contentPadding = endPaddingValues) {
                        items(
                            items = artists,
                            key = Innertube.ArtistItem::key,
                        ) { artist ->
                            ArtistItem(
                                artist = artist,
                                thumbnailSizePx = artistThumbnailSizePx,
                                thumbnailSizeDp = artistThumbnailSizeDp,
                                alternative = true,
                                modifier = Modifier
                                    .clickable(onClick = { onArtistClick(artist.key) })
                            )
                        }
                    }
                }

            }

        OnlinePlaylistShelf(
            title = "TRENDING COMMUNITY PLAYLIST",
            query = "trending Indian community music playlists Hindi Marathi Punjabi Tamil Telugu",
            filter = Innertube.SearchFilter.CommunityPlaylist.value,
            thumbnailSizeDp = playlistThumbnailSizeDp,
            thumbnailSizePx = playlistThumbnailSizePx,
            onPlaylistClick = onPlaylistClick,
            sectionTextModifier = sectionTextModifier,
            refreshKey = refreshToken
        )

    }
        PrimaryButton(
            iconId = R.drawable.search,
            onClick = onSearchClick,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 88.dp)
        )
    }
    }
}


private suspend fun loadLanguageOrderedRelatedPage(
    seed: Song?
): Result<Innertube.RelatedPage?> {
    val baseResult = Innertube.relatedPage(
        NextBody(videoId = seed?.id ?: DefaultSeedVideoId)
    ) ?: return Result.success(null)
    val base = baseResult.getOrNull() ?: return baseResult

    // Keep Song for me personalized to the current listening seed, but request each
    // language group explicitly so English results can never lead the shelf.
    val seedContext = listOfNotNull(seed?.title, seed?.artistsText)
        .joinToString(" ")
        .takeIf { it.isNotBlank() }
        ?: "popular"

    suspend fun fetchRelatedSongs(languageQuery: String) = Innertube.searchPage(
        body = SearchBody(
            query = "$languageQuery similar to $seedContext",
            params = Innertube.SearchFilter.Song.value
        ),
        fromMusicShelfRendererContent = Innertube.SongItem::from
    )?.getOrNull()?.items.orEmpty()

    // Keep the exact language priority requested across recommendation shelves.
    val hindi = fetchRelatedSongs("Hindi Bollywood songs")
    val marathi = fetchRelatedSongs("Marathi songs")
        .filterNot { song -> hindi.any { it.key == song.key } }
    val otherIndianLanguages = fetchRelatedSongs(
        "Punjabi Tamil Telugu Bengali Kannada Malayalam Indian regional songs"
    )
    val indianSongs = (hindi + marathi + otherIndianLanguages).distinctBy { it.key }
    val english = fetchRelatedSongs("English international songs")
        .filterNot { song -> indianSongs.any { it.key == song.key } }

    val orderedSongs = indianSongs + english

    // Never fall back to the unfiltered related list: it can put English tracks first.
    // An empty language search is preferable to violating the requested language order.
    return Result.success(base.copy(songs = orderedSongs))
}

@Composable
private fun OnlineSongShelf(
    title: String,
    query: String,
    thumbnailSizeDp: androidx.compose.ui.unit.Dp,
    thumbnailSizePx: Int,
    onSongClick: (Innertube.SongItem) -> Unit,
    onSongLongClick: (Innertube.SongItem) -> Unit,
    sectionTextModifier: Modifier,
    refreshKey: Int
) {
    val (_, typography) = LocalAppearance.current
    var songs by remember(query, refreshKey) { mutableStateOf<List<Innertube.SongItem>?>(null) }

    LaunchedEffect(query, refreshKey) {
        songs = withContext(Dispatchers.IO) {
            // New Release must target the current release period, not a timeless
            // "new music" search that can keep surfacing old catalogue tracks.
            // Keep the existing language order: Hindi, other Indian languages, English.
            val releasePeriod = LocalDate.now().format(
                DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
            )
            val isNewRelease = title.equals("NEW RELEASE", ignoreCase = true)
            fun languageQuery(language: String): String = if (isNewRelease) {
                "latest new $language songs released $releasePeriod official"
            } else {
                "$language songs India $query"
            }

            suspend fun fetchSongs(searchQuery: String) = Innertube.searchPage(
                body = SearchBody(
                    query = searchQuery,
                    params = Innertube.SearchFilter.Song.value
                ),
                fromMusicShelfRendererContent = Innertube.SongItem::from
            )?.getOrNull()?.items.orEmpty()

            val hindi = fetchSongs(languageQuery("Hindi Bollywood"))
            val marathi = fetchSongs(languageQuery("Marathi"))
                .filterNot { song -> hindi.any { it.key == song.key } }
            val otherIndianLanguages = fetchSongs(
                if (isNewRelease) {
                    "latest new Punjabi Tamil Telugu Bengali Kannada Malayalam songs released $releasePeriod official"
                } else {
                    "Punjabi Tamil Telugu Bengali Kannada Malayalam Indian regional songs $query"
                }
            )
            val indianSongs = (hindi + marathi + otherIndianLanguages).distinctBy { it.key }
            val english = fetchSongs(languageQuery("English international"))
                .filterNot { song -> indianSongs.any { it.key == song.key } }

            indianSongs + english
        }
    }

    BasicText(text = title, style = typography.s.semiBold, modifier = sectionTextModifier)
    if (songs == null) {
        ShimmerHost {
            repeat(2) { SongItemPlaceholder(thumbnailSizeDp = thumbnailSizeDp) }
        }
    } else {
        BoxWithConstraints {
            val itemWidth = maxWidth * 0.9f
            val gridState = rememberLazyGridState()
            LazyHorizontalGrid(
                state = gridState,
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
                            .width(itemWidth)
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

@Composable
private fun OnlinePlaylistShelf(
    title: String,
    query: String,
    filter: String,
    thumbnailSizeDp: androidx.compose.ui.unit.Dp,
    thumbnailSizePx: Int,
    onPlaylistClick: (String) -> Unit,
    sectionTextModifier: Modifier,
    refreshKey: Int
) {
    val (_, typography) = LocalAppearance.current
    var playlists by remember(query, refreshKey) { mutableStateOf<List<Innertube.PlaylistItem>?>(null) }

    LaunchedEffect(query, filter, refreshKey) {
        val result = withContext(Dispatchers.IO) {
            Innertube.searchPage(
                body = SearchBody(query = query, params = filter),
                fromMusicShelfRendererContent = Innertube.PlaylistItem::from
            )
        }
        playlists = result?.getOrNull()?.items.orEmpty()
    }

    BasicText(text = title, style = typography.s.semiBold, modifier = sectionTextModifier)
    if (playlists == null) {
        ShimmerHost {
            repeat(2) { PlaylistItemPlaceholder(thumbnailSizeDp = thumbnailSizeDp, alternative = true) }
        }
    } else {
        LazyRow {
            items(playlists.orEmpty(), key = Innertube.PlaylistItem::key) { playlist ->
                PlaylistItem(
                    playlist = playlist,
                    thumbnailSizePx = thumbnailSizePx,
                    thumbnailSizeDp = thumbnailSizeDp,
                    alternative = true,
                    modifier = Modifier.clickable(onClick = { onPlaylistClick(playlist.key) })
                )
            }
        }
    }
}
