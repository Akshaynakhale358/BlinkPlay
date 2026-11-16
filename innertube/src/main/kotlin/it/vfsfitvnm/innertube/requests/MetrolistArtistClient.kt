package it.vfsfitvnm.innertube.requests

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.compression.brotli
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.headers
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import it.vfsfitvnm.innertube.models.Context
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

/**
 * Standalone Metrolist-style artist network client.
 * Artist search, browse and continuation requests never use Innertube.client.
 */
internal object MetrolistArtistClient {
    private val context = Context.DefaultWeb.copy(
        client = Context.DefaultWeb.client.copy(hl = "hi", gl = "IN")
    )

    val client = HttpClient(OkHttp) {
        expectSuccess = true
        install(HttpTimeout) {
            requestTimeoutMillis = 20000
            connectTimeoutMillis = 10000
            socketTimeoutMillis = 20000
        }
        install(ContentNegotiation) {
            @OptIn(ExperimentalSerializationApi::class)
            json(Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = true
            })
        }
        install(ContentEncoding) {
            brotli()
            gzip()
            deflate()
        }
        defaultRequest {
            url(scheme = "https", host = "music.youtube.com") {
                headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                parameters.append("prettyPrint", "false")
            }
            headers {
                set(HttpHeaders.Origin, "https://music.youtube.com")
                set(HttpHeaders.Referrer, "https://music.youtube.com/")
                set("X-YouTube-Client-Name", context.client.id.toString())
                set("X-YouTube-Client-Version", context.client.clientVersion)
                context.client.userAgent?.let { set(HttpHeaders.UserAgent, it) }
            }
        }
    }

    const val browse = "/youtubei/v1/browse"
    const val search = "/youtubei/v1/search"
}
