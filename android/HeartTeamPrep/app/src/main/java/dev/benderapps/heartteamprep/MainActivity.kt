package dev.benderapps.heartteamprep

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.benderapps.heartteamprep.ui.AppRoot
import dev.benderapps.heartteamprep.ui.theme.HeartTeamPrepTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HeartTeamPrepTheme {
                AppRoot()
            }
        }
    }
}
