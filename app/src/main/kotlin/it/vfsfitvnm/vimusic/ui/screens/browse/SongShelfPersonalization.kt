package it.vfsfitvnm.vimusic.ui.screens.browse

import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.requests.MusicBrowsePage
import it.vfsfitvnm.innertube.requests.MusicBrowseSection
import it.vfsfitvnm.vimusic.Database
import kotlinx.coroutines.flow.first

/**
 * Reorders songs already supplied by a YouTube Music category feed using this listener's
 * actual play-time history and favourites. It never adds tracks from another category.
 */
private suspend fun artistPreferenceScores(): Map<String, Int> {
    val history = (Database.trending(30).first() + Database.songsByPlayTimeDesc().first())
        .distinctBy { it.id }
    val favorites = Database.favorites().first()
    val scores = mutableMapOf<String, Int>()

    fun addArtists(artistsText: String?, weight: Int) {
        artistsText.orEmpty()
            .split(',', '/', '&', '•')
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .forEach { artist -> scores[artist] = (scores[artist] ?: 0) + weight }
    }

    history.take(30).forEachIndexed { index, song ->
        val playWeight = (song.totalPlayTimeMs / 60_000L).toInt().coerceIn(0, 20)
        addArtists(song.artistsText, (30 - index) + playWeight)
    }
    favorites.take(30).forEachIndexed { index, song ->
        addArtists(song.artistsText, 45 - index)
    }
    return scores
}

private fun personalizeSongs(
    songs: List<Innertube.SongItem>,
    artistScores: Map<String, Int>,
): List<Innertube.SongItem> = songs.withIndex()
    .sortedWith(
        compareByDescending<IndexedValue<Innertube.SongItem>> { indexed ->
            indexed.value.authors.orEmpty().mapNotNull { author ->
                author.name?.trim()?.lowercase()?.let(artistScores::get)
            }.maxOrNull() ?: 0
        }.thenBy { it.index }
    )
    .map { it.value }

/** Applies listening-history personalization to every song-only shelf in a browse page. */
suspend fun personalizeSongSections(
    sections: List<MusicBrowseSection>,
): List<MusicBrowseSection> {
    val scores = artistPreferenceScores()
    return sections.map { section ->
        val songs = section.items.filterIsInstance<Innertube.SongItem>()
        if (songs.isNotEmpty() && songs.size == section.items.size) {
            section.copy(items = personalizeSongs(songs, scores))
        } else section
    }
}

/** Applies the same preference ranking to both YouTube Music Home and Explore categories. */
suspend fun personalizeMusicDiscovery(
    discovery: it.vfsfitvnm.innertube.requests.MusicDiscovery,
): it.vfsfitvnm.innertube.requests.MusicDiscovery {
    val scores = artistPreferenceScores()

    fun personalizePage(page: MusicBrowsePage?): MusicBrowsePage? =
        page?.copy(
            sections = page.sections.map { section ->
                val songs = section.items.filterIsInstance<Innertube.SongItem>()
                if (songs.isNotEmpty() && songs.size == section.items.size) {
                    section.copy(items = personalizeSongs(songs, scores))
                } else section
            }
        )

    return discovery.copy(
        home = personalizePage(discovery.home),
        explore = personalizePage(discovery.explore),
    )
}
