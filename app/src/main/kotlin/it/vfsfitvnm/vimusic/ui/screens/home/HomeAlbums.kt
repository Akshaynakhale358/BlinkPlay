package it.vfsfitvnm.vimusic.ui.screens.home

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.bodies.ContinuationBody
import it.vfsfitvnm.innertube.models.bodies.SearchBody
import it.vfsfitvnm.innertube.requests.searchPage
import it.vfsfitvnm.innertube.utils.from
import it.vfsfitvnm.vimusic.R
import it.vfsfitvnm.vimusic.ui.components.themed.Header
import it.vfsfitvnm.vimusic.ui.components.themed.HeaderIconButton
import it.vfsfitvnm.vimusic.ui.items.AlbumItem
import it.vfsfitvnm.vimusic.ui.items.AlbumItemPlaceholder
import it.vfsfitvnm.vimusic.ui.screens.searchresult.ItemsPage
import it.vfsfitvnm.vimusic.ui.styling.px

@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
fun HomeAlbums(
    onAlbumClick: (String) -> Unit,
    onSearchClick: () -> Unit,
) {
    val (colorPalette) = it.vfsfitvnm.vimusic.ui.styling.LocalAppearance.current
    val thumbnailSizeDp = 108.dp
    val thumbnailSizePx = thumbnailSizeDp.px

    ItemsPage(
        tag = "home/online-albums",
        itemsPageProvider = { continuation ->
            if (continuation == null) {
                Innertube.searchPage(
                    body = SearchBody(query = "latest music albums 2026", params = Innertube.SearchFilter.Album.value),
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
        headerContent = {
            Header(title = "Albums") {
                HeaderIconButton(icon = R.drawable.search, color = colorPalette.text, onClick = onSearchClick)
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
