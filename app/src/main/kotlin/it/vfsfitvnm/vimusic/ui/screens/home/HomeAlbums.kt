package it.vfsfitvnm.vimusic.ui.screens.home

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import it.vfsfitvnm.compose.persist.persist
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.Context
import it.vfsfitvnm.innertube.models.bodies.ContinuationBody
import it.vfsfitvnm.innertube.models.bodies.SearchBody
import it.vfsfitvnm.innertube.requests.searchPage
import it.vfsfitvnm.innertube.utils.from
import it.vfsfitvnm.vimusic.R
import it.vfsfitvnm.vimusic.ui.components.themed.Header
import it.vfsfitvnm.vimusic.ui.components.themed.HeaderIconButton
import it.vfsfitvnm.vimusic.ui.components.themed.PrimaryButton
import it.vfsfitvnm.vimusic.ui.items.AlbumItem
import it.vfsfitvnm.vimusic.ui.items.AlbumItemPlaceholder
import it.vfsfitvnm.vimusic.ui.screens.searchresult.ItemsPage
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.ui.styling.px
import it.vfsfitvnm.vimusic.utils.semiBold
import java.time.LocalDate

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
    val releasePeriod = LocalDate.now().month.name.lowercase().replaceFirstChar { it.uppercase() } +
        " " + LocalDate.now().year
    // Keep the category intent separate from language queries so language priority
    // is enforced in the returned list, not left to YouTube's mixed search ranking.
    val categories = listOf(
        "Album" to "popular music albums",
        "New Releases Album" to "newly released music albums $releasePeriod",
        "Trending Album" to "trending music albums this week",
        "Top Album" to "top chart most popular music albums"
    )
    var selectedCategory by persist("home/albumsCategory", "Album")
    val selectedQuery = categories.firstOrNull { it.first == selectedCategory }?.second
        ?: "popular music albums"

    Box(modifier = Modifier.fillMaxSize()) {
    ItemsPage(
        tag = "home/online-albums-$selectedCategory",
        itemsPageProvider = { continuation ->
            // Keep one independent continuation per language group. This prevents
            // later pages from dropping Hindi/Marathi priority or restarting old pages.
            val indiaContext = Context.DefaultWeb.copy(
                client = Context.DefaultWeb.client.copy(hl = "hi", gl = "IN")
            )
            val languageQueries = listOf(
                "Hindi Bollywood music albums",
                "Marathi music albums",
                "Punjabi Tamil Telugu Bengali Kannada Malayalam Indian regional music albums",
                "English international music albums"
            )
            val previousTokens = continuation?.split('\u001F')
            val nextTokens = mutableListOf<String?>()
            val combinedAlbums = mutableListOf<Innertube.AlbumItem>()

            languageQueries.forEachIndexed { index, languageQuery ->
                val token = previousTokens?.getOrNull(index)?.takeIf { it.isNotBlank() }
                if (continuation != null && token == null) {
                    nextTokens += null
                    return@forEachIndexed
                }
                val result = if (token == null) {
                    Innertube.searchPage(
                        body = SearchBody(
                            context = indiaContext,
                            query = "$languageQuery $selectedQuery",
                            params = Innertube.SearchFilter.Album.value
                        ),
                        fromMusicShelfRendererContent = Innertube.AlbumItem::from
                    )
                } else {
                    Innertube.searchPage(
                        body = ContinuationBody(continuation = token),
                        fromMusicShelfRendererContent = Innertube.AlbumItem::from
                    )
                }
                val page = result?.getOrNull()
                combinedAlbums += page?.items.orEmpty()
                nextTokens += page?.continuation
            }

            val nextContinuation = nextTokens
                .takeIf { tokens -> tokens.any { it != null } }
                ?.joinToString("\u001F") { it.orEmpty() }

            Result.success(
                Innertube.ItemsPage(
                    items = combinedAlbums.distinctBy { it.key },
                    continuation = nextContinuation
                )
            )
        },
        emptyItemsText = "No online albums found. Try Search.",
        refreshOnEnter = true,
        headerContent = {
            Column {
                Header(title = "Albums") {
                }
                BasicText(
                    text = "EXPLORE ALBUM CATEGORIES",
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
                BasicText(
                    text = selectedCategory.uppercase(),
                    style = typography.s.semiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
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
        PrimaryButton(
            iconId = R.drawable.search,
            onClick = onSearchClick,
            transparentBackground = true,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 88.dp)
        )
    }
}
