package com.safeher.app.ui.user

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
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
    onUserUpdated: (User) -> Unit = {},
    homeViewModel: HomeViewModel = viewModel(),
    profileViewModel: ProfileViewModel = viewModel(),
    sosViewModel: SosViewModel = viewModel()
) {
    var selectedTab by remember { mutableStateOf(UserHomeTab.HOME) }
    val offlineSosMessage by sosViewModel.offlineSosMessage.collectAsState()
    val currentLocation by
    homeViewModel.currentLocation.collectAsState()

    var showEditProfileDialog by remember { mutableStateOf(false) }
    val isSavingProfile by profileViewModel.isSavingProfile.collectAsState()
    val profileUpdateError by profileViewModel.profileUpdateError.collectAsState()

    LaunchedEffect(user.uid) {
        sosViewModel.observeActiveAlert(user.uid)
        homeViewModel.startLocationUpdates(user.uid)
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
                                currentLocation = currentLocation,
                                sosViewModel = sosViewModel
                            )
                        }
                        UserHomeTab.PROFILE -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp)
                            ) {
                                Card(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(text = "User Profile", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                            IconButton(onClick = { showEditProfileDialog = true }) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Edit Profile"
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(text = "Name: ${user.name}", fontSize = 15.sp)
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

                                Spacer(modifier = Modifier.height(16.dp))
                                com.safeher.app.ui.map.OfflineMapCard()
                                Spacer(modifier = Modifier.height(16.dp))

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

            if (showEditProfileDialog) {
                EditProfileDialog(
                    user = user,
                    isSaving = isSavingProfile,
                    errorMessage = profileUpdateError,
                    onDismiss = {
                        showEditProfileDialog = false
                        profileViewModel.clearProfileUpdateError()
                    },
                    onSave = { newName, newPhone ->
                        profileViewModel.updateProfile(
                            currentUser = user,
                            newName = newName,
                            newPhone = newPhone,
                            onUpdated = { updatedUser ->
                                onUserUpdated(updatedUser)
                                showEditProfileDialog = false
                            }
                        )
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileDialog(
    user: User,
    isSaving: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String) -> Unit
) {
    var name by remember { mutableStateOf(user.name) }
    var phone by remember { mutableStateOf(user.phone) }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("Edit Profile") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone") },
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                )
                errorMessage?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, phone) },
                enabled = !isSaving
            ) {
                Text(if (isSaving) "Saving..." else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Cancel")
            }
        }
    )
}