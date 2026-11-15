package it.vfsfitvnm.vimusic.ui.screens.artist

import android.content.Intent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.BasicText
import com.valentinilk.shimmer.shimmer
import it.vfsfitvnm.compose.persist.PersistMapCleanup
import it.vfsfitvnm.compose.persist.persist
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.bodies.BrowseBody
import it.vfsfitvnm.innertube.models.bodies.ContinuationBody
import it.vfsfitvnm.innertube.requests.metrolistArtistPage
import it.vfsfitvnm.innertube.requests.itemsPage
import it.vfsfitvnm.innertube.utils.from
import it.vfsfitvnm.compose.routing.RouteHandler
import it.vfsfitvnm.vimusic.Database
import it.vfsfitvnm.vimusic.LocalPlayerServiceBinder
import it.vfsfitvnm.vimusic.R
import it.vfsfitvnm.vimusic.models.Artist
import it.vfsfitvnm.vimusic.query
import it.vfsfitvnm.vimusic.ui.components.LocalMenuState
import it.vfsfitvnm.vimusic.ui.components.themed.Header
import it.vfsfitvnm.vimusic.ui.components.themed.HeaderIconButton
import it.vfsfitvnm.vimusic.ui.components.themed.SecondaryTextButton
import it.vfsfitvnm.vimusic.ui.components.themed.HeaderPlaceholder
import it.vfsfitvnm.vimusic.ui.components.themed.NonQueuedMediaItemMenu
import it.vfsfitvnm.vimusic.ui.components.themed.Scaffold
import it.vfsfitvnm.vimusic.ui.components.themed.adaptiveThumbnailContent
import it.vfsfitvnm.vimusic.ui.items.AlbumItem
import it.vfsfitvnm.vimusic.ui.items.ArtistItem
import it.vfsfitvnm.vimusic.ui.items.PlaylistItem
import it.vfsfitvnm.vimusic.ui.items.AlbumItemPlaceholder
import it.vfsfitvnm.vimusic.ui.items.SongItem
import it.vfsfitvnm.vimusic.ui.items.SongItemPlaceholder
import it.vfsfitvnm.vimusic.ui.screens.albumRoute
import it.vfsfitvnm.vimusic.ui.screens.artistRoute
import it.vfsfitvnm.vimusic.ui.screens.playlistRoute
import it.vfsfitvnm.vimusic.ui.screens.globalRoutes
import it.vfsfitvnm.vimusic.ui.screens.searchresult.ItemsPage
import it.vfsfitvnm.vimusic.ui.styling.Dimensions
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.ui.styling.px
import it.vfsfitvnm.vimusic.utils.artistScreenTabIndexKey
import it.vfsfitvnm.vimusic.utils.asMediaItem
import it.vfsfitvnm.vimusic.utils.semiBold
import it.vfsfitvnm.vimusic.utils.forcePlay
import it.vfsfitvnm.vimusic.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
fun ArtistScreen(browseId: String) {
    val saveableStateHolder = rememberSaveableStateHolder()

    var tabIndex by rememberPreference(artistScreenTabIndexKey, defaultValue = 0)

    PersistMapCleanup(tagPrefix = "artist/$browseId/")

    var artist by persist<Artist?>("artist/$browseId/artist")

    var artistPage by persist<Innertube.ArtistPage?>("artist/$browseId/artistPage")

    LaunchedEffect(browseId) {
        Database
            .artist(browseId)
            .combine(snapshotFlow { tabIndex }.map { it != 8 }) { artist, mustFetch -> artist to mustFetch }
            .distinctUntilChanged()
            .collect { (currentArtist, mustFetch) ->
                artist = currentArtist

                if (artistPage == null && (currentArtist?.timestamp == null || mustFetch)) {
                    withContext(Dispatchers.IO) {
                        Innertube.metrolistArtistPage(BrowseBody(browseId = browseId))
                            ?.onSuccess { currentArtistPage ->
                                artistPage = currentArtistPage

                                Database.upsert(
                                    Artist(
                                        id = browseId,
                                        name = currentArtistPage.name,
                                        thumbnailUrl = currentArtistPage.thumbnail?.url,
                                        timestamp = System.currentTimeMillis(),
                                        bookmarkedAt = currentArtist?.bookmarkedAt
                                    )
                                )
                            }
                    }
                }
            }
    }

    RouteHandler(listenToGlobalEmitter = true) {
        globalRoutes()

        host {
            val thumbnailContent =
                adaptiveThumbnailContent(
                    artist?.timestamp == null,
                    artist?.thumbnailUrl,
                    CircleShape
                )

            val headerContent: @Composable (textButton: (@Composable () -> Unit)?) -> Unit =
                { textButton ->
                    if (artist?.timestamp == null) {
                        HeaderPlaceholder(
                            modifier = Modifier
                                .shimmer()
                        )
                    } else {
                        val (colorPalette) = LocalAppearance.current
                        val context = LocalContext.current

                        Header(title = artist?.name ?: "Unknown") {
                            textButton?.invoke()

                            Spacer(
                                modifier = Modifier
                                    .weight(1f)
                            )



                            HeaderIconButton(
                                icon = R.drawable.share_social,
                                color = colorPalette.text,
                                onClick = {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        type = "text/plain"
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "https://music.youtube.com/channel/$browseId"
                                        )
                                    }

                                    context.startActivity(Intent.createChooser(sendIntent, null))
                                }
                            )
                        }
                    }
                }

            Scaffold(
                topIconButtonId = R.drawable.chevron_back,
                onTopIconButtonClick = pop,
                tabIndex = tabIndex,
                onTabChanged = { tabIndex = it },
                tabColumnContent = { Item ->
                    Item(0, "Overview", R.drawable.sparkles)
                    Item(1, "Latest release", R.drawable.disc)
                    Item(2, "Top tracks", R.drawable.musical_notes)
                    Item(3, "Songs", R.drawable.musical_notes)
                    Item(4, "Albums", R.drawable.disc)
                    Item(5, "Singles & EPs", R.drawable.disc)
                    Item(6, "Appears on / Featured on", R.drawable.disc)
                    Item(7, "Fans might also like", R.drawable.person)
                    Item(8, "Library", R.drawable.library)
                },
            ) { currentTabIndex ->
                saveableStateHolder.SaveableStateProvider(key = currentTabIndex) {
                    when (currentTabIndex) {
                        0 -> ArtistOverview(
                            youtubeArtistPage = artistPage,
                            thumbnailContent = thumbnailContent,
                            headerContent = headerContent,
                            onAlbumClick = { albumRoute(it) },
                            onArtistClick = { artistRoute(it) },
                            onPlaylistClick = { playlistRoute(it) },
                            isSubscribed = artist?.bookmarkedAt != null,
                            onToggleSubscribed = {
                                val bookmarkedAt =
                                    if (artist?.bookmarkedAt == null) System.currentTimeMillis() else null

                                query {
                                    // Upsert rather than update: the row only exists once the
                                    // artist page has loaded and been stored, so tapping this
                                    // early used to silently do nothing at all.
                                    Database.upsert(
                                        artist?.copy(bookmarkedAt = bookmarkedAt)
                                            ?: Artist(
                                                id = browseId,
                                                name = artistPage?.name,
                                                thumbnailUrl = artistPage?.thumbnail?.url,
                                                timestamp = System.currentTimeMillis(),
                                                bookmarkedAt = bookmarkedAt
                                            )
                                    )
                                }
                            },
                            onViewAllSongsClick = { tabIndex = 3 },
                            onViewAllAlbumsClick = { tabIndex = 4 },
                            onViewAllSinglesClick = { tabIndex = 5 },
                        )

                        3 -> {
                            val binder = LocalPlayerServiceBinder.current
                            val menuState = LocalMenuState.current
                            val thumbnailSizeDp = Dimensions.thumbnails.song
                            val thumbnailSizePx = thumbnailSizeDp.px

                            ItemsPage(
                                tag = "artist/$browseId/songs",
                                headerContent = headerContent,
                                itemsPageProvider = artistPage?.let {
                                    ({ continuation ->
                                        continuation?.let {
                                            Innertube.itemsPage(
                                                body = ContinuationBody(continuation = continuation),
                                                fromMusicResponsiveListItemRenderer = Innertube.SongItem::from,
                                            )
                                        } ?: artistPage
                                            ?.songsEndpoint
                                            ?.takeIf { it.browseId != null }
                                            ?.let { endpoint ->
                                                Innertube.itemsPage(
                                                    body = BrowseBody(
                                                        browseId = endpoint.browseId!!,
                                                        params = endpoint.params,
                                                    ),
                                                    fromMusicResponsiveListItemRenderer = Innertube.SongItem::from,
                                                )
                                            }
                                        ?: Result.success(
                                            Innertube.ItemsPage(
                                                items = artistPage?.songs,
                                                continuation = null
                                            )
                                        )
                                    })
                                },
                                itemContent = { song ->
                                    SongItem(
                                        song = song,
                                        thumbnailSizeDp = thumbnailSizeDp,
                                        thumbnailSizePx = thumbnailSizePx,
                                        titleColor = Color.White,
                                        modifier = Modifier
                                            .combinedClickable(
                                                onLongClick = {
                                                    menuState.display {
                                                        NonQueuedMediaItemMenu(
                                                            onDismiss = menuState::hide,
                                                            mediaItem = song.asMediaItem,
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

                        4 -> {
                            val thumbnailSizeDp = 108.dp
                            val thumbnailSizePx = thumbnailSizeDp.px

                            ItemsPage(
                                tag = "artist/$browseId/albums",
                                headerContent = headerContent,
                                emptyItemsText = "This artist didn't release any album",
                                itemsPageProvider = artistPage?.let {
                                    ({ continuation ->
                                        continuation?.let {
                                            Innertube.itemsPage(
                                                body = ContinuationBody(continuation = continuation),
                                                fromMusicTwoRowItemRenderer = Innertube.AlbumItem::from,
                                            )
                                        } ?: artistPage
                                            ?.albumsEndpoint
                                            ?.takeIf { it.browseId != null }
                                            ?.let { endpoint ->
                                                Innertube.itemsPage(
                                                    body = BrowseBody(
                                                        browseId = endpoint.browseId!!,
                                                        params = endpoint.params,
                                                    ),
                                                    fromMusicTwoRowItemRenderer = Innertube.AlbumItem::from,
                                                )
                                            }
                                        ?: Result.success(
                                            Innertube.ItemsPage(
                                                items = artistPage?.albums,
                                                continuation = null
                                            )
                                        )
                                    })
                                },
                                itemContent = { album ->
                                    AlbumItem(
                                        album = album,
                                        thumbnailSizePx = thumbnailSizePx,
                                        thumbnailSizeDp = thumbnailSizeDp,
                                        titleColor = Color.White,
                                        modifier = Modifier
                                            .clickable(onClick = { albumRoute(album.key) })
                                    )
                                },
                                itemPlaceholderContent = {
                                    AlbumItemPlaceholder(thumbnailSizeDp = thumbnailSizeDp)
                                }
                            )
                        }

                        1 -> ArtistShelfPage("Latest release", artistPage?.findArtistSection("latest release", "latest releases", "new release", "new releases", "latest album", "latest albums"), headerContent, { albumRoute(it) }, { artistRoute(it) }, { playlistRoute(it) })
                        2 -> ArtistShelfPage("Top tracks", artistPage?.findArtistSection("top tracks", "top songs", "tracks"), headerContent, { albumRoute(it) }, { artistRoute(it) }, { playlistRoute(it) })
                        5 -> ArtistShelfPage("Singles & EPs", artistPage?.findArtistSection("singles & eps", "singles", "eps"), headerContent, { albumRoute(it) }, { artistRoute(it) }, { playlistRoute(it) })
                        6 -> ArtistShelfPage("Appears on / Featured on", artistPage?.findArtistSection("appears on", "featured on"), headerContent, { albumRoute(it) }, { artistRoute(it) }, { playlistRoute(it) })
                        7 -> ArtistShelfPage("Fans might also like", artistPage?.findArtistSection("fans might also like", "similar artists", "related artists"), headerContent, { albumRoute(it) }, { artistRoute(it) }, { playlistRoute(it) })
                        8 -> ArtistLocalSongs(
                            browseId = browseId,
                            headerContent = headerContent,
                            thumbnailContent = thumbnailContent,
                        )
                    }
                }
            }
        }
    }
}


private fun Innertube.ArtistPage.findArtistSection(vararg aliases: String): Innertube.ArtistSection? =
    sections.firstOrNull { section ->
        aliases.any { alias -> section.title.contains(alias, ignoreCase = true) }
    }

@Composable
private fun ArtistShelfPage(
    title: String,
    section: Innertube.ArtistSection?,
    headerContent: @Composable (textButton: (@Composable () -> Unit)?) -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
) {
    val (_, typography) = LocalAppearance.current
    val songSize = Dimensions.thumbnails.song
    val songSizePx = songSize.px
    val entitySize = 108.dp
    val entitySizePx = entitySize.px
    val binder = LocalPlayerServiceBinder.current
    val shelfItems = section?.items.orEmpty()

    // Lazy composition keeps large artist shelves responsive and avoids creating every row
    // and thumbnail at once when opening an artist tab.
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "artist-header") {
            headerContent(null)
        }
        item(key = "artist-shelf-title") {
            BasicText(
                text = title,
                style = typography.m.semiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
            )
        }

        if (shelfItems.isEmpty()) {
            item(key = "artist-shelf-empty") {
                BasicText(
                    text = "No $title available for this artist.",
                    style = typography.s,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            items(
                items = shelfItems,
                key = Innertube.Item::key
            ) { item ->
                when (item) {
                    is Innertube.SongItem -> SongItem(
                        song = item,
                        thumbnailSizeDp = songSize,
                        thumbnailSizePx = songSizePx,
                        titleColor = Color.White,
                        modifier = Modifier.clickable {
                            binder?.stopRadio()
                            binder?.player?.forcePlay(item.asMediaItem)
                            binder?.setupRadio(item.info?.endpoint)
                        }
                    )
                    is Innertube.AlbumItem -> AlbumItem(
                        album = item,
                        thumbnailSizePx = entitySizePx,
                        thumbnailSizeDp = entitySize,
                        alternative = true,
                        titleColor = Color.White,
                        modifier = Modifier.clickable { onAlbumClick(item.key) }
                    )
                    is Innertube.ArtistItem -> ArtistItem(
                        artist = item,
                        thumbnailSizePx = entitySizePx,
                        thumbnailSizeDp = entitySize,
                        alternative = true,
                        modifier = Modifier.clickable { onArtistClick(item.key) }
                    )
                    is Innertube.PlaylistItem -> PlaylistItem(
                        playlist = item,
                        thumbnailSizePx = entitySizePx,
                        thumbnailSizeDp = entitySize,
                        alternative = true,
                        modifier = Modifier.clickable { onPlaylistClick(item.key) }
                    )
                    else -> Unit
                }
            }
        }
    }
}
