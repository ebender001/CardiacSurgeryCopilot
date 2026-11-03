package dev.benderapps.heartteamprep

import android.app.Application
import android.util.Log
import com.parse.Parse
import dev.benderapps.heartteamprep.service.PHIFilterService

class HeartTeamPrepApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Parse.initialize(
            Parse.Configuration.Builder(this)
                .applicationId(BackendConfig.applicationId)
                .clientKey(BackendConfig.clientKey)
                .server(BackendConfig.serverUrl.toString())
                .build()
        )

        // Loading the PHI models takes about a second -- do it now, off the
        // main thread, so the first case submission doesn't wait on it.
        Thread {
            try {
                PHIFilterService.warmUp()
            } catch (e: Throwable) {
                // Retried (and surfaced) when a case is actually submitted -- see PHIFilterService.
                Log.w("HeartTeamPrep", "PHI filter warm-up failed", e)
            }
        }.start()
    }
}
