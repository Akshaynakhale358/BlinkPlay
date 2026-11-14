package it.vfsfitvnm.vimusic.ui.screens.home

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import it.vfsfitvnm.compose.persist.PersistMapCleanup
import it.vfsfitvnm.compose.routing.RouteHandler
import it.vfsfitvnm.compose.routing.defaultStacking
import it.vfsfitvnm.compose.routing.defaultStill
import it.vfsfitvnm.compose.routing.defaultUnstacking
import it.vfsfitvnm.compose.routing.isStacking
import it.vfsfitvnm.compose.routing.isUnknown
import it.vfsfitvnm.compose.routing.isUnstacking
import it.vfsfitvnm.vimusic.Database
import it.vfsfitvnm.vimusic.enums.BuiltInPlaylist
import it.vfsfitvnm.vimusic.R
import it.vfsfitvnm.vimusic.models.SearchQuery
import it.vfsfitvnm.vimusic.query
import it.vfsfitvnm.vimusic.ui.components.themed.Scaffold
import it.vfsfitvnm.vimusic.ui.components.themed.PrimaryButton
import it.vfsfitvnm.vimusic.ui.components.themed.MiniPlayerActionButton
import it.vfsfitvnm.vimusic.ui.screens.albumRoute
import it.vfsfitvnm.vimusic.ui.screens.artistRoute
import it.vfsfitvnm.vimusic.ui.screens.builtInPlaylistRoute
import it.vfsfitvnm.vimusic.ui.screens.builtinplaylist.BuiltInPlaylistScreen
import it.vfsfitvnm.vimusic.ui.screens.globalRoutes
import it.vfsfitvnm.vimusic.ui.screens.localPlaylistRoute
import it.vfsfitvnm.vimusic.ui.screens.localplaylist.LocalPlaylistScreen
import it.vfsfitvnm.vimusic.ui.screens.playlistRoute
import it.vfsfitvnm.vimusic.ui.screens.search.SearchScreen
import it.vfsfitvnm.vimusic.ui.screens.searchResultRoute
import it.vfsfitvnm.vimusic.ui.screens.searchRoute
import it.vfsfitvnm.vimusic.ui.screens.searchresult.SearchResultScreen
import it.vfsfitvnm.vimusic.ui.screens.settings.SettingsScreen
import it.vfsfitvnm.vimusic.ui.screens.settingsRoute
import it.vfsfitvnm.vimusic.utils.homeScreenTabIndexKey
import it.vfsfitvnm.vimusic.utils.pauseSearchHistoryKey
import it.vfsfitvnm.vimusic.utils.preferences
import it.vfsfitvnm.vimusic.utils.rememberPreference

@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
fun HomeScreen(onPlaylistUrl: (String) -> Unit) {
    val saveableStateHolder = rememberSaveableStateHolder()

    PersistMapCleanup("home/")

    RouteHandler(
        listenToGlobalEmitter = true,
        transitionSpec = {
            when {
                isStacking -> defaultStacking
                isUnstacking -> defaultUnstacking
                isUnknown -> when {
                    initialState.route == searchRoute && targetState.route == searchResultRoute -> defaultStacking
                    initialState.route == searchResultRoute && targetState.route == searchRoute -> defaultUnstacking
                    else -> defaultStill
                }

                else -> defaultStill
            }
        }
    ) {
        globalRoutes()

        settingsRoute {
            SettingsScreen()
        }

        localPlaylistRoute { playlistId ->
            LocalPlaylistScreen(
                playlistId = playlistId ?: error("playlistId cannot be null")
            )
        }

        builtInPlaylistRoute { builtInPlaylist ->
            BuiltInPlaylistScreen(
                builtInPlaylist = builtInPlaylist
            )
        }

        searchResultRoute { query ->
            SearchResultScreen(
                query = query,
                onSearchAgain = {
                    searchRoute(query)
                }
            )
        }

        searchRoute { initialTextInput ->
            val context = LocalContext.current

            SearchScreen(
                initialTextInput = initialTextInput,
                onSearch = { query ->
                    pop()
                    searchResultRoute(query)

                    if (!context.preferences.getBoolean(pauseSearchHistoryKey, false)) {
                        query {
                            Database.insert(SearchQuery(query = query))
                        }
                    }
                },
                onViewPlaylist = onPlaylistUrl,
                onViewArtist = { browseId -> pop(); artistRoute(browseId) },
                onViewAlbum = { browseId -> pop(); albumRoute(browseId) },
                onViewPlaylistId = { browseId -> pop(); playlistRoute(browseId) }
            )
        }

        host {
            val quickPicksHomeResetKey = remember { mutableStateOf(0) }
            val (tabIndex, saveTabChanged) = rememberPreference(
                homeScreenTabIndexKey,
                defaultValue = 0
            )

            Scaffold(
                topIconButtonId = R.drawable.equalizer,
                onTopIconButtonClick = { settingsRoute() },
                tabIndex = tabIndex,
                onTabChanged = { newIndex ->
                    if (newIndex == tabIndex) {
                        if (newIndex == 0) quickPicksHomeResetKey.value += 1
                    } else {
                        saveTabChanged(newIndex)
                    }
                },
                tabColumnContent = { Item ->
                    Item(0, "Quick Picks", R.drawable.sparkles)
                    Item(1, "Songs", R.drawable.musical_notes)
                    Item(2, "Albums", R.drawable.disc)
                    Item(3, "Artists", R.drawable.person)
                },
                bottomContent = {
                    Spacer(modifier = Modifier.height(12.dp))
                    PrimaryButton(
                        iconId = R.drawable.heart,
                        onClick = { builtInPlaylistRoute(BuiltInPlaylist.Favorites) },
                        transparentBackground = true,
                        circle = true
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    PrimaryButton(
                        iconId = R.drawable.search,
                        onClick = { searchRoute("") },
                        transparentBackground = true,
                        circle = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    MiniPlayerActionButton(circle = true)
                    Spacer(modifier = Modifier.height(16.dp))
                }
            ) { currentTabIndex ->
                saveableStateHolder.SaveableStateProvider(key = currentTabIndex) {
                    when (currentTabIndex) {
                        0 -> QuickPicks(
                            homeResetKey = quickPicksHomeResetKey.value,
                            onAlbumClick = { albumRoute(it) },
                            onArtistClick = { artistRoute(it) },
                            onPlaylistClick = { playlistRoute(it) },
                            onSearchClick = { searchRoute("") }
                        )

                        1 -> HomeSongs(
                            onSearchClick = { searchRoute("") },
                            onPlaylistClick = { playlistRoute(it) }
                        )

                        2 -> HomeAlbums(
                            onAlbumClick = { albumRoute(it) },
                            onSearchClick = { searchRoute("") }
                        )

                        3 -> HomeArtistList(
                            onArtistClick = { artistRoute(it) },
                            onSearchClick = { searchRoute("") }
                        )
                    }
                }
            }
        }
    }
}
