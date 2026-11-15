package it.vfsfitvnm.innertube.requests

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistAudioFilterTest {
    @Test
    fun excludesMetrolistVideoTypesFromAudioArtistShelves() {
        listOf(
            "MUSIC_VIDEO_TYPE_OMV",
            "MUSIC_VIDEO_TYPE_UGC",
            "MUSIC_VIDEO_TYPE_VIDEO",
            "MUSIC_VIDEO_TYPE_LIVE",
            "MUSIC_VIDEO_TYPE_PODCAST_EPISODE",
        ).forEach { type ->
            assertFalse("$type must not appear as an audio artist track", isMetrolistArtistAudioType(type))
        }
    }

    @Test
    fun retainsAudioTrackCounterpartsAndUnknownTypes() {
        assertTrue(isMetrolistArtistAudioType("MUSIC_VIDEO_TYPE_ATV"))
        assertTrue(isMetrolistArtistAudioType(null))
        assertTrue(isMetrolistArtistAudioType(""))
        assertTrue(isMetrolistArtistAudioType("MUSIC_VIDEO_TYPE_UNSPECIFIED"))
    }

    @Test
    fun classificationIsCaseInsensitiveAndWhitespaceTolerant() {
        assertFalse(isMetrolistArtistAudioType(" music_video_type_podcast_episode "))
        assertFalse(isMetrolistArtistAudioType("music_video_type_ugc"))
        assertTrue(isMetrolistArtistAudioType(" music_video_type_atv "))
    }
}
