package com.hanzg.mipass.ui.navigation

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hanzg.mipass.data.local.AppPreferences
import com.hanzg.mipass.ui.screens.AddPasswordScreen
import com.hanzg.mipass.ui.screens.GeneratorScreen
import com.hanzg.mipass.ui.screens.PasswordDetailScreen
import com.hanzg.mipass.ui.screens.SettingsScreen
import com.hanzg.mipass.ui.screens.VaultScreen
import com.hanzg.mipass.ui.theme.DurationMedium
import com.hanzg.mipass.ui.theme.MiPassEaseIn
import com.hanzg.mipass.ui.theme.MiPassEaseOut
import com.hanzg.mipass.utils.ClipboardUtils
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NavHostEntryPoint {
    fun clipboardUtils(): ClipboardUtils
    fun appPreferences(): AppPreferences
}

@Composable
fun MiPassNavHost(
    navController: NavHostController = rememberNavController(),
    pendingImportUri: Uri? = null,
    onImportHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardUtils = remember {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            NavHostEntryPoint::class.java
        )
        entryPoint.clipboardUtils()
    }

    val onTabNavigate: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        NavHost(
            navController = navController,
            startDestination = NavRoutes.Vault.route,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(
                NavRoutes.Vault.route,
                enterTransition = { fadeIn(tween(100)) },
                exitTransition = { fadeOut(tween(70)) },
                popEnterTransition = { fadeIn(tween(100)) },
                popExitTransition = { fadeOut(tween(70)) }
            ) {
                VaultScreen(
                    onNavigateToDetail = { id ->
                        navController.navigate(NavRoutes.Detail.createRoute(id))
                    },
                    onNavigateToAdd = { type ->
                        navController.navigate(NavRoutes.Add.createRoute(type))
                    },
                    onNavigate = onTabNavigate,
                    clipboardUtils = clipboardUtils
                )
            }

            composable(
                NavRoutes.Generator.route,
                enterTransition = { fadeIn(tween(100)) },
                exitTransition = { fadeOut(tween(70)) },
                popEnterTransition = { fadeIn(tween(100)) },
                popExitTransition = { fadeOut(tween(70)) }
            ) {
                GeneratorScreen(
                    onNavigate = onTabNavigate,
                    clipboardUtils = clipboardUtils
                )
            }

            composable(
                NavRoutes.Settings.route,
                enterTransition = { fadeIn(tween(100)) },
                exitTransition = { fadeOut(tween(70)) },
                popEnterTransition = { fadeIn(tween(100)) },
                popExitTransition = { fadeOut(tween(70)) }
            ) {
                SettingsScreen(
                    onNavigate = onTabNavigate,
                    pendingImportUri = pendingImportUri
                )
            }

            composable(
                route = NavRoutes.Detail.route,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
                enterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = tween(DurationMedium, easing = MiPassEaseOut)
                    ) + fadeIn(tween(DurationMedium, easing = MiPassEaseOut))
                },
                exitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { -it / 3 },
                        animationSpec = tween(DurationMedium, easing = MiPassEaseIn)
                    ) + fadeOut(tween(DurationMedium, easing = MiPassEaseIn))
                },
                popEnterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { -it / 3 },
                        animationSpec = tween(DurationMedium, easing = MiPassEaseOut)
                    ) + fadeIn(tween(DurationMedium, easing = MiPassEaseOut))
                },
                popExitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(DurationMedium, easing = MiPassEaseIn)
                    ) + fadeOut(tween(DurationMedium, easing = MiPassEaseIn))
                }
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id") ?: return@composable
                PasswordDetailScreen(
                    passwordId = id,
                    onNavigateBack = { navController.popBackStack() },
                    clipboardUtils = clipboardUtils
                )
            }

            composable(
                route = NavRoutes.Add.route,
                arguments = listOf(navArgument("type") { type = NavType.StringType }),
                enterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = tween(DurationMedium, easing = MiPassEaseOut)
                    ) + fadeIn(tween(DurationMedium, easing = MiPassEaseOut))
                },
                exitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { -it / 3 },
                        animationSpec = tween(DurationMedium, easing = MiPassEaseIn)
                    ) + fadeOut(tween(DurationMedium, easing = MiPassEaseIn))
                },
                popEnterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { -it / 3 },
                        animationSpec = tween(DurationMedium, easing = MiPassEaseOut)
                    ) + fadeIn(tween(DurationMedium, easing = MiPassEaseOut))
                },
                popExitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(DurationMedium, easing = MiPassEaseIn)
                    ) + fadeOut(tween(DurationMedium, easing = MiPassEaseIn))
                }
            ) { backStackEntry ->
                val type = backStackEntry.arguments?.getString("type") ?: "APP"
                AddPasswordScreen(
                    entryType = type,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}