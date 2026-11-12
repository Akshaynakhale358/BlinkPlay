package it.vfsfitvnm.vimusic.ui.screens.home

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import it.vfsfitvnm.compose.persist.persist
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import it.vfsfitvnm.innertube.Innertube
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
import it.vfsfitvnm.vimusic.ui.items.SongItem
import it.vfsfitvnm.vimusic.ui.items.SongItemPlaceholder
import it.vfsfitvnm.vimusic.ui.screens.searchresult.ItemsPage
import it.vfsfitvnm.vimusic.ui.styling.Dimensions
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.ui.styling.px
import it.vfsfitvnm.vimusic.utils.asMediaItem
import it.vfsfitvnm.vimusic.utils.forcePlay
import it.vfsfitvnm.vimusic.utils.semiBold

@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
fun HomeSongs(onSearchClick: () -> Unit) {
    val (colorPalette, typography) = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current
    val thumbnailSizeDp = Dimensions.thumbnails.song
    val thumbnailSizePx = thumbnailSizeDp.px
    val categories = listOf(
        "Podcast" to "popular podcasts episodes",
        "Workout" to "workout motivation songs",
        "Feel Good" to "feel good happy songs",
        "Romance" to "romantic love songs",
        "Energetic" to "high energy energetic songs",
        "Relax" to "relaxing calm music",
        "Commute" to "road trip commute songs",
        "Sad" to "sad emotional songs",
        "Focus" to "focus concentration instrumental music",
        "Sleep" to "sleep peaceful ambient music",
        "Party" to "party dance hits"
    )
    var selectedCategory by persist("home/songsCategory", "Workout")
    val selectedQuery = categories.firstOrNull { it.first == selectedCategory }?.second
        ?: "workout motivation songs"

    ItemsPage(
        tag = "home/category-songs-$selectedCategory",
        itemsPageProvider = { continuation ->
            if (continuation == null) {
                Innertube.searchPage(
                    body = SearchBody(query = selectedQuery, params = Innertube.SearchFilter.Song.value),
                    fromMusicShelfRendererContent = Innertube.SongItem::from
                )
            } else {
                Innertube.searchPage(
                    body = ContinuationBody(continuation = continuation),
                    fromMusicShelfRendererContent = Innertube.SongItem::from
                )
            }
        },
        emptyItemsText = "No online songs found. Try Search.",
        headerContent = {
            androidx.compose.foundation.layout.Column {
                Header(title = "Songs") {
                    HeaderIconButton(icon = R.drawable.search, color = colorPalette.text, onClick = onSearchClick)
                }
                BasicText(
                    text = "Explore categories",
                    style = typography.m.semiBold,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp)
                )
                LazyRow {
                    items(categories, key = { it.first }) { category ->
                        BasicText(
                            text = category.first,
                            style = if (selectedCategory == category.first) typography.m.semiBold else typography.m,
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
                BasicText(
                    text = selectedCategory,
                    style = typography.m.semiBold,
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
