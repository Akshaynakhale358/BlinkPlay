package it.vfsfitvnm.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class Tabs(
    val tabs: List<Tab>? = null
) {
    @Serializable
    data class Tab(
        val tabRenderer: TabRenderer? = null
    ) {
        @Serializable
        data class TabRenderer(
            val content: Content? = null,
            val title: String? = null,
            val tabIdentifier: String? = null,
        ) {
            @Serializable
            data class Content(
                val sectionListRenderer: SectionListRenderer? = null,
            )
        }
    }
}
