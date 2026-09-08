package com.safeher.app.ui.navigation

sealed class Screen(val route: String) {
    object Auth : Screen("auth_screen")
    object UserHome : Screen("user_home_screen")
    object AdminHome : Screen("admin_home_screen")
}
