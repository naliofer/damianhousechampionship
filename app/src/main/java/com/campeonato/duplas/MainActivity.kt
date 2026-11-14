package com.campeonato.duplas

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.campeonato.duplas.data.ChampionshipRepository
import com.campeonato.duplas.ui.screens.ChampionshipApp
import com.campeonato.duplas.ui.theme.CampeonatoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("championship", Activity.MODE_PRIVATE)
        val repository = ChampionshipRepository(prefs)
        setContent {
            CampeonatoTheme {
                ChampionshipApp(repository)
            }
        }
    }
}
