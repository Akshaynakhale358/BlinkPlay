package it.vfsfitvnm.innertube.models

import kotlinx.serialization.Serializable

/**
 * Album and playlist browse responses switched from `singleColumnBrowseResultsRenderer` to this
 * two-column shape: the header sits under [tabs], the track list under [secondaryContents].
 */
@Serializable
data class TwoColumnBrowseResults(
    val secondaryContents: SecondaryContents? = null,
    val tabs: List<Tabs.Tab>? = null
) {
    @Serializable
    data class SecondaryContents(
        val sectionListRenderer: SectionListRenderer? = null
    )
}
