package com.betteraichat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.betteraichat.ui.navigation.AppNavHost
import com.betteraichat.ui.theme.BetterAIChatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BetterAIChatTheme {
                AppNavHost()
            }
        }
    }
}
