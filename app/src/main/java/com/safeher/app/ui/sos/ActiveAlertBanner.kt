package com.safeher.app.ui.sos

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeher.app.data.model.SosAlert
import com.safeher.app.data.model.User

@Composable
fun ActiveAlertBanner(
    user: User,
    viewModel: SosViewModel
) {
    val context = LocalContext.current
    val activeAlert by viewModel.activeAlert.collectAsState()
    val isResolving by viewModel.isResolving.collectAsState()

    AnimatedVisibility(
        visible = activeAlert != null && activeAlert?.status != SosAlert.STATUS_RESOLVED,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        activeAlert?.let { alert ->
            val isAcknowledged = (alert.status == SosAlert.STATUS_ACKNOWLEDGED)
            val backgroundColor = if (isAcknowledged) Color(0xFFE65100) else Color(0xFFB71C1C)

            Surface(
                color = backgroundColor,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 10.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isAcknowledged) Icons.Default.Security else Icons.Default.Warning,
                            contentDescription = "Alert Status Icon",
                            tint = Color.Yellow,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isAcknowledged) "SECURITY ROOM ACKNOWLEDGED" else "EMERGENCY SOS ACTIVE",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (isAcknowledged)
                            "Security Room Admin acknowledged your SOS alert. Security response team has been dispatched."
                        else
                            "Emergency alerts & SMS sent to contacts. Help is on the way.",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.95f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            viewModel.resolveAlert(context, alert.alertId)
                        },
                        enabled = !isResolving,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = backgroundColor
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        if (isResolving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = backgroundColor
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "I'm Safe",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "I'M SAFE - CANCEL ALERT",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
