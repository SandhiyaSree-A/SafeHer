package com.safeher.app.ui.user

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safeher.app.data.model.User
import com.safeher.app.ui.home.HomeTabContent
import com.safeher.app.ui.home.HomeViewModel
import com.safeher.app.ui.journey.JourneyTabContent
import com.safeher.app.ui.profile.EmergencyContactsSection
import com.safeher.app.ui.profile.ProfileViewModel
import com.safeher.app.ui.sos.ActiveAlertBanner
import com.safeher.app.ui.sos.SosFloatingButton
import com.safeher.app.ui.sos.SosViewModel

enum class UserHomeTab(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    JOURNEY("Journey", Icons.Default.Map),
    PROFILE("Profile", Icons.Default.Person)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserHomeScreen(
    user: User,
    onSignOut: () -> Unit,
    homeViewModel: HomeViewModel = viewModel(),
    profileViewModel: ProfileViewModel = viewModel(),
    sosViewModel: SosViewModel = viewModel()
) {
    var selectedTab by remember { mutableStateOf(UserHomeTab.HOME) }
    val offlineSosMessage by sosViewModel.offlineSosMessage.collectAsState()
    val currentLocation by
    homeViewModel.currentLocation.collectAsState()

    LaunchedEffect(user.uid) {
        sosViewModel.observeActiveAlert(user.uid)
    }

    LaunchedEffect(offlineSosMessage) {
        if (offlineSosMessage != null) {
            kotlinx.coroutines.delay(10000)
            sosViewModel.clearOfflineSosMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "SafeHer - ${selectedTab.title}",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                UserHomeTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = (selectedTab == tab),
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Persistent Active Alert Banner (shown across all tabs when SOS is active)
                ActiveAlertBanner(
                    user = user,
                    currentLocation = currentLocation,
                    viewModel = sosViewModel
                )
                offlineSosMessage?.let { message ->
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(message, modifier = Modifier.padding(12.dp))
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        UserHomeTab.HOME -> {
                            HomeTabContent(
                                user = user,
                                viewModel = homeViewModel
                            )
                        }
                        UserHomeTab.JOURNEY -> {
                            JourneyTabContent(
                                user = user,
                                sosViewModel = sosViewModel
                            )
                        }
                        UserHomeTab.PROFILE -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                            ) {
                                Card(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(text = "User Profile", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(text = "Phone: ${user.phone}", fontSize = 15.sp)
                                        Text(text = "Role: ${user.role}", fontSize = 14.sp, color = MaterialTheme.colorScheme.outline)
                                        Text(text = "UID: ${user.uid}", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                EmergencyContactsSection(
                                    user = user,
                                    viewModel = profileViewModel
                                )

                                Spacer(modifier = Modifier.weight(1f))

                                Button(
                                    onClick = onSignOut,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Sign Out")
                                }
                            }
                        }
                    }
                }
            }

            // Always-Accessible Prominent SOS Floating Action Button
            SosFloatingButton(
                user = user,
                viewModel = sosViewModel,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
            )
        }
    }
}
