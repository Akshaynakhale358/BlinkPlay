package it.vfsfitvnm.innertube.requests

/**
 * Metrolist-compatible artist audio filtering.
 *
 * YouTube Music artist shelves can mix audio tracks, official/user videos,
 * live recordings and podcast episodes. Keep ATV audio-track counterparts
 * and unknown types (some valid audio rows omit this metadata).
 */
internal fun isMetrolistArtistAudioType(rawType: String?): Boolean {
    val type = rawType
        ?.uppercase()
        ?.trim()
        ?.removePrefix("MUSIC_VIDEO_TYPE_")
        .orEmpty()

    if ("PODCAST" in type) return false

    val isVideo =
        "OMV" in type ||
            "OFFICIAL_SOURCE_MUSIC_VIDEO" in type ||
            "OFFICIAL_SOURCE" in type ||
            "UGC" in type ||
            "USER_GENERATED" in type ||
            type == "VIDEO" ||
            "VIDEO" in type ||
            "LIVE" in type

    return !isVideo
}
