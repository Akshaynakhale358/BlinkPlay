package it.vfsfitvnm.innertube.requests

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.BrowseResponse
import it.vfsfitvnm.innertube.models.ContinuationResponse
import it.vfsfitvnm.innertube.models.MusicCarouselShelfRenderer
import it.vfsfitvnm.innertube.models.MusicResponsiveHeaderRenderer
import it.vfsfitvnm.innertube.models.MusicResponsiveListItemRenderer
import it.vfsfitvnm.innertube.models.MusicShelfRenderer
import it.vfsfitvnm.innertube.models.NavigationEndpoint
import it.vfsfitvnm.innertube.models.SectionListRenderer
import it.vfsfitvnm.innertube.models.bodies.BrowseBody
import it.vfsfitvnm.innertube.models.bodies.ContinuationBody
import it.vfsfitvnm.innertube.utils.from
import it.vfsfitvnm.innertube.utils.runCatchingNonCancellable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive

private val playlistResponseJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

private fun JsonElement.descendants(): Sequence<JsonElement> = sequence {
    yield(this@descendants)
    when (val node = this@descendants) {
        is JsonObject -> node.values.forEach { yieldAll(it.descendants()) }
        is JsonArray -> node.forEach { yieldAll(it.descendants()) }
        else -> Unit
    }
}

private fun JsonElement.songRenderers(): List<Innertube.SongItem> =
    descendants()
        .mapNotNull { node ->
            val renderer = (node as? JsonObject)?.get("musicResponsiveListItemRenderer")
                ?: return@mapNotNull null
            runCatching {
                playlistResponseJson.decodeFromJsonElement<MusicResponsiveListItemRenderer>(renderer)
            }.getOrNull()?.let(Innertube.SongItem::from)
        }
        .distinctBy(Innertube.SongItem::key)

private fun JsonElement.playlistShelfSongs(): List<Innertube.SongItem> {
    val shelves = descendants().mapNotNull { node ->
        val obj = node as? JsonObject ?: return@mapNotNull null
        obj["musicShelfRenderer"] ?: obj["musicPlaylistShelfRenderer"]
    }.toList()

    return shelves
        .map { it.songRenderers() }
        .maxByOrNull { it.size }
        .orEmpty()
}

private fun JsonElement.continuationToken(): String? =
    descendants().mapNotNull { node ->
        val obj = node as? JsonObject ?: return@mapNotNull null
        val continuationItem = obj["continuationItemRenderer"] as? JsonObject
        val endpoint = continuationItem?.get("continuationEndpoint") as? JsonObject
        val direct = (endpoint?.get("continuationCommand") as? JsonObject)
            ?.get("token")?.jsonPrimitive?.contentOrNull
        val nested = ((endpoint?.get("commandExecutorCommand") as? JsonObject)
            ?.get("commands") as? JsonArray)
            ?.firstNotNullOfOrNull { command ->
                (((command as? JsonObject)?.get("continuationCommand") as? JsonObject)
                    ?.get("token") as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
            }
        val legacy = (((obj["nextContinuationData"] as? JsonObject)
            ?.get("continuation")) as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
        direct ?: nested ?: legacy
    }.lastOrNull()

suspend fun Innertube.playlistPage(body: BrowseBody) = runCatchingNonCancellable {
    val raw = client.post(browse) {
        setBody(body)
        mask("*")
    }.body<String>()
    val root = playlistResponseJson.parseToJsonElement(raw)
    val response = runCatching {
        playlistResponseJson.decodeFromJsonElement<BrowseResponse>(root)
    }.getOrNull()

    val twoColumn = response?.contents?.twoColumnBrowseResultsRenderer
    val header: MusicResponsiveHeaderRenderer? = twoColumn
        ?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents
        ?.firstNotNullOfOrNull(SectionListRenderer.Content::musicResponsiveHeaderRenderer)

    val sectionListRendererContents = twoColumn?.secondaryContents?.sectionListRenderer?.contents
        ?: response?.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents
        ?: response?.contents?.sectionListRenderer?.contents

    val musicShelfRenderer = sectionListRendererContents
        ?.firstNotNullOfOrNull(SectionListRenderer.Content::musicShelfRenderer)
    val musicCarouselShelfRenderer = sectionListRendererContents
        ?.firstNotNullOfOrNull(SectionListRenderer.Content::musicCarouselShelfRenderer)
    val legacyHeader = response?.header?.musicDetailHeaderRenderer

    val typedSongsPage = musicShelfRenderer?.toSongsPage(
        continuation = sectionListRendererContents.orEmpty()
            .mapNotNull { it.continuationItemRenderer?.continuationEndpoint?.token() }
            .firstOrNull()
            ?: musicShelfRenderer.contents
                ?.mapNotNull { it.continuationItemRenderer?.continuationEndpoint?.token() }
                ?.firstOrNull()
    )
    val rawSongs = root.playlistShelfSongs()
    val songsPage = if (rawSongs.isNotEmpty()) {
        Innertube.ItemsPage(
            items = rawSongs,
            continuation = root.continuationToken() ?: typedSongsPage?.continuation
        )
    } else typedSongsPage

    Innertube.PlaylistOrAlbumPage(
        title = header?.title?.text ?: legacyHeader?.title?.text,
        thumbnail = (header?.thumbnail ?: legacyHeader?.thumbnail)
            ?.musicThumbnailRenderer?.thumbnail?.thumbnails?.firstOrNull(),
        authors = header?.straplineTextOne?.runs
            ?.filter { it.navigationEndpoint != null }
            ?.map { Innertube.Info<NavigationEndpoint.Endpoint.Browse>(it) }
            ?.takeIf { it.isNotEmpty() }
            ?: legacyHeader?.subtitle?.splitBySeparator()?.getOrNull(1)
                ?.map { Innertube.Info<NavigationEndpoint.Endpoint.Browse>(it) },
        year = header?.subtitle?.splitBySeparator()?.lastOrNull()?.firstOrNull()?.text
            ?: legacyHeader?.subtitle?.splitBySeparator()?.getOrNull(2)?.firstOrNull()?.text,
        url = response?.microformat?.microformatDataRenderer?.urlCanonical,
        songsPage = songsPage,
        otherVersions = musicCarouselShelfRenderer?.contents
            ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
            ?.mapNotNull(Innertube.AlbumItem::from)
    )
}

suspend fun Innertube.playlistPage(body: ContinuationBody) = runCatchingNonCancellable {
    val raw = client.post(browse) {
        setBody(body)
        mask("*")
    }.body<String>()
    val root = playlistResponseJson.parseToJsonElement(raw)
    val response = runCatching {
        playlistResponseJson.decodeFromJsonElement<ContinuationResponse>(root)
    }.getOrNull()

    val shelf = response?.continuationContents?.musicShelfContinuation
    val typedSongs = shelf?.contents
        ?.mapNotNull(MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
        ?.mapNotNull(Innertube.SongItem::from)
        .orEmpty() + (response?.onResponseReceivedActions.orEmpty() +
        response?.onResponseReceivedEndpoints.orEmpty()).flatMap { action ->
        (action.appendContinuationItemsAction ?: action.reloadContinuationItemsCommand)
            ?.continuationItems.orEmpty()
    }.mapNotNull { it.musicResponsiveListItemRenderer }.mapNotNull(Innertube.SongItem::from)

    val rawSongs = root.songRenderers()
    val songs = (if (rawSongs.isNotEmpty()) rawSongs else typedSongs)
        .distinctBy(Innertube.SongItem::key)

    Innertube.ItemsPage(
        items = songs,
        continuation = root.continuationToken()
            ?: shelf?.continuations?.firstOrNull()?.nextContinuationData?.continuation
    )
}

private fun MusicShelfRenderer?.toSongsPage(continuation: String? = null) =
    Innertube.ItemsPage(
        items = this?.contents
            ?.mapNotNull(MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
            ?.mapNotNull(Innertube.SongItem::from),
        continuation = continuation ?: this?.continuations
            ?.firstOrNull()?.nextContinuationData?.continuation
    )
