package com.safeher.app.ui.sos

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.safeher.app.data.model.User
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SosFloatingButton(
    user: User,
    viewModel: SosViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    var isHolding by remember { mutableStateOf(false) }
    var holdProgress by remember { mutableFloatStateOf(0f) }
    var showSmsRationaleDialog by remember { mutableStateOf(false) }

    fun checkSmsPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val smsGranted = permissions[Manifest.permission.SEND_SMS] ?: false
        if (smsGranted) {
            viewModel.triggerSos(context, user)
        }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = holdProgress,
        animationSpec = tween(durationMillis = 100),
        label = "SosHoldProgress"
    )

    fun startSosTrigger() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (checkSmsPermissionGranted()) {
            viewModel.triggerSos(context, user)
        } else {
            showSmsRationaleDialog = true
        }
    }

    if (showSmsRationaleDialog) {
        AlertDialog(
            onDismissRequest = { showSmsRationaleDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = "SMS Permission") },
            title = { Text("SMS Permission Needed") },
            text = {
                Text("SafeHer requires permission to send emergency SMS alerts to your saved emergency contacts during an SOS trigger.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSmsRationaleDialog = false
                        val perms = mutableListOf(Manifest.permission.SEND_SMS)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            perms.add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        smsPermissionLauncher.launch(perms.toTypedArray())
                    }
                ) {
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSmsRationaleDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        // Outer Progress Ring during hold
        if (isHolding) {
            Canvas(modifier = Modifier.size(84.dp)) {
                drawArc(
                    color = Color.Red,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    style = Stroke(width = 6.dp.toPx())
                )
            }
        }

        Surface(
            shape = CircleShape,
            color = if (isHolding) Color(0xFFD32F2F) else Color(0xFFB71C1C),
            tonalElevation = 12.dp,
            shadowElevation = 12.dp,
            modifier = Modifier
                .size(72.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isHolding = true
                            holdProgress = 0f
                            val totalTimeMs = 3000L
                            val stepMs = 50L
                            var elapsed = 0L

                            val job = coroutineScope.launch {
                                while (elapsed < totalTimeMs && isHolding) {
                                    delay(stepMs)
                                    elapsed += stepMs
                                    holdProgress = (elapsed.toFloat() / totalTimeMs.toFloat()).coerceIn(0f, 1f)
                                }
                                if (elapsed >= totalTimeMs && isHolding) {
                                    startSosTrigger()
                                    isHolding = false
                                    holdProgress = 0f
                                }
                            }

                            tryAwaitRelease()
                            isHolding = false
                            holdProgress = 0f
                            job.cancel()
                        }
                    )
                }
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "SOS",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = if (isHolding) "HOLD" else "3s HOLD",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
