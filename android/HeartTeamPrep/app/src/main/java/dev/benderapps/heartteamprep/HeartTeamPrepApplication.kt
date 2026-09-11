package dev.benderapps.heartteamprep

import android.app.Application
import com.parse.Parse

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
    }
}
