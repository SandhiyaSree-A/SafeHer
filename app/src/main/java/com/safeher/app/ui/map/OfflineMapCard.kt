package com.safeher.app.ui.map

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun OfflineMapCard(modifier: Modifier = Modifier) {
    Card(modifier = modifier.padding(16.dp)) {
        Text(
            text = "Offline maps are not supported with Google Maps. You need an internet connection to view the map and get routes.",
            modifier = Modifier.padding(16.dp)
        )
    }
}
