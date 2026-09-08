package com.safeher.app.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safeher.app.data.model.SosAlert
import com.safeher.app.data.model.User
import java.text.SimpleDateFormat
import java.util.*

enum class AdminViewMode {
    LIST, MAP
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminHomeScreen(
    user: User,
    onSignOut: () -> Unit,
    adminViewModel: AdminViewModel = viewModel()
) {
    val activeAlerts by adminViewModel.activeAlerts.collectAsState()
    var currentViewMode by remember { mutableStateOf(AdminViewMode.LIST) }
    var selectedAlertForDetail by remember { mutableStateOf<SosAlert?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Security Room - Control Center",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            currentViewMode = if (currentViewMode == AdminViewMode.LIST) AdminViewMode.MAP else AdminViewMode.LIST
                        }
                    ) {
                        Icon(
                            imageVector = if (currentViewMode == AdminViewMode.LIST) Icons.Default.Map else Icons.Default.List,
                            contentDescription = "Toggle View"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    titleContentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Count Bar
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Active Alerts: ${activeAlerts.size}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )

                        SingleChoiceSegmentedButtonRow {
                            SegmentedButton(
                                selected = (currentViewMode == AdminViewMode.LIST),
                                onClick = { currentViewMode = AdminViewMode.LIST },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                            ) {
                                Icon(Icons.Default.List, contentDescription = "List View", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("List", fontSize = 12.sp)
                            }
                            SegmentedButton(
                                selected = (currentViewMode == AdminViewMode.MAP),
                                onClick = { currentViewMode = AdminViewMode.MAP },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                            ) {
                                Icon(Icons.Default.Map, contentDescription = "Map View", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Map", fontSize = 12.sp)
                            }
                        }
                    }
                }

                if (currentViewMode == AdminViewMode.MAP) {
                    AdminMultiMapView(
                        alerts = activeAlerts,
                        onSelectAlert = { alert -> selectedAlertForDetail = alert }
                    )
                } else {
                    if (activeAlerts.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "No Active Alerts",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "All Quiet in Security Room",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "No active emergency SOS alerts reported.",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(onClick = onSignOut) {
                                    Text("Sign Out")
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(activeAlerts, key = { it.alertId }) { alert ->
                                AdminAlertCard(
                                    alert = alert,
                                    onCardClick = { selectedAlertForDetail = alert },
                                    onAcknowledge = { adminViewModel.acknowledgeAlert(alert.alertId, user.uid) },
                                    onResolve = { adminViewModel.resolveAlert(alert.alertId) }
                                )
                            }
                        }
                    }
                }
            }

            selectedAlertForDetail?.let { alert ->
                AdminAlertDetailDialog(
                    alert = alert,
                    adminUid = user.uid,
                    onDismiss = { selectedAlertForDetail = null },
                    onAcknowledge = {
                        adminViewModel.acknowledgeAlert(alert.alertId, user.uid)
                        selectedAlertForDetail = null
                    },
                    onResolve = {
                        adminViewModel.resolveAlert(alert.alertId)
                        selectedAlertForDetail = null
                    }
                )
            }
        }
    }
}

@Composable
fun AdminAlertCard(
    alert: SosAlert,
    onCardClick: () -> Unit,
    onAcknowledge: () -> Unit,
    onResolve: () -> Unit
) {
    val dateFormatter = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
    val timeStr = dateFormatter.format(Date(alert.timestamp))

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Alert Icon",
                        tint = if (alert.status == SosAlert.STATUS_ACKNOWLEDGED) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = alert.userName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    color = if (alert.status == SosAlert.STATUS_ACKNOWLEDGED) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = alert.status.uppercase(Locale.getDefault()),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (alert.status == SosAlert.STATUS_ACKNOWLEDGED) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(text = "Phone: ${alert.userPhone}", fontSize = 14.sp)
            Text(text = "Triggered at: $timeStr", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = "Location: Lat ${String.format("%.4f", alert.lat)}, Lng ${String.format("%.4f", alert.lng)}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (alert.status == SosAlert.STATUS_ACTIVE) {
                    OutlinedButton(
                        onClick = onAcknowledge,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Acknowledge", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Acknowledge")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Button(
                    onClick = onResolve,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Resolve", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Resolve")
                }
            }
        }
    }
}
