package com.safeher.app.ui.map

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.geometry.LatLngBounds

/**
 * Self-contained card that lets the user download the Chennai region for offline use.
 * Uses MapLibre OfflineManager – no modifications needed in calling screens.
 */
@SuppressLint("MissingPermission")
@Composable
fun OfflineMapCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var statusMessage by remember { mutableStateOf("") }
    var isDownloaded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isDownloaded) Icons.Default.CheckCircle else Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = if (isDownloaded) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Offline Map",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Download Chennai region for offline navigation",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                    )
                }
            }

            if (isDownloading) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = statusMessage.ifBlank { "Downloading… ${(downloadProgress * 100).toInt()}%" },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            if (statusMessage.isNotBlank() && !isDownloading) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = statusMessage,
                    fontSize = 12.sp,
                    color = if (isDownloaded) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    scope.launch {
                        isDownloading = true
                        statusMessage = ""
                        downloadProgress = 0f
                        try {
                            withContext(Dispatchers.Main) {
                                // Ensure MapLibre is initialised (idempotent)
                                MapLibre.getInstance(context)
                                val offlineManager = OfflineManager.getInstance(context)

                                // Chennai bounding box
                                val bounds = LatLngBounds.from(13.25, 80.40, 12.90, 80.10)
                                val definition = OfflineTilePyramidRegionDefinition(
                                    MapConfig.styleUrl,
                                    bounds,
                                    10.0,
                                    16.0,
                                    context.resources.displayMetrics.density
                                )
                                val metadata = "{}".toByteArray()

                                offlineManager.createOfflineRegion(
                                    definition,
                                    metadata,
                                    object : OfflineManager.CreateOfflineRegionCallback {
                                        override fun onCreate(region: OfflineRegion) {
                                            region.setDownloadState(OfflineRegion.STATE_ACTIVE)
                                            region.setObserver(object : OfflineRegion.OfflineRegionObserver {
                                                override fun onStatusChanged(status: OfflineRegionStatus) {
                                                    val total = status.requiredResourceCount.coerceAtLeast(1)
                                                    downloadProgress = status.completedResourceCount.toFloat() / total
                                                    if (status.isComplete) {
                                                        isDownloading = false
                                                        isDownloaded = true
                                                        statusMessage = "✓ Offline map ready"
                                                    }
                                                }
                                                override fun onError(error: OfflineRegionError) {
                                                    isDownloading = false
                                                    statusMessage = "Download error: ${error.message}"
                                                }
                                                override fun mapboxTileCountLimitExceeded(limit: Long) {
                                                    isDownloading = false
                                                    statusMessage = "Tile limit exceeded ($limit)"
                                                }
                                            })
                                        }
                                        override fun onError(error: String) {
                                            isDownloading = false
                                            statusMessage = "Error: $error"
                                        }
                                    }
                                )
                            }
                        } catch (e: Exception) {
                            isDownloading = false
                            statusMessage = "Error: ${e.localizedMessage}"
                        }
                    }
                },
                enabled = !isDownloading && !isDownloaded,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isDownloaded) "Downloaded" else "Download Offline Map")
            }
        }
    }
}
