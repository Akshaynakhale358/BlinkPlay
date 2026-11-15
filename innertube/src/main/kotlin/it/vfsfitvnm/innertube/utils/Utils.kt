package it.vfsfitvnm.innertube.utils

import io.ktor.utils.io.CancellationException
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.SectionListRenderer

internal fun SectionListRenderer.findSectionByTitle(text: String): SectionListRenderer.Content? {
    return contents?.find { content ->
        val title = content
            .musicCarouselShelfRenderer
            ?.header
            ?.musicCarouselShelfBasicHeaderRenderer
            ?.title
            ?: content
                .musicShelfRenderer
                ?.title

        title
            ?.runs
            ?.firstOrNull()
            ?.text == text
    }
}

/**
 * Finds a section whose title *contains* [text], ignoring case.
 *
 * Exact matching broke silently as YouTube renamed shelves: an artist's songs are under "Top songs"
 * and their singles under "Singles & EPs", so lookups for "Songs" and "Singles" found nothing and
 * those sections simply vanished from the page with no error anywhere.
 */
internal fun SectionListRenderer.findSectionByTitleContaining(text: String): SectionListRenderer.Content? {
    return contents?.find { content ->
        val title = content
            .musicCarouselShelfRenderer
            ?.header
            ?.musicCarouselShelfBasicHeaderRenderer
            ?.title
            ?: content
                .musicShelfRenderer
                ?.title

        title
            ?.runs
            ?.firstOrNull()
            ?.text
            ?.contains(text, ignoreCase = true) == true
    }
}

/**
 * Adapted from haturatu/ViMusic (GPL-3.0).
 * Matches known artist shelf aliases and tolerates YouTube's title variations.
 */
internal fun SectionListRenderer.findSectionByTitleAny(vararg texts: String): SectionListRenderer.Content? {
    fun normalize(value: String?) = value?.trim()?.lowercase()

    return contents?.find { content ->
        val title = normalize(
            content.musicCarouselShelfRenderer
                ?.header
                ?.musicCarouselShelfBasicHeaderRenderer
                ?.title
                ?.runs
                ?.firstOrNull()
                ?.text
                ?: content.musicShelfRenderer
                    ?.title
                    ?.runs
                    ?.firstOrNull()
                    ?.text
        )

        title != null && texts.any { text ->
            val target = normalize(text)
            target != null && (title == target || title.contains(target))
        }
    }
}

internal fun SectionListRenderer.findSectionByStrapline(text: String): SectionListRenderer.Content? {
    return contents?.find { content ->
        content
            .musicCarouselShelfRenderer
            ?.header
            ?.musicCarouselShelfBasicHeaderRenderer
            ?.strapline
            ?.runs
            ?.firstOrNull()
            ?.text == text
    }
}

internal inline fun <R> runCatchingNonCancellable(block: () -> R): Result<R>? {
    val result = runCatching(block)
    return when (result.exceptionOrNull()) {
        is CancellationException -> null
        else -> result
    }
}

infix operator fun <T : Innertube.Item> Innertube.ItemsPage<T>?.plus(other: Innertube.ItemsPage<T>) =
    other.copy(
        items = (this?.items?.plus(other.items ?: emptyList())
            ?: other.items)?.distinctBy(Innertube.Item::key)
    )
