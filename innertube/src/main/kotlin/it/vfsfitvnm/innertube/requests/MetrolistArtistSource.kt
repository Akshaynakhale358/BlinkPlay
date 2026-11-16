package it.vfsfitvnm.innertube.requests

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.BrowseResponse
import it.vfsfitvnm.innertube.models.bodies.BrowseBody
import it.vfsfitvnm.innertube.utils.runCatchingNonCancellable

/**
 * Artist-only source boundary adapted from MetrolistGroup/Metrolist's artist data flow
 * (GPL-3.0). Uses an isolated Metrolist artist HTTP client; BlinkPlay's playback/search client is not used.
 */
suspend fun Innertube.metrolistArtistPage(body: BrowseBody): Result<Innertube.ArtistPage>? =
    runCatchingNonCancellable {
        val response = MetrolistArtistClient.client.post(MetrolistArtistClient.browse) {
            setBody(body)
            mask("contents,header,microformat")
        }.body<BrowseResponse>()
        MetrolistArtistParser.parseArtist(response, body.browseId)
    }
