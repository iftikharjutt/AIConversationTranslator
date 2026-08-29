package com.example.aitranslator.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.aitranslator.ui.history.HistoryScreen
import com.example.aitranslator.ui.history.HistoryViewModel
import com.example.aitranslator.ui.home.HomeScreen
import com.example.aitranslator.ui.home.HomeViewModel
import com.example.aitranslator.ui.recording.RecordingScreen
import com.example.aitranslator.ui.recording.RecordingViewModel
import com.example.aitranslator.ui.settings.SettingsScreen
import com.example.aitranslator.ui.settings.SettingsViewModel
import com.example.aitranslator.ui.translation.TranslationScreen
import com.example.aitranslator.ui.translation.TranslationViewModel

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            val viewModel: HomeViewModel = hiltViewModel()
            HomeScreen(
                viewModel = viewModel,
                onStartRecording = { conversationId ->
                    navController.navigate(Screen.Recording.createRoute(conversationId))
                },
                onOpenConversation = { conversationId ->
                    navController.navigate(Screen.Translation.createRoute(conversationId))
                },
                onOpenHistory = {
                    navController.navigate(Screen.History.route)
                },
                onOpenSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(
            route = Screen.Recording.route,
            arguments = listOf(navArgument("conversationId") { type = NavType.LongType })
        ) {
            val viewModel: RecordingViewModel = hiltViewModel()
            RecordingScreen(
                viewModel = viewModel,
                onNavigateToDetail = { conversationId ->
                    navController.navigate(Screen.Translation.createRoute(conversationId)) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }

        composable(
            route = Screen.Translation.route,
            arguments = listOf(navArgument("conversationId") { type = NavType.LongType })
        ) {
            val viewModel: TranslationViewModel = hiltViewModel()
            TranslationScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.History.route) {
            val viewModel: HistoryViewModel = hiltViewModel()
            HistoryScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onOpenConversation = { conversationId ->
                    navController.navigate(Screen.Translation.createRoute(conversationId))
                }
            )
        }

        composable(Screen.Settings.route) {
            val viewModel: SettingsViewModel = hiltViewModel()
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
