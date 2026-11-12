package it.vfsfitvnm.vimusic.ui.screens.home

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.bodies.ContinuationBody
import it.vfsfitvnm.innertube.models.bodies.SearchBody
import it.vfsfitvnm.innertube.requests.searchPage
import it.vfsfitvnm.innertube.utils.from
import it.vfsfitvnm.vimusic.R
import it.vfsfitvnm.vimusic.ui.components.themed.Header
import it.vfsfitvnm.vimusic.ui.components.themed.HeaderIconButton
import it.vfsfitvnm.vimusic.ui.items.ArtistItem
import it.vfsfitvnm.vimusic.ui.items.ArtistItemPlaceholder
import it.vfsfitvnm.vimusic.ui.screens.searchresult.ItemsPage
import it.vfsfitvnm.vimusic.ui.styling.Dimensions
import it.vfsfitvnm.vimusic.ui.styling.px

@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
fun HomeArtistList(
    onArtistClick: (String) -> Unit,
    onSearchClick: () -> Unit,
) {
    val thumbnailSizeDp = Dimensions.thumbnails.song * 2
    val thumbnailSizePx = thumbnailSizeDp.px

    ItemsPage(
        tag = "home/online-artists",
        itemsPageProvider = { continuation ->
            if (continuation == null) {
                Innertube.searchPage(
                    body = SearchBody(query = "popular Indian music artists", params = Innertube.SearchFilter.Artist.value),
                    fromMusicShelfRendererContent = Innertube.ArtistItem::from
                )
            } else {
                Innertube.searchPage(
                    body = ContinuationBody(continuation = continuation),
                    fromMusicShelfRendererContent = Innertube.ArtistItem::from
                )
            }
        },
        emptyItemsText = "No online artists found. Try Search.",
        headerContent = {
            Header(title = "Artists") {
                HeaderIconButton(icon = R.drawable.search, onClick = onSearchClick)
            }
        },
        itemContent = { artist ->
            ArtistItem(
                artist = artist,
                thumbnailSizePx = thumbnailSizePx,
                thumbnailSizeDp = thumbnailSizeDp,
                alternative = true,
                modifier = Modifier.clickable {
                    artist.info?.endpoint?.browseId?.let(onArtistClick)
                }
            )
        },
        itemPlaceholderContent = {
            ArtistItemPlaceholder(thumbnailSizeDp = thumbnailSizeDp, alternative = true)
        }
    )
}
