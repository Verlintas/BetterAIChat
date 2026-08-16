package com.betteraichat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.betteraichat.AppContainer
import com.betteraichat.BetterAIChatApp

@Composable
fun rememberContainer(): AppContainer {
    val context = LocalContext.current
    return remember { (context.applicationContext as BetterAIChatApp).container }
}
