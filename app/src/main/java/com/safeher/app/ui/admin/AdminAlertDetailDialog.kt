package com.safeher.app.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.safeher.app.data.model.SosAlert
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AdminAlertDetailDialog(
    alert: SosAlert,
    adminUid: String,
    onDismiss: () -> Unit,
    onAcknowledge: () -> Unit,
    onResolve: () -> Unit
) {
    val alertLatLng = LatLng(alert.lat, alert.lng)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(alertLatLng, 16f)
    }

    val dateFormatter = SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", Locale.getDefault())
    val formattedTime = dateFormatter.format(Date(alert.timestamp))

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "Emergency Alert Detail",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(text = "User: ${alert.userName}", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(text = "Phone: ${alert.userPhone}", fontSize = 14.sp)
                Text(text = "Triggered: $formattedTime", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = "Status: ${alert.status.uppercase(Locale.getDefault())}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (alert.status == SosAlert.STATUS_ACKNOWLEDGED) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Embedded Google Map centered on alert location
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        uiSettings = MapUiSettings(zoomControlsEnabled = true)
                    ) {
                        Marker(
                            state = MarkerState(position = alertLatLng),
                            title = alert.userName,
                            snippet = "Lat: ${String.format("%.4f", alert.lat)}, Lng: ${String.format("%.4f", alert.lng)}"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (alert.status == SosAlert.STATUS_ACTIVE) {
                        Button(
                            onClick = onAcknowledge,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Acknowledge")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Button(
                        onClick = onResolve,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Resolve Alert")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close")
                }
            }
        }
    }
}
