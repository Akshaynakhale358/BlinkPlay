package it.vfsfitvnm.vimusic.ui.screens.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.requests.MusicBrowseSection
import it.vfsfitvnm.vimusic.ui.items.AlbumItem
import it.vfsfitvnm.vimusic.ui.items.ArtistItem
import it.vfsfitvnm.vimusic.ui.items.PlaylistItem
import it.vfsfitvnm.vimusic.ui.items.SongItem
import it.vfsfitvnm.vimusic.ui.styling.Dimensions
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.ui.styling.px
import it.vfsfitvnm.vimusic.utils.semiBold

/** Shared renderer for YouTube Music's live shelves; both Quick Picks and category pages use it. */
@Composable
fun MusicBrowseShelves(
    sections: List<MusicBrowseSection>,
    sectionTextModifier: Modifier,
    onSongClick: (Innertube.SongItem) -> Unit,
    onSongLongClick: (Innertube.SongItem) -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onCategoryClick: (String, String?) -> Unit,
) {
    val (_, typography) = LocalAppearance.current
    val songSize = Dimensions.thumbnails.song
    val albumSize = 108.dp
    val artistSize = 92.dp
    val playlistSize = 108.dp

    sections.forEach { section ->
        if (section.items.isNotEmpty()) {
            BasicText(
                text = section.title,
                style = typography.s.semiBold,
                modifier = sectionTextModifier.clickable {
                    section.browseId?.let { onCategoryClick(it, section.params) }
                }
            )
            LazyRow {
                items(section.items, key = { it.key }) { item ->
                    when (item) {
                        is Innertube.SongItem -> SongItem(
                            song = item,
                            thumbnailSizePx = songSize.px,
                            thumbnailSizeDp = songSize,
                            modifier = Modifier.combinedClickable(
                                onClick = { onSongClick(item) },
                                onLongClick = { onSongLongClick(item) }
                            )
                        )
                        is Innertube.PlaylistItem -> PlaylistItem(
                            playlist = item,
                            thumbnailSizePx = playlistSize.px,
                            thumbnailSizeDp = playlistSize,
                            alternative = true,
                            modifier = Modifier.clickable { onPlaylistClick(item.key) }
                        )
                        is Innertube.AlbumItem -> AlbumItem(
                            album = item,
                            thumbnailSizePx = albumSize.px,
                            thumbnailSizeDp = albumSize,
                            alternative = true,
                            modifier = Modifier.clickable {
                                item.info?.endpoint?.browseId?.let(onAlbumClick)
                            }
                        )
                        is Innertube.ArtistItem -> ArtistItem(
                            artist = item,
                            thumbnailSizePx = artistSize.px,
                            thumbnailSizeDp = artistSize,
                            alternative = true,
                            modifier = Modifier.clickable {
                                item.info?.endpoint?.browseId?.let(onArtistClick)
                            }
                        )
                        else -> Unit
                    }
                }
            }
        }
    }
}
