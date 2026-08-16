package com.betteraichat.ui.navigation

import androidx.compose.runtime.Composable
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

    fun chat(id: Long) = "chat/$id"
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.CONVERSATIONS) {
        composable(Routes.CONVERSATIONS) {
            ConversationListScreen(
                onOpenChat = { id -> navController.navigate(Routes.chat(id)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
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
