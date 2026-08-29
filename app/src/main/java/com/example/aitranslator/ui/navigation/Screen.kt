package com.example.aitranslator.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Recording : Screen("recording/{conversationId}") {
        fun createRoute(conversationId: Long) = "recording/$conversationId"
    }
    data object Translation : Screen("translation/{conversationId}") {
        fun createRoute(conversationId: Long) = "translation/$conversationId"
    }
    data object History : Screen("history")
    data object Settings : Screen("settings")
}
