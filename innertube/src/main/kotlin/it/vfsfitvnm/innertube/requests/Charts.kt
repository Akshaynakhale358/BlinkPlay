package it.vfsfitvnm.innertube.requests

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.headers
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.NavigationEndpoint
import it.vfsfitvnm.innertube.models.Thumbnail
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

private val chartJson = Json { ignoreUnknownKeys = true; isLenient = true }

private fun JsonElement.walkJson(): Sequence<JsonElement> = sequence {
    yield(this@walkJson)
    when (val value = this@walkJson) {
        is JsonObject -> value.values.forEach { yieldAll(it.walkJson()) }
        is JsonArray -> value.forEach { yieldAll(it.walkJson()) }
        else -> Unit
    }
}

private fun JsonElement.stringAt(vararg keys: String): String? {
    val objects = walkJson().mapNotNull { it as? JsonObject }
    for (key in keys) {
        objects.forEach { obj ->
            (obj[key] as? JsonPrimitive)?.contentOrNull
                ?.takeIf(String::isNotBlank)?.let { return it }
        }
    }
    return null
}

private fun JsonElement.analyticsSection(): JsonObject? =
    walkJson().mapNotNull { it as? JsonObject }
        .firstOrNull { it.containsKey("videos") && it.containsKey("trackTypes") }

private fun JsonObject.chartItems(
    groupKey: String,
    groupIndex: Int,
    viewKey: String
): List<JsonElement> {
    val groups = this[groupKey] as? JsonArray ?: return emptyList()
    val group = groups.getOrNull(groupIndex) as? JsonObject ?: return emptyList()
    return (group[viewKey] as? JsonArray)?.toList().orEmpty()
}

private fun chartThumbnail(element: JsonElement, videoId: String): Thumbnail {
    // Prefer the largest thumbnail actually supplied by the chart response.
    // The chart may include several sizes; taking the first often selects a tiny preview.
    val candidates = element.walkJson().mapNotNull { node ->
        val obj = node as? JsonObject ?: return@mapNotNull null
        val entries = obj["thumbnails"] as? JsonArray ?: return@mapNotNull null
        entries.mapNotNull { entry ->
            val image = entry as? JsonObject ?: return@mapNotNull null
            val url = (image["url"] as? JsonPrimitive)?.contentOrNull
                ?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
            val width = (image["width"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
            val height = (image["height"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
            Thumbnail(url = url, width = width.takeIf { it > 0 }, height = height.takeIf { it > 0 })
        }
    }.flatten().distinctBy(Thumbnail::url)

    return candidates.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }
        ?: Thumbnail(
            url = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
            height = 360,
            width = 480
        )
}

private fun chartSong(element: JsonElement): Innertube.SongItem? {
    val videoId = element.stringAt("encryptedVideoId", "videoId", "id")
        ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) } ?: return null
    val title = element.stringAt("name", "title")?.takeIf(String::isNotBlank) ?: return null

    val artistNames = element.walkJson().mapNotNull { node ->
        val obj = node as? JsonObject ?: return@mapNotNull null
        val artistArray = obj["artists"] as? JsonArray ?: return@mapNotNull null
        artistArray.mapNotNull { entry ->
            val artistObj = entry as? JsonObject
            (artistObj?.get("name") as? JsonPrimitive)?.contentOrNull
                ?: (entry as? JsonPrimitive)?.contentOrNull
        }
    }.firstOrNull().orEmpty().distinct()

    val thumbnail = chartThumbnail(element, videoId)
    return Innertube.SongItem(
        info = Innertube.Info(
            name = title,
            endpoint = NavigationEndpoint.Endpoint.Watch(videoId = videoId)
        ),
        authors = artistNames.map { Innertube.Info<NavigationEndpoint.Endpoint.Browse>(it, null) },
        album = null,
        durationText = null,
        thumbnail = thumbnail
    )
}

/**
 * Fetches an India chart from YouTube Charts. This endpoint is undocumented;
 * callers should retain their regular YouTube Music search/browse fallback.
 */
suspend fun Innertube.indiaChartSongs(
    chartQuery: String,
    groupKey: String,
    groupIndex: Int,
    viewKey: String
): List<Innertube.SongItem> =
    runCatching {
        val requestBody = buildJsonObject {
            put("context", buildJsonObject {
                put("client", buildJsonObject {
                    put("clientName", "WEB_MUSIC_ANALYTICS")
                    put("clientVersion", "0.2")
                    put("hl", "en")
                    put("gl", "IN")
                    put("theme", "MUSIC")
                })
            })
            put("browseId", "FEmusic_analytics_charts_home")
            put("capabilities", buildJsonObject {})
            put("request", buildJsonObject { put("internalExperimentFlags", JsonArray(emptyList())) })
            put("query", chartQuery)
        }
        val raw = Innertube.client.post(
            "https://charts.youtube.com/youtubei/v1/browse?alt=json&key=AIzaSyCzEW7JUJdSql0-2V4tHUb6laYm4iAE_dM"
        ) {
            contentType(ContentType.Application.Json)
            headers {
                append("X-YouTube-Client-Name", "67")
                append("X-YouTube-Client-Version", "2.0")
            }
            setBody(requestBody)
        }.body<String>()
        val section = chartJson.parseToJsonElement(raw).analyticsSection()
            ?: return@runCatching emptyList()
        section.chartItems(groupKey, groupIndex, viewKey)
            .mapNotNull(::chartSong)
            .distinctBy(Innertube.SongItem::key)
    }.getOrDefault(emptyList())
