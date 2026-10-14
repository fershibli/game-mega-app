package io.github.fershibli.megagames

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.fershibli.megagames.core.SaveStore
import io.github.fershibli.megagames.core.SettingsStore
import io.github.fershibli.megagames.ui.MegaGamesApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settingsStore = SettingsStore(applicationContext)
        val saveStore = SaveStore(applicationContext)
        // Only a cold start offers to resume the saved game; a recreated activity keeps going.
        val coldStart = savedInstanceState == null
        setContent {
            MegaGamesApp(settingsStore, saveStore, offerResume = coldStart)
        }
    }
}
