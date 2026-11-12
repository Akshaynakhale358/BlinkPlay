package it.vfsfitvnm.vimusic

import android.app.ActivityManager
import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import it.vfsfitvnm.vimusic.service.PlayerService
import it.vfsfitvnm.vimusic.utils.autoClearCacheEnabledKey
import it.vfsfitvnm.vimusic.utils.preferences

/**
 * Clears temporary app cache once a day while music playback is stopped.
 * User data, downloads, playlists, preferences and the database are not touched.
 */
class DailyCacheCleanupWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    @Suppress("DEPRECATION")
    override fun doWork(): Result {
        if (!applicationContext.preferences.getBoolean(autoClearCacheEnabledKey, true)) {
            return Result.success()
        }

        val activityManager =
            applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        val isPlayerServiceRunning = activityManager
            .getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == PlayerService::class.java.name }

        // Never remove the audio cache while the player service may be using it.
        if (isPlayerServiceRunning) return Result.success()

        applicationContext.cacheDir.listFiles()?.forEach { file ->
            file.deleteRecursively()
        }

        return Result.success()
    }
}
