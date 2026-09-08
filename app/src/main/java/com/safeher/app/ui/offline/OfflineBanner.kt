package com.safeher.app.ui.offline

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.safeher.app.data.offline.ConnectivityRepository

@Composable
fun OfflineBanner() {
    val context = LocalContext.current
    val isOnline by ConnectivityRepository.get(context).isOnline.collectAsState()
    if (!isOnline) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Icon(Icons.Default.CloudOff, contentDescription = "Offline")
                Text(
                    "Offline: location and journey data will sync when internet returns.",
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}