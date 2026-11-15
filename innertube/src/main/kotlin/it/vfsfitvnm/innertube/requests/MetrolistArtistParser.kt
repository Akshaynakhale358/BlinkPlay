package it.vfsfitvnm.innertube.requests

import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.BrowseResponse
import it.vfsfitvnm.innertube.models.MusicCarouselShelfRenderer
import it.vfsfitvnm.innertube.models.MusicResponsiveListItemRenderer
import it.vfsfitvnm.innertube.models.MusicShelfRenderer
import it.vfsfitvnm.innertube.models.MusicTwoRowItemRenderer
import it.vfsfitvnm.innertube.models.NavigationEndpoint
import it.vfsfitvnm.innertube.models.SectionListRenderer

/**
 * Artist-specific parser adapted from MetrolistGroup/Metrolist's YouTubeArtistParser
 * (GPL-3.0). It maps Metrolist's artist/header/shelf parsing strategy onto BlinkPlay's
 * existing Innertube models so BlinkPlay's UI and navigation types remain unchanged.
 */
internal object MetrolistArtistParser {
    private val artistPageTypes = setOf(
        "MUSIC_PAGE_TYPE_ARTIST",
        "MUSIC_PAGE_TYPE_LIBRARY_ARTIST",
        "MUSIC_PAGE_TYPE_USER_CHANNEL",
        "MUSIC_PAGE_TYPE_ARTIST_CHANNEL"
    )

    private fun isArtistEndpoint(endpoint: NavigationEndpoint.Endpoint.Browse?): Boolean {
        val id = endpoint?.browseId ?: return false
        return endpoint.type in artistPageTypes || id.startsWith("UC")
    }

    fun fromShelf(content: MusicShelfRenderer.Content): Innertube.ArtistItem? {
        val renderer = content.musicResponsiveListItemRenderer ?: return null
        val endpoint = renderer.navigationEndpoint?.browseEndpoint
            ?: renderer.flexColumns.firstOrNull()
                ?.musicResponsiveListItemFlexColumnRenderer
                ?.text?.runs
                ?.firstNotNullOfOrNull { it.navigationEndpoint?.browseEndpoint }
            ?: return null
        if (!isArtistEndpoint(endpoint)) return null

        val title = renderer.flexColumns.firstOrNull()
            ?.musicResponsiveListItemFlexColumnRenderer
            ?.text?.runs?.firstOrNull()?.text
            ?.takeIf(String::isNotBlank)
            ?: return null
        val thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.thumbnail
            ?.thumbnails?.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }
        val subscribers = renderer.flexColumns.getOrNull(1)
            ?.musicResponsiveListItemFlexColumnRenderer
            ?.text?.runs?.lastOrNull()?.text

        return Innertube.ArtistItem(
            info = Innertube.Info(name = title, endpoint = endpoint),
            subscribersCountText = subscribers,
            thumbnail = thumbnail
        )
    }

    fun fromTwoRow(renderer: MusicTwoRowItemRenderer): Innertube.ArtistItem? {
        val titleRun = renderer.title?.runs?.firstOrNull()
        val endpoint = renderer.navigationEndpoint?.browseEndpoint
            ?: titleRun?.navigationEndpoint?.browseEndpoint
            ?: return null
        if (!isArtistEndpoint(endpoint)) return null

        val title = titleRun?.text?.takeIf(String::isNotBlank) ?: return null
        val thumbnail = renderer.thumbnailRenderer?.musicThumbnailRenderer
            ?.thumbnail?.thumbnails
            ?.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }
        return Innertube.ArtistItem(
            info = Innertube.Info(name = title, endpoint = endpoint),
            subscribersCountText = renderer.subtitle?.runs?.firstOrNull()?.text,
            thumbnail = thumbnail
        )
    }

    fun parseArtist(response: BrowseResponse, channelId: String): Innertube.ArtistPage {
        val sectionLists = buildList {
            response.contents?.twoColumnBrowseResultsRenderer?.secondaryContents
                ?.sectionListRenderer?.let(::add)
            response.contents?.twoColumnBrowseResultsRenderer?.tabs.orEmpty()
                .mapNotNull { it.tabRenderer?.content?.sectionListRenderer }
                .forEach(::add)
            response.contents?.singleColumnBrowseResultsRenderer?.tabs.orEmpty()
                .mapNotNull { it.tabRenderer?.content?.sectionListRenderer }
                .forEach(::add)
            response.contents?.sectionListRenderer?.let(::add)
        }

        val sectionContents = sectionLists.flatMap { it.contents.orEmpty() }
            .distinctBy { content ->
                content.musicCarouselShelfRenderer?.header?.musicCarouselShelfBasicHeaderRenderer?.title?.text
                    ?: content.musicPlaylistShelfRenderer?.title?.text
                    ?: content.musicShelfRenderer?.title?.text
                    ?: content.musicResponsiveHeaderRenderer?.title?.text
                    ?: content.hashCode().toString()
            }

        fun section(vararg aliases: String): SectionListRenderer.Content? =
            sectionContents.firstOrNull { content ->
                val title = shelfTitle(content)
                title?.let { value -> aliases.any { value.contains(it, ignoreCase = true) } } == true
            }

        fun audioSong(renderer: MusicResponsiveListItemRenderer): Innertube.SongItem? =
            Innertube.SongItem.from(renderer)?.takeIf(::isMetrolistArtistAudio)

        fun shelfItems(content: SectionListRenderer.Content): List<Innertube.Item> =
            buildList {
                (content.musicPlaylistShelfRenderer ?: content.musicShelfRenderer)
                    ?.contents.orEmpty().forEach { item ->
                        item.musicResponsiveListItemRenderer?.let(::audioSong)?.let(::add)
                    }
                content.musicCarouselShelfRenderer?.contents.orEmpty().forEach { item ->
                    item.musicResponsiveListItemRenderer?.let(::audioSong)?.let(::add)
                    item.musicTwoRowItemRenderer?.let { renderer ->
                        (Innertube.AlbumItem.from(renderer)
                            ?: fromTwoRow(renderer)
                            ?: Innertube.PlaylistItem.from(renderer))?.let(::add)
                    }
                }
                content.gridRenderer?.items.orEmpty().forEach { item ->
                    item.musicResponsiveListItemRenderer?.let(::audioSong)?.let(::add)
                    item.musicTwoRowItemRenderer?.let { renderer ->
                        (Innertube.AlbumItem.from(renderer)
                            ?: fromTwoRow(renderer)
                            ?: Innertube.PlaylistItem.from(renderer))?.let(::add)
                    }
                }
            }.distinctBy(Innertube.Item::key)

        fun sectionContinuation(content: SectionListRenderer.Content): String? =
            (content.musicPlaylistShelfRenderer ?: content.musicShelfRenderer)
                ?.continuations?.firstOrNull()?.nextContinuationData?.continuation
                ?: content.musicCarouselShelfRenderer?.continuations
                    ?.firstOrNull()?.nextContinuationData?.continuation
                ?: content.gridRenderer?.continuations
                    ?.firstOrNull()?.nextContinuationData?.continuation

        val allSections = sectionContents.mapNotNull { content ->
            val title = shelfTitle(content)?.trim()?.takeIf(String::isNotEmpty)
                ?: return@mapNotNull null
            val items = shelfItems(content)
            val browseEndpoint = (content.musicPlaylistShelfRenderer ?: content.musicShelfRenderer)
                ?.bottomEndpoint?.browseEndpoint
                ?: content.musicCarouselShelfRenderer?.header
                    ?.musicCarouselShelfBasicHeaderRenderer?.moreContentButton
                    ?.buttonRenderer?.navigationEndpoint?.browseEndpoint
            val continuation = sectionContinuation(content)
            Innertube.ArtistSection(
                title = title,
                items = items,
                browseEndpoint = browseEndpoint,
                continuation = continuation
            ).takeIf { items.isNotEmpty() || continuation != null || browseEndpoint != null }
        }

        val immersive = response.header?.musicImmersiveHeaderRenderer
        val responsive = response.header?.musicResponsiveHeaderRenderer
            ?: sectionContents.firstNotNullOfOrNull { it.musicResponsiveHeaderRenderer }
        val generic = response.header?.musicHeaderRenderer
        val detail = response.header?.musicDetailHeaderRenderer

        val parsedItems = allSections.flatMap { it.items }
        val exactArtist = parsedItems.filterIsInstance<Innertube.ArtistItem>()
            .firstOrNull { it.info?.endpoint?.browseId == channelId }
        val exactSongArtist = parsedItems.filterIsInstance<Innertube.SongItem>()
            .flatMap { it.authors.orEmpty() }
            .firstOrNull { it.endpoint?.browseId == channelId }
        val exactAlbumArtist = parsedItems.filterIsInstance<Innertube.AlbumItem>()
            .flatMap { it.authors.orEmpty() }
            .firstOrNull { it.endpoint?.browseId == channelId }

        val title = immersive?.title?.text
            ?: responsive?.title?.text
            ?: generic?.title?.text
            ?: detail?.title?.text
            ?: exactArtist?.info?.name
            ?: exactSongArtist?.name
            ?: exactAlbumArtist?.name
            ?: channelId

        val headerThumbnail = (
            immersive?.foregroundThumbnail
                ?: immersive?.thumbnail
                ?: responsive?.thumbnail
                ?: generic?.thumbnail
                ?: detail?.thumbnail
            )?.musicThumbnailRenderer?.thumbnail?.thumbnails
            ?.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }

        val description = immersive?.description?.text
            ?: responsive?.description?.text
            ?: generic?.description?.text
            ?: sectionContents.firstNotNullOfOrNull { it.musicDescriptionShelfRenderer?.description?.text }

        val songsContent = section("Songs", "Top songs", "Top tracks", "Popular songs", "Tracks")
        val songsShelf = songsContent?.musicPlaylistShelfRenderer ?: songsContent?.musicShelfRenderer
        val albumsSection = section("Albums", "Releases", "Full albums")?.musicCarouselShelfRenderer
        val singlesSection = section("Singles & EPs", "Singles and EPs", "Singles", "EPs")?.musicCarouselShelfRenderer
        val similarSection = section("Fans might also like", "Similar artists", "Related artists", "You might also like")?.musicCarouselShelfRenderer
        val playlistsSection = section("Playlists")?.musicCarouselShelfRenderer
        val featuredSection = section("Featured on", "Appears on", "Appears on / Featured on")?.musicCarouselShelfRenderer

        return Innertube.ArtistPage(
            name = title,
            description = description,
            subscriberCountText = immersive?.subscriptionButton?.subscribeButtonRenderer?.let {
                it.subscriberCountText?.text?.takeIf(String::isNotBlank)
                    ?: it.longSubscriberCountText?.text
            },
            thumbnail = headerThumbnail ?: exactArtist?.thumbnail,
            shuffleEndpoint = immersive?.playButton?.buttonRenderer?.navigationEndpoint?.watchEndpoint,
            radioEndpoint = immersive?.startRadioButton?.buttonRenderer?.navigationEndpoint?.watchEndpoint,
            songs = songsShelf?.contents
                ?.mapNotNull(MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
                ?.mapNotNull(::audioSong)
                ?.distinctBy(Innertube.Item::key),
            songsEndpoint = songsShelf?.bottomEndpoint?.browseEndpoint,
            albums = albumsSection?.contents
                ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
                ?.mapNotNull(Innertube.AlbumItem::from)
                ?.distinctBy(Innertube.Item::key),
            albumsEndpoint = albumsSection?.header?.musicCarouselShelfBasicHeaderRenderer
                ?.moreContentButton?.buttonRenderer?.navigationEndpoint?.browseEndpoint,
            singles = singlesSection?.contents
                ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
                ?.mapNotNull(Innertube.AlbumItem::from)
                ?.distinctBy(Innertube.Item::key),
            singlesEndpoint = singlesSection?.header?.musicCarouselShelfBasicHeaderRenderer
                ?.moreContentButton?.buttonRenderer?.navigationEndpoint?.browseEndpoint,
            similarArtists = similarSection?.contents
                ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
                ?.mapNotNull(::fromTwoRow)
                ?.distinctBy(Innertube.Item::key),
            playlists = playlistsSection?.contents
                ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
                ?.mapNotNull(Innertube.PlaylistItem::from)
                ?.distinctBy(Innertube.Item::key),
            featuredOn = featuredSection?.contents
                ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
                ?.mapNotNull(Innertube.PlaylistItem::from)
                ?.distinctBy(Innertube.Item::key),
            sections = allSections,
            monthlyListenersText = immersive?.monthlyListenerCount?.text,
            isSubscribed = immersive?.subscriptionButton?.subscribeButtonRenderer?.subscribed
        )
    }

    private fun shelfTitle(content: SectionListRenderer.Content): String? =
        content.musicCarouselShelfRenderer?.header?.musicCarouselShelfBasicHeaderRenderer?.title?.text
            ?: content.musicPlaylistShelfRenderer?.title?.text
            ?: content.musicShelfRenderer?.title?.text
            ?: content.musicResponsiveHeaderRenderer?.title?.text
}
