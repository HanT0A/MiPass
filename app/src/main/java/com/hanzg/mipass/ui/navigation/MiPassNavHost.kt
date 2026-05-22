package com.hanzg.mipass.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import android.net.Uri
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hanzg.mipass.ui.screens.AddPasswordScreen
import com.hanzg.mipass.ui.screens.GeneratorScreen
import com.hanzg.mipass.ui.screens.PasswordDetailScreen
import com.hanzg.mipass.ui.screens.SettingsScreen
import com.hanzg.mipass.ui.screens.VaultScreen
import com.hanzg.mipass.utils.ClipboardUtils
import com.hanzg.mipass.data.local.AppPreferences
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
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val context = LocalContext.current
    val clipboardUtils = remember {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            NavHostEntryPoint::class.java
        )
        entryPoint.clipboardUtils()
    }

    val showBottomBar = bottomNavItems.any { item ->
        currentDestination?.hierarchy?.any { it.route == item.route } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    modifier = Modifier.height(64.dp),
                    windowInsets = WindowInsets.navigationBars
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == item.route
                        } == true

                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            },
                            label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NavRoutes.Vault.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(NavRoutes.Vault.route) {
                VaultScreen(
                    onNavigateToDetail = { id ->
                        navController.navigate(NavRoutes.Detail.createRoute(id))
                    },
                    onNavigateToAdd = { type ->
                        navController.navigate(NavRoutes.Add.createRoute(type))
                    },
                    clipboardUtils = clipboardUtils
                )
            }

            composable(NavRoutes.Generator.route) {
                GeneratorScreen(
                    clipboardUtils = clipboardUtils
                )
            }

            composable(NavRoutes.Settings.route) {
                SettingsScreen(
                    pendingImportUri = pendingImportUri
                )
            }

            composable(
                route = NavRoutes.Detail.route,
                arguments = listOf(navArgument("id") { type = NavType.StringType })
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
                arguments = listOf(navArgument("type") { type = NavType.StringType })
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
