package com.betteraichat.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.betteraichat.ui.chat.ChatScreen
import com.betteraichat.ui.conversations.ConversationListScreen
import com.betteraichat.ui.settings.SettingsScreen

object Routes {
    const val CONVERSATIONS = "conversations"
    const val CHAT = "chat/{id}"
    const val SETTINGS = "settings"
    const val STARRED = "starred"

    fun chat(id: Long) = "chat/$id"
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val container = remember {
        (context.applicationContext as com.betteraichat.BetterAIChatApp).container
    }
    val shareTick by container.shareNavTick.collectAsStateWithLifecycle()
    LaunchedEffect(shareTick) {
        if (shareTick > 0) {
            navController.navigate(Routes.chat(-1L))
        }
    }
    NavHost(
        navController = navController,
        startDestination = Routes.CONVERSATIONS,
        enterTransition = {
            androidx.compose.animation.slideInHorizontally(
                animationSpec = androidx.compose.animation.core.tween(300)
            ) { it / 3 } + androidx.compose.animation.fadeIn(
                androidx.compose.animation.core.tween(300)
            )
        },
        exitTransition = {
            androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(220))
        },
        popEnterTransition = {
            androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(300))
        },
        popExitTransition = {
            androidx.compose.animation.slideOutHorizontally(
                animationSpec = androidx.compose.animation.core.tween(300)
            ) { it / 3 } + androidx.compose.animation.fadeOut(
                androidx.compose.animation.core.tween(220)
            )
        }
    ) {
        composable(Routes.CONVERSATIONS) {
            ConversationListScreen(
                onOpenChat = { id -> navController.navigate(Routes.chat(id)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenStarred = { navController.navigate(Routes.STARRED) }
            )
        }
        composable(Routes.STARRED) {
            com.betteraichat.ui.starred.StarredScreen(
                onBack = { navController.popBackStack() },
                onOpenChat = { id -> navController.navigate(Routes.chat(id)) }
            )
        }
        composable(
            route = Routes.CHAT,
            arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L })
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: -1L
            ChatScreen(conversationId = id, onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
