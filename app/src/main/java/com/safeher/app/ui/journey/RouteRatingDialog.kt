package com.safeher.app.ui.journey

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private enum class RatingStep {
    INITIAL_QUESTION, // Safe or not (Yes / No)
    UNSAFE_REASONS,   // Reasons selection + Other text input
    THANKS            // "Thanks for the rating"
}

@Composable
fun RouteRatingDialog(
    destinationName: String,
    routeName: String,
    feedbackSummary: String? = null,
    onSafeClicked: () -> Unit,
    onUnsafeSubmit: (selectedReasons: List<String>, otherReasonText: String) -> Unit,
    onDismiss: () -> Unit
) {
    var currentStep by remember { mutableStateOf(RatingStep.INITIAL_QUESTION) }

    val predefinedReasons = remember {
        listOf(
            "Poor street lighting / Dark spots",
            "Deserted area / Isolated road",
            "Felt unsafe / Harassment",
            "Suspicious activity / People gathering",
            "No police or security patrol nearby",
            "Rough road or dangerous pathway"
        )
    }

    val selectedReasons = remember { mutableStateListOf<String>() }
    var isOtherSelected by remember { mutableStateOf(false) }
    var otherReasonText by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = {
            if (currentStep == RatingStep.THANKS) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = (currentStep == RatingStep.THANKS),
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (currentStep) {
                    RatingStep.INITIAL_QUESTION -> {
                        // Header Icon
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE8F5E9)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Destination Reached! 🏁",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Navigation stopped at:\n${destinationName.ifBlank { "Your destination" }}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        if (routeName.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Text(
                                    text = "Route: $routeName",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = "Was this route safe for you?",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // YES & NO Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // YES BUTTON
                            Button(
                                onClick = {
                                    isSubmitting = true
                                    onSafeClicked()
                                    currentStep = RatingStep.THANKS
                                    isSubmitting = false
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2E7D32)
                                )
                            ) {
                                Icon(
                                    Icons.Default.ThumbUp,
                                    contentDescription = "Yes",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Yes, Safe",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color.White
                                )
                            }

                            // NO BUTTON
                            Button(
                                onClick = {
                                    currentStep = RatingStep.UNSAFE_REASONS
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFC62828)
                                )
                            ) {
                                Icon(
                                    Icons.Default.ThumbDown,
                                    contentDescription = "No",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "No, Unsafe",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    RatingStep.UNSAFE_REASONS -> {
                        // Header Icon
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFEBEE)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFC62828),
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "What made it unsafe?",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Choose reasons or add your own feedback to help protect other women:",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Scrollable reasons list
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Custom made reasons
                            predefinedReasons.forEach { reason ->
                                val isChecked = selectedReasons.contains(reason)
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isChecked) Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            if (isChecked) selectedReasons.remove(reason)
                                            else selectedReasons.add(reason)
                                        }
                                        .border(
                                            width = 1.dp,
                                            color = if (isChecked) Color(0xFFC62828) else Color.Transparent,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { checked ->
                                                if (checked) selectedReasons.add(reason)
                                                else selectedReasons.remove(reason)
                                            },
                                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFFC62828))
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = reason,
                                            fontSize = 13.sp,
                                            fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            // "Other" Option
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isOtherSelected) Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        isOtherSelected = !isOtherSelected
                                    }
                                    .border(
                                        width = 1.dp,
                                        color = if (isOtherSelected) Color(0xFFC62828) else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isOtherSelected,
                                        onCheckedChange = { checked ->
                                            isOtherSelected = checked
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFFC62828))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Other reason (type your own)",
                                        fontSize = 13.sp,
                                        fontWeight = if (isOtherSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            // Expandable text field for "Other"
                            AnimatedVisibility(
                                visible = isOtherSelected,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                OutlinedTextField(
                                    value = otherReasonText,
                                    onValueChange = { otherReasonText = it },
                                    placeholder = { Text("Specify your reason here...", fontSize = 12.sp) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    maxLines = 3,
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Action Buttons: Back & Submit
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { currentStep = RatingStep.INITIAL_QUESTION },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Back", fontSize = 13.sp)
                            }

                            val canSubmit = selectedReasons.isNotEmpty() || (isOtherSelected && otherReasonText.isNotBlank())
                            Button(
                                onClick = {
                                    val reasonsToSubmit = selectedReasons.toList()
                                    val otherToSubmit = if (isOtherSelected) otherReasonText.trim() else ""
                                    onUnsafeSubmit(reasonsToSubmit, otherToSubmit)
                                    currentStep = RatingStep.THANKS
                                },
                                enabled = canSubmit,
                                modifier = Modifier
                                    .weight(1.5f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFC62828)
                                )
                            ) {
                                Text("Submit Feedback", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    RatingStep.THANKS -> {
                        // Celebration Icon
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE8F5E9)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(44.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Thanks for the rating!",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = feedbackSummary ?: "Your response has been saved to the database. It helps SafeHer rank the safest routes and protect the community.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2E7D32)
                            )
                        ) {
                            Text("Done", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}
