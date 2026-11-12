package it.vfsfitvnm.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class PlayerResponse(
    val playabilityStatus: PlayabilityStatus?,
    val playerConfig: PlayerConfig?,
    val streamingData: StreamingData?,
    val videoDetails: VideoDetails?,
) {
    @Serializable
    data class PlayabilityStatus(
        val status: String?,
        /** YouTube's own words for a refusal, e.g. "Video unavailable". Already in the mask. */
        val reason: String? = null
    )

    @Serializable
    data class PlayerConfig(
        val audioConfig: AudioConfig?
    ) {
        @Serializable
        data class AudioConfig(
            private val loudnessDb: Double?,
            private val perceptualLoudnessDb: Double? = null
        ) {
            /**
             * How much louder than YouTube's reference level this track is, in dB.
             * Playback applies the negation of this as gain.
             */
            val normalizedLoudnessDb: Float?
                get() = loudnessDb?.toFloat()
        }
    }

    @Serializable
    data class StreamingData(
        val adaptiveFormats: List<AdaptiveFormat>?
    ) {
        /**
         * Prefer the highest-bitrate audio-only format actually supplied by YouTube.
         * YouTube does not expose 320 kbps for every track, so never upsample or label
         * a lower-bitrate source as 320 kbps.
         */
        private val playableAudioFormats: List<AdaptiveFormat>
            get() = adaptiveFormats.orEmpty().filter {
                (it.url != null || it.signatureCipher != null) &&
                    it.mimeType.startsWith("audio/")
            }

        /** Prefer a genuine 320 kbps stream when available, otherwise best source quality. */
        val preferred320KbpsFormat: AdaptiveFormat?
            get() = playableAudioFormats
                .filter { (it.averageBitrate ?: it.bitrate ?: 0L) >= 320_000L }
                .maxByOrNull { it.averageBitrate ?: it.bitrate ?: 0L }
                ?: highestQualityFormat

        val highestQualityFormat: AdaptiveFormat?
            get() = playableAudioFormats.maxByOrNull { it.averageBitrate ?: it.bitrate ?: 0L }

        @Serializable
        data class AdaptiveFormat(
            val itag: Int,
            val mimeType: String,
            val bitrate: Long?,
            val averageBitrate: Long?,
            val contentLength: Long?,
            val audioQuality: String?,
            val approxDurationMs: Long?,
            val lastModified: Long?,
            val loudnessDb: Double?,
            val audioSampleRate: Int?,
            val url: String?,
            /** Web clients return this in place of [url]; it has to be deciphered first. */
            val signatureCipher: String? = null,
        )
    }

    @Serializable
    data class VideoDetails(
        val videoId: String?
    )
}
