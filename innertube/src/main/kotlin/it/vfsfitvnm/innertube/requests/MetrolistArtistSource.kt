package it.vfsfitvnm.innertube.requests

import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.bodies.BrowseBody

/**
 * Artist data-source boundary used by BlinkPlay's ArtistScreen.
 *
 * This is currently a ZPlay-compatible adapter around the existing typed
 * InnerTube Artist parser. The original Metrolist parser and its InnertubeX /
 * shared-model dependencies are preserved under artist-reference and still
 * need a full dependency-compatible port before this function can invoke them.
 *
 * Keep this boundary stable: once that port is complete, replace the delegate
 * below with the Metrolist parser without changing the Artist UI.
 */
suspend fun Innertube.metrolistArtistPage(
    body: BrowseBody
): Result<Innertube.ArtistPage>? = artistPage(body)
