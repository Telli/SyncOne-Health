package com.syncone.health.presentation.navigation

import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.syncone.health.presentation.auth.AppLockScreen
import com.syncone.health.presentation.detail.ThreadDetailScreen
import com.syncone.health.presentation.monitor.SmsMonitorScreen

sealed class Screen(val route: String) {
    object AppLock : Screen("app_lock")
    object Monitor : Screen("monitor")
    object ThreadDetail : Screen("thread_detail/{threadId}") {
        fun createRoute(threadId: Long) = "thread_detail/$threadId"
    }
}

@Composable
fun Navigation(
    navController: NavHostController,
    startDestination: String
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.AppLock.route) {
            AppLockScreen(
                onUnlocked = {
                    navController.navigate(Screen.Monitor.route) {
                        popUpTo(Screen.AppLock.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Monitor.route) {
            SmsMonitorScreen(
                onThreadClick = { threadId ->
                    navController.navigate(Screen.ThreadDetail.createRoute(threadId))
                }
            )
        }

        composable(
            route = Screen.ThreadDetail.route,
            arguments = listOf(
                navArgument("threadId") { type = NavType.LongType }
            )
        ) {
            ThreadDetailScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
