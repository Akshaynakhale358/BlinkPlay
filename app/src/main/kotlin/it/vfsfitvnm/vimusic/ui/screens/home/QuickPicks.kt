package it.vfsfitvnm.vimusic.ui.screens.home

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
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
import it.vfsfitvnm.innertube.models.Context
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** How many of the most-played songs Quick Picks may seed itself from. */
private const val SeedPoolSize = 20

/** Used when nothing has been played yet, so the page is not empty on a fresh install. */
private const val DefaultSeedVideoId = "J7p4bzqLvCw"

// Each date selects a different 10-theme pack. Adjacent days never repeat a theme;
// the six-pack cycle keeps the daily list deterministic for everyone on that date.
private data class DailyMixSpec(val title: String, val query: String)

private val DailyMixThemes = listOf(
    // Pack 1
    DailyMixSpec("Intense Love", "Hindi romantic intense love songs playlist"),
    DailyMixSpec("Today's Top 10", "India current top 10 hit songs playlist"),
    DailyMixSpec("Bollywood Gold", "classic golden era Bollywood Hindi songs playlist"),
    DailyMixSpec("Marathi Mania", "popular Marathi hit songs playlist"),
    DailyMixSpec("Broken Hearts", "Hindi sad breakup heartbreak songs playlist"),
    DailyMixSpec("Chill & Relax", "Indian chill relaxing soft music playlist"),
    DailyMixSpec("Punjabi Swag", "Punjabi hit songs playlist"),
    DailyMixSpec("Desi Party", "Hindi Bollywood dance party songs playlist"),
    DailyMixSpec("Midnight Romance", "Hindi late night romantic songs playlist"),
    DailyMixSpec("Retro Rewind", "old Hindi retro Bollywood songs playlist"),

    // Pack 2
    DailyMixSpec("Romantic Nights", "Hindi romantic night songs playlist"),
    DailyMixSpec("Viral India Hits", "currently viral Indian music hits playlist"),
    DailyMixSpec("2000s Bollywood", "2000s Bollywood Hindi hit songs playlist"),
    DailyMixSpec("Marathi DJ Beats", "Marathi DJ remix dance songs playlist"),
    DailyMixSpec("Sad Soul", "Hindi emotional sad songs playlist"),
    DailyMixSpec("Lo-Fi Dreams", "Indian Hindi lo-fi chill beats playlist"),
    DailyMixSpec("Punjabi Fire", "latest Punjabi bhangra hit songs playlist"),
    DailyMixSpec("Party Anthems", "Bollywood high energy party dance songs playlist"),
    DailyMixSpec("Acoustic Love", "Hindi acoustic romantic unplugged songs playlist"),
    DailyMixSpec("90s Classics", "1990s classic Hindi Bollywood songs playlist"),

    // Pack 3
    DailyMixSpec("Love Unplugged", "Hindi unplugged love songs playlist"),
    DailyMixSpec("Chartbusters India", "India latest chartbuster hit songs playlist"),
    DailyMixSpec("Evergreen Hindi", "evergreen all time Hindi film songs playlist"),
    DailyMixSpec("Marathi Melodies", "Marathi melodious romantic songs playlist"),
    DailyMixSpec("Heartbreak Hindi", "Hindi heartbreak pain sad songs playlist"),
    DailyMixSpec("Peaceful Mornings", "peaceful Indian instrumental and soft songs playlist"),
    DailyMixSpec("Bhangra Beats", "Punjabi bhangra dance hits playlist"),
    DailyMixSpec("Wedding Dance Hits", "Indian wedding sangeet dance songs playlist"),
    DailyMixSpec("Rainy Day Songs", "Hindi monsoon rainy day romantic songs playlist"),
    DailyMixSpec("Indie Discoveries", "Indian independent indie pop songs playlist"),

    // Pack 4
    DailyMixSpec("Soft Love", "Hindi soft romantic love songs playlist"),
    DailyMixSpec("India Viral Mix", "India trending viral songs this week playlist"),
    DailyMixSpec("Bollywood 2010s", "2010 to 2019 Bollywood Hindi hit songs playlist"),
    DailyMixSpec("Marathi Love Songs", "Marathi romantic love songs playlist"),
    DailyMixSpec("Emotional Nights", "Hindi emotional slow sad songs playlist"),
    DailyMixSpec("Ambient Focus", "ambient focus study instrumental playlist"),
    DailyMixSpec("Punjabi Romance", "Punjabi romantic love songs playlist"),
    DailyMixSpec("Sangeet Special", "Indian sangeet wedding dance songs playlist"),
    DailyMixSpec("Coffee House Acoustic", "acoustic cafe soft indie songs playlist"),
    DailyMixSpec("Classic 80s Hindi", "1980s classic Hindi Bollywood songs playlist"),

    // Pack 5
    DailyMixSpec("Love Forever", "evergreen Hindi romantic love songs playlist"),
    DailyMixSpec("Global Top Hits", "global current top chart hits playlist"),
    DailyMixSpec("Bollywood New Wave", "latest Bollywood Hindi soundtrack hits playlist"),
    DailyMixSpec("Marathi Folk Fusion", "Marathi folk fusion popular songs playlist"),
    DailyMixSpec("Lonely Nights", "Hindi lonely heartbreak sad songs playlist"),
    DailyMixSpec("Rainy Chill", "relaxing monsoon rainy day chill songs playlist"),
    DailyMixSpec("Punjabi Pop Party", "Punjabi pop party dance hits playlist"),
    DailyMixSpec("Dance Floor Anthems", "Bollywood dance floor party anthems playlist"),
    DailyMixSpec("Late Night Acoustic", "late night Hindi acoustic soft songs playlist"),
    DailyMixSpec("Golden 70s", "1970s golden era Hindi film songs playlist"),

    // Pack 6
    DailyMixSpec("Heartfelt Duets", "Hindi romantic duet songs playlist"),
    DailyMixSpec("Fresh Finds India", "new Indian music discoveries playlist"),
    DailyMixSpec("Bollywood Unplugged", "Bollywood unplugged acoustic covers playlist"),
    DailyMixSpec("Marathi Superhits", "all time Marathi superhit songs playlist"),
    DailyMixSpec("Tearjerker Tunes", "Hindi emotional tearjerker sad songs playlist"),
    DailyMixSpec("Calm Instrumentals", "peaceful Indian instrumental music playlist"),
    DailyMixSpec("Punjabi Beats", "Punjabi upbeat bhangra beats playlist"),
    DailyMixSpec("Festival Dhamaka", "Indian festival celebration dance songs playlist"),
    DailyMixSpec("Soulful Romance", "soulful Hindi romantic melody songs playlist"),
    DailyMixSpec("Vintage Hindi Gems", "vintage old Hindi Bollywood gems playlist")
)

private val IndiaMusicContext = Context.DefaultWeb.copy(
    client = Context.DefaultWeb.client.copy(hl = "hi", gl = "IN")
)

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
    var tasteSeeds by remember { mutableStateOf<List<Song>>(emptyList()) }

    var relatedPageResult by persist<Result<Innertube.RelatedPage?>?>(tag = "home/relatedPageResult")
    var refreshToken by remember { mutableStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    var hasLoadedFreshRecommendations by remember { mutableStateOf(false) }

    LaunchedEffect(refreshToken) {
        if (refreshToken > 0) {
            relatedPageResult = null
            relatedPageResult = loadLanguageOrderedRelatedPage(trending, tasteSeeds)
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
            // Database.trending is already ranked by recency-weighted listening time.
            // Keep several top taste signals so one repeatedly played track cannot define
            // the entire For You shelf.
            tasteSeeds = candidates.take(4)
            val seed = current?.takeIf { song -> candidates.any { it.id == song.id } }
                ?: candidates.firstOrNull()

            if (!hasLoadedFreshRecommendations || seed?.id != current?.id || tasteSeeds.isNotEmpty()) {
                // Build recommendations from several recently enjoyed tracks, not one random seed.
                relatedPageResult = null
                relatedPageResult = loadLanguageOrderedRelatedPage(seed, tasteSeeds)
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
                text = "SONGS FOR YOU",
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

        // Keep chart-style Top Song and currently popular Trending Song as separate shelves.
        // YouTube Music search does not expose a guaranteed live chart rank here.
        OnlineSongShelf(
            title = "TOP SONG",
            query = "top chart highest ranked most popular hit songs India Hindi Marathi Punjabi Tamil Telugu Bengali Kannada Malayalam",
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
            title = "TRENDING SONG",
            query = "trending now viral popular songs India Hindi Marathi Punjabi Tamil Telugu Bengali Kannada Malayalam ${LocalDate.now().year}",
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

        MixForYouShelf(
            seedArtists = trending?.artistsText.orEmpty(),
            thumbnailSizeDp = playlistThumbnailSizeDp,
            thumbnailSizePx = playlistThumbnailSizePx,
            onPlaylistClick = onPlaylistClick,
            sectionTextModifier = sectionTextModifier,
            refreshKey = refreshToken
        )

        // Podcast discovery is deliberately separated from music shelves. These searches use
        // YouTube's Video filter so full talks and podcast episodes are not mixed with songs.
        PodcastTopicShelf(
            title = "OSHO DISCOURSES",
            query = "Osho full discourse podcast Hindi English official talk",
            thumbnailSizeDp = songThumbnailSizeDp,
            thumbnailSizePx = songThumbnailSizePx,
            sectionTextModifier = sectionTextModifier,
            refreshKey = refreshToken
        )
        PodcastTopicShelf(
            title = "SPIRITUALITY & MEDITATION",
            query = "spirituality meditation mindfulness philosophy podcast Hindi Marathi",
            thumbnailSizeDp = songThumbnailSizeDp,
            thumbnailSizePx = songThumbnailSizePx,
            sectionTextModifier = sectionTextModifier,
            refreshKey = refreshToken
        )
        PodcastTopicShelf(
            title = "MOTIVATION & SELF IMPROVEMENT",
            query = "motivation self improvement discipline success podcast Hindi Marathi",
            thumbnailSizeDp = songThumbnailSizeDp,
            thumbnailSizePx = songThumbnailSizePx,
            sectionTextModifier = sectionTextModifier,
            refreshKey = refreshToken
        )
        PodcastTopicShelf(
            title = "KNOWLEDGE & INTERVIEWS",
            query = "educational knowledge science history technology long form podcast India",
            thumbnailSizeDp = songThumbnailSizeDp,
            thumbnailSizePx = songThumbnailSizePx,
            sectionTextModifier = sectionTextModifier,
            refreshKey = refreshToken
        )
        PodcastTopicShelf(
            title = "MARATHI PODCASTS",
            query = "Marathi podcast मुलाखत विचार ज्ञान गप्पा full episode",
            thumbnailSizeDp = songThumbnailSizeDp,
            thumbnailSizePx = songThumbnailSizePx,
            sectionTextModifier = sectionTextModifier,
            refreshKey = refreshToken
        )

        var dailyMixDay by remember { mutableStateOf(LocalDate.now().toEpochDay()) }
        LaunchedEffect(Unit) {
            // Keep the shelf current even if Quick Picks stays open across midnight.
            while (true) {
                delay(60_000L)
                val today = LocalDate.now().toEpochDay()
                if (today != dailyMixDay) dailyMixDay = today
            }
        }
        // Ten disjoint themes per day: every title changes on the next day.
        // The six themed packs rotate as a six-day cycle, then start a fresh cycle.
        val dailyMixPack = Math.floorMod(dailyMixDay, (DailyMixThemes.size / 10).toLong()).toInt()
        val dailyMixes = remember(dailyMixDay) {
            DailyMixThemes.drop(dailyMixPack * 10).take(10)
        }

        BasicText(
            text = "DAILY MIXES",
            style = typography.s.semiBold,
            modifier = sectionTextModifier
        )
        dailyMixes.chunked(2).forEach { rowMixes ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                rowMixes.forEach { mix ->
                    DailyMixPlaylistCard(
                        mix = mix,
                        dayKey = dailyMixDay,
                        dayLabel = LocalDate.ofEpochDay(dailyMixDay).format(DateTimeFormatter.ofPattern("d MMM")),
                        refreshKey = refreshToken,
                        thumbnailSizeDp = playlistThumbnailSizeDp,
                        thumbnailSizePx = playlistThumbnailSizePx,
                        onPlaylistClick = onPlaylistClick
                    )
                }
            }
        }
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
    }
    }
}


@Composable
private fun PodcastTopicShelf(
    title: String,
    query: String,
    thumbnailSizeDp: androidx.compose.ui.unit.Dp,
    thumbnailSizePx: Int,
    sectionTextModifier: Modifier,
    refreshKey: Int
) {
    val (_, typography) = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current
    var episodes by remember(query, refreshKey) {
        mutableStateOf<List<Innertube.SongItem>?>(null)
    }

    LaunchedEffect(query, refreshKey) {
        episodes = try {
            withContext(Dispatchers.IO) {
                Innertube.searchPage(
                    body = SearchBody(
                        context = IndiaMusicContext,
                        query = query,
                        params = Innertube.SearchFilter.Video.value
                    ),
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
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
    }

    BasicText(
        text = title,
        style = typography.s.semiBold,
        modifier = sectionTextModifier
    )

    LazyRow {
        if (episodes == null) {
            items(4) {
                SongItemPlaceholder(thumbnailSizeDp = thumbnailSizeDp)
            }
        } else {
            items(episodes.orEmpty(), key = Innertube.SongItem::key) { episode ->
                SongItem(
                    song = episode,
                    thumbnailSizePx = thumbnailSizePx,
                    thumbnailSizeDp = thumbnailSizeDp,
                    modifier = Modifier.combinedClickable(
                        onLongClick = {
                            menuState.display {
                                NonQueuedMediaItemMenu(
                                    onDismiss = menuState::hide,
                                    mediaItem = episode.asMediaItem
                                )
                            }
                        },
                        onClick = {
                            val mediaItem = episode.asMediaItem
                            binder?.stopRadio()
                            binder?.player?.forcePlay(mediaItem)
                            binder?.setupRadio(
                                NavigationEndpoint.Endpoint.Watch(videoId = mediaItem.mediaId)
                            )
                        }
                    )
                )
            }
        }
    }
}

private suspend fun loadLanguageOrderedRelatedPage(
    seed: Song?,
    tasteSeeds: List<Song> = listOfNotNull(seed)
): Result<Innertube.RelatedPage?> {
    return try {
        val baseResult = Innertube.relatedPage(
            NextBody(videoId = seed?.id ?: DefaultSeedVideoId)
        ) ?: return Result.failure(
            IllegalStateException("Related recommendations are unavailable")
        )

        if (baseResult.isFailure) return baseResult

        val base = baseResult.getOrNull()
            ?: return Result.failure(
                IllegalStateException("Related recommendations are empty")
            )

        // Keep Song for me personalized to the current listening seed, but request each
        // language group explicitly so English results can never lead the shelf.
        val seedContext = tasteSeeds
            .take(4)
            .flatMap { song -> listOfNotNull(song.title, song.artistsText) }
            .joinToString(" ")
            .takeIf { it.isNotBlank() }
            ?: listOfNotNull(seed?.title, seed?.artistsText).joinToString(" ")
                .takeIf { it.isNotBlank() }
            ?: "popular"

        suspend fun fetchRelatedSongs(languageQuery: String) = Innertube.searchPage(
            body = SearchBody(
                context = IndiaMusicContext,
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
        Result.success(base.copy(songs = orderedSongs))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }
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
        songs = try {
            withContext(Dispatchers.IO) {
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
                    context = IndiaMusicContext,
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
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
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
private fun MixForYouShelf(
    seedArtists: String,
    thumbnailSizeDp: androidx.compose.ui.unit.Dp,
    thumbnailSizePx: Int,
    onPlaylistClick: (String) -> Unit,
    sectionTextModifier: Modifier,
    refreshKey: Int
) {
    val (_, typography) = LocalAppearance.current
    var playlists by remember(seedArtists, refreshKey) {
        mutableStateOf<List<Innertube.PlaylistItem>?>(null)
    }

    LaunchedEffect(seedArtists, refreshKey) {
        playlists = try {
            withContext(Dispatchers.IO) {
            val seed = seedArtists.takeIf { it.isNotBlank() } ?: "popular Indian music"
            val languageQueries = listOf(
                "$seed Hindi Bollywood songs playlist mix",
                "$seed Marathi songs playlist mix",
                "$seed Punjabi Tamil Telugu Bengali Kannada Malayalam Indian regional playlist mix",
                "$seed English international songs playlist mix"
            )

            suspend fun fetch(query: String) = Innertube.searchPage(
                body = SearchBody(
                    context = IndiaMusicContext,
                    query = query,
                    params = Innertube.SearchFilter.FeaturedPlaylist.value
                ),
                fromMusicShelfRendererContent = Innertube.PlaylistItem::from
            )?.getOrNull()?.items.orEmpty()

            val languageOrdered = mutableListOf<Innertube.PlaylistItem>()
            for (query in languageQueries) {
                languageOrdered += fetch(query)
            }
            val uniqueLanguageOrdered = languageOrdered.distinctBy { it.key }

            if (uniqueLanguageOrdered.isNotEmpty()) {
                uniqueLanguageOrdered
            } else {
                Innertube.searchPage(
                    body = SearchBody(
                        context = IndiaMusicContext,
                        query = "$seed Indian Hindi Marathi regional music mix playlist",
                        params = Innertube.SearchFilter.CommunityPlaylist.value
                    ),
                    fromMusicShelfRendererContent = Innertube.PlaylistItem::from
                )?.getOrNull()?.items.orEmpty()
            }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
    }

    BasicText(text = "MIX FOR YOU", style = typography.s.semiBold, modifier = sectionTextModifier)
    if (playlists == null) {
        ShimmerHost {
            repeat(2) {
                PlaylistItemPlaceholder(thumbnailSizeDp = thumbnailSizeDp, alternative = true)
            }
        }
    } else {
        LazyRow {
            items(playlists.orEmpty(), key = Innertube.PlaylistItem::key) { playlist ->
                PlaylistItem(
                    playlist = playlist,
                    thumbnailSizePx = thumbnailSizePx,
                    thumbnailSizeDp = thumbnailSizeDp,
                    alternative = true,
                    modifier = Modifier.clickable { onPlaylistClick(playlist.key) }
                )
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
        val result = try {
            withContext(Dispatchers.IO) {
            Innertube.searchPage(
                body = SearchBody(context = IndiaMusicContext, query = query, params = filter),
                fromMusicShelfRendererContent = Innertube.PlaylistItem::from
            )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
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



@Composable
private fun DailyMixPlaylistCard(
    mix: DailyMixSpec,
    dayKey: Long,
    dayLabel: String,
    refreshKey: Int,
    thumbnailSizeDp: androidx.compose.ui.unit.Dp,
    thumbnailSizePx: Int,
    onPlaylistClick: (String) -> Unit
) {
    var playlist by remember(mix.query, dayKey, refreshKey) {
        mutableStateOf<Innertube.PlaylistItem?>(null)
    }
    var loaded by remember(mix.query, dayKey, refreshKey) { mutableStateOf(false) }

    LaunchedEffect(mix.query, dayKey, refreshKey) {
        playlist = try {
            withContext(Dispatchers.IO) {
            suspend fun fetch(query: String, filter: String) = Innertube.searchPage(
                body = SearchBody(
                    context = IndiaMusicContext,
                    query = query,
                    params = filter
                ),
                fromMusicShelfRendererContent = Innertube.PlaylistItem::from
            )?.getOrNull()?.items.orEmpty()

            // Featured playlists are preferred, but category searches often return better
            // matches from community playlists. Try that filter before marking a mix unavailable.
            val results = fetch(mix.query, Innertube.SearchFilter.FeaturedPlaylist.value)
                .ifEmpty {
                    fetch(mix.query, Innertube.SearchFilter.CommunityPlaylist.value)
                }.ifEmpty {
                    fetch("${mix.query} official music", Innertube.SearchFilter.CommunityPlaylist.value)
                }
            if (results.isEmpty()) null else {
                val index = Math.floorMod(dayKey, results.size.toLong()).toInt()
                results[index]
            }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        loaded = true
    }

    when {
        playlist != null -> {
            val result = playlist!!
            PlaylistItem(
                thumbnailUrl = result.thumbnail?.url,
                songCount = result.songCount,
                name = "${mix.title} · $dayLabel",
                channelName = null,
                thumbnailSizePx = thumbnailSizePx,
                thumbnailSizeDp = thumbnailSizeDp,
                alternative = true,
                modifier = Modifier.clickable { onPlaylistClick(result.key) }
            )
        }
        !loaded -> PlaylistItemPlaceholder(
            thumbnailSizeDp = thumbnailSizeDp,
            alternative = true
        )
        else -> {
            BasicText(
                text = "${mix.title} unavailable",
                style = LocalAppearance.current.typography.xs,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}
