package it.vfsfitvnm.innertube.requests

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.BrowseResponse
import it.vfsfitvnm.innertube.models.bodies.BrowseBody

/**
 * Artist-only source boundary adapted from MetrolistGroup/Metrolist's artist data flow
 * (GPL-3.0). Uses BlinkPlay's HTTP client and UI-facing model types; no Metrolist UI or
 * unrelated Last.fm/Apple Music/settings modules are imported.
 */
suspend fun Innertube.metrolistArtistPage(body: BrowseBody): Result<Innertube.ArtistPage>? =
    runCatchingNonCancellable {
        val response = client.post(browse) {
            setBody(body)
            mask("contents,header,microformat")
        }.body<BrowseResponse>()
        MetrolistArtistParser.parseArtist(response, body.browseId)
    }
