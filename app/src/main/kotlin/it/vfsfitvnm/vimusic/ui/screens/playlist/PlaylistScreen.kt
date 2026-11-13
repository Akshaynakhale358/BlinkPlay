package it.vfsfitvnm.vimusic.ui.screens.playlist

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import it.vfsfitvnm.compose.persist.PersistMapCleanup
import it.vfsfitvnm.compose.routing.RouteHandler
import it.vfsfitvnm.vimusic.R
import it.vfsfitvnm.vimusic.ui.components.themed.Scaffold
import it.vfsfitvnm.vimusic.ui.screens.globalRoutes
import it.vfsfitvnm.vimusic.ui.screens.albumRoute
import it.vfsfitvnm.vimusic.ui.screens.artistRoute
import it.vfsfitvnm.vimusic.ui.screens.playlistRoute
import it.vfsfitvnm.vimusic.ui.screens.browse.BrowseCategoryRoutePrefix
import it.vfsfitvnm.vimusic.ui.screens.browse.BrowseCategoryScreen

@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
fun PlaylistScreen(browseId: String) {
    val isBrowseCategory = browseId.startsWith(BrowseCategoryRoutePrefix)
    val routeValue = if (isBrowseCategory) browseId.removePrefix(BrowseCategoryRoutePrefix) else browseId
    val parts = routeValue.split("|||BLINK_PARAMS|||", limit = 2)
    val actualBrowseId = parts.first()
    val browseParams = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
    val saveableStateHolder = rememberSaveableStateHolder()
    PersistMapCleanup(tagPrefix = "playlist/$browseId")

    RouteHandler(listenToGlobalEmitter = true) {
        globalRoutes()

        host {
            Scaffold(
                topIconButtonId = R.drawable.chevron_back,
                onTopIconButtonClick = pop,
                tabIndex = 0,
                onTabChanged = { },
                tabColumnContent = { Item ->
                    Item(0, "Songs", R.drawable.musical_notes)
                }
            ) { currentTabIndex ->
                saveableStateHolder.SaveableStateProvider(key = currentTabIndex) {
                    when (currentTabIndex) {
                        0 -> if (isBrowseCategory) {
                            BrowseCategoryScreen(
                                browseId = actualBrowseId,
                                params = browseParams,
                                onAlbumClick = { albumRoute(it) },
                                onArtistClick = { artistRoute(it) },
                                onPlaylistClick = { playlistRoute(it) }
                            )
                        } else {
                            PlaylistSongList(browseId = actualBrowseId, params = browseParams)
                        }
                    }
                }
            }
        }
    }
}
