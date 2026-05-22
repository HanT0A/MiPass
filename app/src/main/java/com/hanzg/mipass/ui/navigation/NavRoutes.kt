package com.hanzg.mipass.ui.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.*

sealed class NavRoutes(val route: String) {
    data object Vault : NavRoutes("vault")
    data object Generator : NavRoutes("generator")
    data object Settings : NavRoutes("settings")

    data object Detail : NavRoutes("detail/{id}") {
        fun createRoute(id: String) = "detail/$id"
    }

    data object Add : NavRoutes("add/{type}") {
        fun createRoute(type: String) = "add/$type"
    }
}

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)

val bottomNavItems = listOf(
    BottomNavItem("密码库", PhosphorIcons.Regular.LockSimple, NavRoutes.Vault.route),
    BottomNavItem("生成器", PhosphorIcons.Regular.Password, NavRoutes.Generator.route),
    BottomNavItem("设置", PhosphorIcons.Regular.GearSix, NavRoutes.Settings.route)
)
