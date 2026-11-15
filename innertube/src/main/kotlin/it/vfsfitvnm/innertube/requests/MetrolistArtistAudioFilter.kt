package it.vfsfitvnm.innertube.requests

import it.vfsfitvnm.innertube.Innertube

/**
 * Artist-shelf audio classification adapted from MetrolistGroup/Metrolist ArtistSongs
 * (GPL-3.0). Unknown types are retained because YouTube omits this metadata on valid audio rows.
 */
internal fun isMetrolistArtistAudio(song: Innertube.SongItem): Boolean {
    val type = song.info?.endpoint?.type
        ?.uppercase()
        ?.trim()
        ?.removePrefix("MUSIC_VIDEO_TYPE_")
        .orEmpty()

    if ("PODCAST" in type) return false

    val isVideo = "OMV" in type ||
        "OFFICIAL_SOURCE_MUSIC_VIDEO" in type ||
        "OFFICIAL_SOURCE" in type ||
        "UGC" in type ||
        "USER_GENERATED" in type ||
        type == "VIDEO" ||
        "VIDEO" in type ||
        "LIVE" in type

    return !isVideo
}
