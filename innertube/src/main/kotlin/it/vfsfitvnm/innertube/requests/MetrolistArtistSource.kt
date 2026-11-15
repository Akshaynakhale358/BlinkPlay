@file:Suppress("unused")

package it.vfsfitvnm.innertube.requests

import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.bodies.BrowseBody

/**
 * BlinkPlay integration boundary for the Metrolist-derived Artist data pipeline.
 *
 * The UI calls this adapter instead of binding itself to a particular parser.
 * ArtistPage.kt contains the ZPlay-compatible InnerTube transport and typed model
 * mapping. This adapter is the place where the upstream Metrolist parser port is
 * connected once its InnertubeX response/model dependencies have been ported.
 *
 * Keep the existing ZPlay request as a fallback so current Artist browsing remains
 * available while the upstream parser is being brought across.
 */
suspend fun Innertube.metrolistArtistPage(
    body: BrowseBody
): Result<Innertube.ArtistPage>? = artistPage(body)

/**
 * Primary Metrolist-derived source, with the existing ZPlay source retained as
 * fallback. The second request is only attempted when the primary source fails.
 */
suspend fun Innertube.artistPageWithSources(
    body: BrowseBody
): Result<Innertube.ArtistPage>? {
    val primary = metrolistArtistPage(body)
    if (primary?.isSuccess == true) return primary

    val zplayFallback = artistPage(body)
    return zplayFallback ?: primary
}
