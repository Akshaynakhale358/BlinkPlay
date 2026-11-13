package it.vfsfitvnm.vimusic.utils

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline

val Player.currentWindow: Timeline.Window?
    get() = if (mediaItemCount == 0) null else currentTimeline.getWindow(currentMediaItemIndex, Timeline.Window())

val Timeline.mediaItems: List<MediaItem>
    get() = List(windowCount) {
        getWindow(it, Timeline.Window()).mediaItem
    }

inline val Timeline.windows: List<Timeline.Window>
    get() = List(windowCount) {
        getWindow(it, Timeline.Window())
    }

val Player.shouldBePlaying: Boolean
    get() = !(playbackState == Player.STATE_ENDED || !playWhenReady)

fun Player.seamlessPlay(mediaItem: MediaItem) {
    if (mediaItem.mediaId == currentMediaItem?.mediaId) {
        if (currentMediaItemIndex > 0) removeMediaItems(0, currentMediaItemIndex)
        if (currentMediaItemIndex < mediaItemCount - 1) removeMediaItems(currentMediaItemIndex + 1, mediaItemCount)
    } else {
        forcePlay(mediaItem)
    }
}

fun Player.shuffleQueue() {
    val currentIndex = currentMediaItemIndex
    if (currentIndex !in 0 until mediaItemCount) return

    // Keep the current item playing at its current position; only shuffle the rest.
    val remainingItems = currentTimeline.mediaItems.filterIndexed { index, _ ->
        index != currentIndex
    }
    if (currentIndex > 0) removeMediaItems(0, currentIndex)
    if (mediaItemCount > 1) removeMediaItems(1, mediaItemCount)
    if (remainingItems.isNotEmpty()) addMediaItems(1, remainingItems.shuffled())
}

fun Player.forcePlay(mediaItem: MediaItem) {
    setMediaItem(mediaItem, true)
    playWhenReady = true
    prepare()
}

fun Player.forcePlayAtIndex(mediaItems: List<MediaItem>, mediaItemIndex: Int) {
    if (mediaItems.isEmpty()) return

    setMediaItems(mediaItems, mediaItemIndex, C.TIME_UNSET)
    playWhenReady = true
    prepare()
}

fun Player.forcePlayFromBeginning(mediaItems: List<MediaItem>) =
    forcePlayAtIndex(mediaItems, 0)

fun Player.forceSeekToPrevious() {
    if (hasPreviousMediaItem() || currentPosition > maxSeekToPreviousPosition) {
        seekToPrevious()
    } else if (mediaItemCount > 0) {
        seekTo(mediaItemCount - 1, C.TIME_UNSET)
    }
}

fun Player.forceSeekToNext() =
    if (hasNextMediaItem()) seekToNext() else seekTo(0, C.TIME_UNSET)

fun Player.addNext(mediaItem: MediaItem) {
    if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
        forcePlay(mediaItem)
    } else {
        addMediaItem(currentMediaItemIndex + 1, mediaItem)
    }
}

fun Player.enqueue(mediaItem: MediaItem) {
    if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
        forcePlay(mediaItem)
    } else {
        addMediaItem(mediaItemCount, mediaItem)
    }
}

fun Player.enqueue(mediaItems: List<MediaItem>) {
    if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
        forcePlayFromBeginning(mediaItems)
    } else {
        addMediaItems(mediaItemCount, mediaItems)
    }
}

fun Player.findNextMediaItemById(mediaId: String): MediaItem? {
    for (i in currentMediaItemIndex until mediaItemCount) {
        if (getMediaItemAt(i).mediaId == mediaId) {
            return getMediaItemAt(i)
        }
    }
    return null
}
