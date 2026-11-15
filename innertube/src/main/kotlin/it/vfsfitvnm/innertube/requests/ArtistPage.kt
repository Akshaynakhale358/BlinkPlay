package it.vfsfitvnm.innertube.requests

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.BrowseResponse
import it.vfsfitvnm.innertube.models.MusicCarouselShelfRenderer
import it.vfsfitvnm.innertube.models.MusicResponsiveListItemRenderer
import it.vfsfitvnm.innertube.models.MusicShelfRenderer
import it.vfsfitvnm.innertube.models.SectionListRenderer
import it.vfsfitvnm.innertube.models.bodies.BrowseBody
import it.vfsfitvnm.innertube.utils.from
import it.vfsfitvnm.innertube.utils.runCatchingNonCancellable

suspend fun Innertube.artistPage(body: BrowseBody): Result<Innertube.ArtistPage>? =
    runCatchingNonCancellable {
        val response = client.post(browse) {
            setBody(body)
            // Keep the complete artist contents and header so all required shelves and continuation data are returned.
            mask("contents,header")
        }.body<BrowseResponse>()

        // Artist responses can split the page between the main tab and secondary contents.
        // Merge every supported section list instead of selecting only the first one.
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
        val sectionList = sectionLists.firstOrNull()

        // YouTube changes shelf names and header renderer variants. Keep the lookup tolerant,
        // following the artist parser strategy used by Metrolist, while retaining BlinkPlay models.
        fun section(vararg titles: String): SectionListRenderer.Content? =
            sectionContents.firstOrNull { content ->
                val title = content.musicCarouselShelfRenderer
                    ?.header?.musicCarouselShelfBasicHeaderRenderer?.title?.text
                    ?: content.musicPlaylistShelfRenderer?.title?.text
                    ?: content.musicShelfRenderer?.title?.text
                    ?: content.musicResponsiveHeaderRenderer?.title?.text
                title?.let { value -> titles.any { value.contains(it, ignoreCase = true) } } == true
            }

        val songsSectionContent = section("Songs", "Top songs", "Top tracks", "Popular songs", "Tracks")
        val songsSection = songsSectionContent?.musicPlaylistShelfRenderer
            ?: songsSectionContent?.musicShelfRenderer
        val albumsSection = section("Albums", "Releases", "Full albums")?.musicCarouselShelfRenderer
        val singlesSection = section("Singles & EPs", "Singles and EPs", "Singles", "EPs")?.musicCarouselShelfRenderer
        val similarArtistsSection = section("Fans might also like", "Similar artists", "Related artists", "You might also like")?.musicCarouselShelfRenderer
        val playlistsSection = section("Playlists")?.musicCarouselShelfRenderer
        val featuredOnSection = section("Featured on", "Appears on", "Appears on / Featured on")?.musicCarouselShelfRenderer

        fun audioSong(renderer: MusicResponsiveListItemRenderer): Innertube.SongItem? {
            val song = Innertube.SongItem.from(renderer) ?: return null
            val videoType = song.info
                ?.endpoint
                ?.watchEndpointMusicSupportedConfigs
                ?.watchEndpointMusicConfig
                ?.musicVideoType
            // Apply the same video/podcast distinction as Metrolist ArtistSongs.
            return song.takeIf { isMetrolistArtistAudioType(videoType) }
        }

        fun shelfTitle(content: SectionListRenderer.Content): String? =
            content.musicCarouselShelfRenderer
                ?.header
                ?.musicCarouselShelfBasicHeaderRenderer
                ?.title
                ?.text
                ?: content.musicPlaylistShelfRenderer?.title?.text
                ?: content.musicShelfRenderer?.title?.text
                ?: content.musicResponsiveHeaderRenderer?.title?.text

        fun shelfItems(content: SectionListRenderer.Content): List<Innertube.Item> =
            buildList {
                (content.musicPlaylistShelfRenderer ?: content.musicShelfRenderer)?.contents.orEmpty().forEach { shelfItem ->
                    shelfItem.musicResponsiveListItemRenderer
                        ?.let(::audioSong)
                        ?.let(::add)
                }
                content.musicCarouselShelfRenderer?.contents.orEmpty().forEach { shelfItem ->
                    shelfItem.musicResponsiveListItemRenderer
                        ?.let(::audioSong)
                        ?.let(::add)
                    shelfItem.musicTwoRowItemRenderer?.let { renderer ->
                        val item = Innertube.AlbumItem.from(renderer)
                            ?: Innertube.ArtistItem.from(renderer)
                            ?: Innertube.PlaylistItem.from(renderer)
                        item?.let(::add)
                    }
                }
            }.distinctBy(Innertube.Item::key)

        // Preserve shelves the current screen does not render yet. This lets the next UI pass
        // decide how to display the data without throwing away sections the API already returned.
        val allSections = sectionContents.mapNotNull { content ->
            val title = shelfTitle(content)?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
            val items = shelfItems(content)

            val browseEndpoint = (content.musicPlaylistShelfRenderer ?: content.musicShelfRenderer)
                ?.bottomEndpoint
                ?.browseEndpoint
                ?: content.musicCarouselShelfRenderer
                    ?.header
                    ?.musicCarouselShelfBasicHeaderRenderer
                    ?.moreContentButton
                    ?.buttonRenderer
                    ?.navigationEndpoint
                    ?.browseEndpoint

            val shelfContinuation = (content.musicPlaylistShelfRenderer ?: content.musicShelfRenderer)
                ?.continuations
                ?.firstOrNull()
                ?.nextContinuationData
                ?.continuation
                ?: content.musicCarouselShelfRenderer
                    ?.continuations
                    ?.firstOrNull()
                    ?.nextContinuationData
                    ?.continuation

            Innertube.ArtistSection(
                title = title,
                items = items,
                browseEndpoint = browseEndpoint,
                continuation = shelfContinuation,
            ).takeIf { items.isNotEmpty() || shelfContinuation != null || browseEndpoint != null }
        }

        val immersiveHeader = response.header?.musicImmersiveHeaderRenderer
        val detailHeader = response.header?.musicDetailHeaderRenderer
        val description = immersiveHeader?.description?.text
            ?: sectionContents
                .firstNotNullOfOrNull { it.musicDescriptionShelfRenderer?.description?.text }

        Innertube.ArtistPage(
            name = immersiveHeader?.title?.text ?: detailHeader?.title?.text,
            description = description,
            subscriberCountText = immersiveHeader
                ?.subscriptionButton
                ?.subscribeButtonRenderer
                ?.subscriberCountText
                ?.text
                ?: immersiveHeader
                    ?.subscriptionButton
                    ?.subscribeButtonRenderer
                    ?.longSubscriberCountText
                    ?.text,
            thumbnail = (
                immersiveHeader?.foregroundThumbnail
                    ?: immersiveHeader?.thumbnail
                    ?: detailHeader?.thumbnail
                )
                ?.musicThumbnailRenderer
                ?.thumbnail
                ?.thumbnails
                ?.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) },
            shuffleEndpoint = immersiveHeader
                ?.playButton
                ?.buttonRenderer
                ?.navigationEndpoint
                ?.watchEndpoint,
            radioEndpoint = immersiveHeader
                ?.startRadioButton
                ?.buttonRenderer
                ?.navigationEndpoint
                ?.watchEndpoint,
            songs = songsSection
                ?.contents
                ?.mapNotNull(MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
                ?.mapNotNull(::audioSong)
                ?.distinctBy(Innertube.Item::key),
            songsEndpoint = songsSection
                ?.bottomEndpoint
                ?.browseEndpoint,
            albums = albumsSection
                ?.contents
                ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
                ?.mapNotNull(Innertube.AlbumItem::from)
                ?.distinctBy(Innertube.Item::key),
            albumsEndpoint = albumsSection
                ?.header
                ?.musicCarouselShelfBasicHeaderRenderer
                ?.moreContentButton
                ?.buttonRenderer
                ?.navigationEndpoint
                ?.browseEndpoint,
            singles = singlesSection
                ?.contents
                ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
                ?.mapNotNull(Innertube.AlbumItem::from)
                ?.distinctBy(Innertube.Item::key),
            singlesEndpoint = singlesSection
                ?.header
                ?.musicCarouselShelfBasicHeaderRenderer
                ?.moreContentButton
                ?.buttonRenderer
                ?.navigationEndpoint
                ?.browseEndpoint,
            similarArtists = similarArtistsSection
                ?.contents
                ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
                ?.mapNotNull(Innertube.ArtistItem::from)
                ?.distinctBy(Innertube.Item::key),
            playlists = playlistsSection
                ?.contents
                ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
                ?.mapNotNull(Innertube.PlaylistItem::from)
                ?.distinctBy(Innertube.Item::key),
            featuredOn = featuredOnSection
                ?.contents
                ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
                ?.mapNotNull(Innertube.PlaylistItem::from)
                ?.distinctBy(Innertube.Item::key),
            sections = allSections,
            monthlyListenersText = immersiveHeader?.monthlyListenerCount?.text,
            isSubscribed = immersiveHeader
                ?.subscriptionButton
                ?.subscribeButtonRenderer
                ?.subscribed,
        )
    }
