package com.safeher.app.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.safeher.app.data.model.User
import com.safeher.app.ui.admin.AdminHomeScreen
import com.safeher.app.ui.auth.AuthScreen
import com.safeher.app.ui.auth.AuthViewModel
import com.safeher.app.ui.user.UserHomeScreen
import com.safeher.app.ui.offline.OfflineBanner

@Composable
fun SafeHerNavGraph(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    onSignOut: () -> Unit
) {
    var currentUserState by remember { mutableStateOf<User?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        OfflineBanner()
        NavHost(
            navController = navController,
            startDestination = Screen.Auth.route,
            modifier = Modifier.weight(1f)
        ) {
        composable(Screen.Auth.route) {
            AuthScreen(
                viewModel = authViewModel,
                onAuthSuccess = { user ->
                    currentUserState = user
                    if (user.role == User.ROLE_ADMIN) {
                        navController.navigate(Screen.AdminHome.route) {
                            popUpTo(Screen.Auth.route) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Screen.UserHome.route) {
                            popUpTo(Screen.Auth.route) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(Screen.UserHome.route) {
            currentUserState?.let { user ->
                UserHomeScreen(
                    user = user,
                    onUserUpdated = { updatedUser ->
                        currentUserState = updatedUser
                    },
                    onSignOut = {
                        onSignOut()
                        navController.navigate(Screen.Auth.route) {
                            popUpTo(Screen.UserHome.route) { inclusive = true }
                        }
                    }
                )
            }
        }

        composable(Screen.AdminHome.route) {
            currentUserState?.let { user ->
                AdminHomeScreen(
                    user = user,
                    onSignOut = {
                        onSignOut()
                        navController.navigate(Screen.Auth.route) {
                            popUpTo(Screen.AdminHome.route) { inclusive = true }
                        }
                    }
                )
            }
        }
        }
    }
}