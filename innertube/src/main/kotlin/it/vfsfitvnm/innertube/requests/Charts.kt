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

private fun JsonElement.arrayFor(key: String): List<JsonElement> =
    walkJson().mapNotNull { it as? JsonObject }
        .flatMap { (it[key] as? JsonArray)?.toList().orEmpty() }

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

    val thumbnailUrl = element.stringAt("url", "thumbnailUrl")
        ?.takeIf { it.startsWith("https://") }
    return Innertube.SongItem(
        info = Innertube.Info(
            name = title,
            endpoint = NavigationEndpoint.Endpoint.Watch(videoId = videoId)
        ),
        authors = artistNames.map { Innertube.Info<NavigationEndpoint.Endpoint.Browse>(it, null) },
        album = null,
        durationText = null,
        thumbnail = thumbnailUrl?.let { Thumbnail(url = it, height = null, width = null) }
    )
}

/**
 * Fetches an India chart from YouTube Charts. This endpoint is undocumented;
 * callers should retain their regular YouTube Music search/browse fallback.
 */
suspend fun Innertube.indiaChartSongs(chartQuery: String, arrayKey: String): List<Innertube.SongItem> =
    runCatching {
        val requestBody = buildJsonObject {
            put("context", buildJsonObject {
                put("client", buildJsonObject {
                    put("clientName", "WEB_MUSIC_ANALYTICS")
                    put("clientVersion", "2.0")
                    put("hl", "en")
                    put("gl", "IN")
                    put("theme", "MUSIC")
                })
            })
            put("browseId", "FEmusic_analytics_charts_home")
            put("query", chartQuery)
        }
        val raw = Innertube.client.post("https://charts.youtube.com/youtubei/v1/browse?alt=json") {
            contentType(ContentType.Application.Json)
            headers {
                append("X-YouTube-Client-Name", "67")
                append("X-YouTube-Client-Version", "2.0")
            }
            setBody(requestBody)
        }.body<String>()
        chartJson.parseToJsonElement(raw).arrayFor(arrayKey)
            .mapNotNull(::chartSong)
            .distinctBy(Innertube.SongItem::key)
    }.getOrDefault(emptyList())
