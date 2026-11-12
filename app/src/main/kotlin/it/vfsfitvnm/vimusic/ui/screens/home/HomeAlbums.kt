package it.vfsfitvnm.vimusic.ui.screens.home

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.bodies.ContinuationBody
import it.vfsfitvnm.innertube.models.bodies.SearchBody
import it.vfsfitvnm.innertube.requests.searchPage
import it.vfsfitvnm.innertube.utils.from
import it.vfsfitvnm.vimusic.R
import it.vfsfitvnm.vimusic.ui.components.ShimmerHost
import it.vfsfitvnm.vimusic.ui.components.themed.Header
import it.vfsfitvnm.vimusic.ui.components.themed.HeaderIconButton
import it.vfsfitvnm.vimusic.ui.items.AlbumItem
import it.vfsfitvnm.vimusic.ui.items.AlbumItemPlaceholder
import it.vfsfitvnm.vimusic.ui.screens.searchresult.ItemsPage
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.ui.styling.px
import it.vfsfitvnm.vimusic.utils.semiBold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
fun HomeAlbums(
    onAlbumClick: (String) -> Unit,
    onSearchClick: () -> Unit,
) {
    val (colorPalette, typography) = LocalAppearance.current
    val thumbnailSizeDp = 108.dp
    val thumbnailSizePx = thumbnailSizeDp.px

    ItemsPage(
        tag = "home/online-albums",
        itemsPageProvider = { continuation ->
            if (continuation == null) {
                Innertube.searchPage(
                    body = SearchBody(query = "latest music albums", params = Innertube.SearchFilter.Album.value),
                    fromMusicShelfRendererContent = Innertube.AlbumItem::from
                )
            } else {
                Innertube.searchPage(
                    body = ContinuationBody(continuation = continuation),
                    fromMusicShelfRendererContent = Innertube.AlbumItem::from
                )
            }
        },
        emptyItemsText = "No online albums found. Try Search.",
        refreshOnEnter = true,
        headerContent = {
            Column {
                Header(title = "Albums") {
                    HeaderIconButton(icon = R.drawable.search, color = colorPalette.text, onClick = onSearchClick)
                }
                OnlineAlbumShelf(
                    title = "Album",
                    query = "popular music albums",
                    thumbnailSizeDp = thumbnailSizeDp,
                    thumbnailSizePx = thumbnailSizePx,
                    onAlbumClick = onAlbumClick
                )
                OnlineAlbumShelf(
                    title = "New Album",
                    query = "new album releases",
                    thumbnailSizeDp = thumbnailSizeDp,
                    thumbnailSizePx = thumbnailSizePx,
                    onAlbumClick = onAlbumClick
                )
                OnlineAlbumShelf(
                    title = "Top Album",
                    query = "top chart popular albums",
                    thumbnailSizeDp = thumbnailSizeDp,
                    thumbnailSizePx = thumbnailSizePx,
                    onAlbumClick = onAlbumClick
                )
                OnlineAlbumShelf(
                    title = "Trending Album",
                    query = "trending albums this week",
                    thumbnailSizeDp = thumbnailSizeDp,
                    thumbnailSizePx = thumbnailSizePx,
                    onAlbumClick = onAlbumClick
                )
                BasicText(
                    text = "Explore all albums",
                    style = typography.m.semiBold,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                )
            }
        },
        itemContent = { album ->
            AlbumItem(
                album = album,
                thumbnailSizePx = thumbnailSizePx,
                thumbnailSizeDp = thumbnailSizeDp,
                modifier = Modifier.clickable {
                    album.info?.endpoint?.browseId?.let(onAlbumClick)
                }
            )
        },
        itemPlaceholderContent = {
            AlbumItemPlaceholder(thumbnailSizeDp = thumbnailSizeDp)
        }
    )
}

@Composable
private fun OnlineAlbumShelf(
    title: String,
    query: String,
    thumbnailSizeDp: androidx.compose.ui.unit.Dp,
    thumbnailSizePx: Int,
    onAlbumClick: (String) -> Unit
) {
    val (_, typography) = LocalAppearance.current
    var albums by remember(query) { mutableStateOf<List<Innertube.AlbumItem>?>(null) }

    LaunchedEffect(query) {
        albums = withContext(Dispatchers.IO) {
            Innertube.searchPage(
                body = SearchBody(query = query, params = Innertube.SearchFilter.Album.value),
                fromMusicShelfRendererContent = Innertube.AlbumItem::from
            )?.getOrNull()?.items.orEmpty()
        }
    }

    BasicText(
        text = title,
        style = typography.m.semiBold,
        modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 8.dp)
    )

    if (albums == null) {
        ShimmerHost {
            repeat(3) {
                AlbumItemPlaceholder(thumbnailSizeDp = thumbnailSizeDp, alternative = true)
            }
        }
    } else {
        LazyRow {
            items(albums.orEmpty(), key = Innertube.AlbumItem::key) { album ->
                AlbumItem(
                    album = album,
                    thumbnailSizePx = thumbnailSizePx,
                    thumbnailSizeDp = thumbnailSizeDp,
                    alternative = true,
                    modifier = Modifier.clickable {
                        album.info?.endpoint?.browseId?.let(onAlbumClick)
                    }
                )
            }
        }
    }
}
