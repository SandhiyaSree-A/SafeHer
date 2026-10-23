package com.safeher.app.ui.profile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class HelplineItem(
    val title: String,
    val number: String,
    val description: String,
    val icon: ImageVector,
    val isPrimarySos: Boolean = false
)

data class HelplineCategory(
    val categoryName: String,
    val items: List<HelplineItem>
)

@Composable
fun EmergencyHelplineDirectorySection() {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var isExpanded by remember { mutableStateOf(true) }
    var itemToCall by remember { mutableStateOf<HelplineItem?>(null) }

    val helplineCategories = remember {
        listOf(
            HelplineCategory(
                categoryName = "1. Emergency Helplines — India 🇮🇳",
                items = listOf(
                    HelplineItem(
                        title = "National Emergency (Police, Fire, Ambulance)",
                        number = "112",
                        description = "Immediate emergency response support service (ERSS)",
                        icon = Icons.Default.Emergency,
                        isPrimarySos = true
                    ),
                    HelplineItem(
                        title = "Police",
                        number = "100",
                        description = "Immediate police assistance",
                        icon = Icons.Default.LocalPolice
                    ),
                    HelplineItem(
                        title = "Ambulance",
                        number = "102",
                        description = "Medical & ambulance emergency",
                        icon = Icons.Default.LocalHospital
                    ),
                    HelplineItem(
                        title = "Fire and Rescue",
                        number = "101",
                        description = "Fire safety & rescue operations",
                        icon = Icons.Default.LocalFireDepartment
                    ),
                    HelplineItem(
                        title = "Women Helpline",
                        number = "181",
                        description = "24/7 all-India women helpline & support",
                        icon = Icons.Default.SupportAgent
                    ),
                    HelplineItem(
                        title = "Women in Distress",
                        number = "1091",
                        description = "Emergency response for women facing distress",
                        icon = Icons.Default.Shield
                    ),
                    HelplineItem(
                        title = "Child Helpline",
                        number = "1098",
                        description = "24-hour emergency phone outreach for children",
                        icon = Icons.Default.ChildCare
                    ),
                    HelplineItem(
                        title = "Road Accident Assistance",
                        number = "1073",
                        description = "Assistance for road accident emergencies",
                        icon = Icons.Default.CarCrash
                    ),
                    HelplineItem(
                        title = "National Highways Assistance",
                        number = "1033",
                        description = "Emergency assistance on national highways (NHAI)",
                        icon = Icons.Default.AltRoute
                    )
                )
            ),
            HelplineCategory(
                categoryName = "2. Additional Useful Helplines",
                items = listOf(
                    HelplineItem(
                        title = "Senior Citizens Helpline",
                        number = "14567",
                        description = "Elder line assistance and support services",
                        icon = Icons.Default.Elderly
                    ),
                    HelplineItem(
                        title = "National Consumer Helpline",
                        number = "1915",
                        description = "Consumer grievances and unfair trade practices",
                        icon = Icons.Default.Storefront
                    ),
                    HelplineItem(
                        title = "Tourist Helpline",
                        number = "1363",
                        description = "Multi-lingual tourist info & safety guidance",
                        icon = Icons.Default.Explore
                    ),
                    HelplineItem(
                        title = "National Commission for Women",
                        number = "7827170170",
                        description = "NCW 24x7 women helpline",
                        icon = Icons.Default.Female
                    )
                )
            ),
            HelplineCategory(
                categoryName = "3. Tamil Nadu / Chennai Contacts",
                items = listOf(
                    HelplineItem(
                        title = "Chennai Traffic Police",
                        number = "103",
                        description = "Traffic safety & road regulation helpline",
                        icon = Icons.Default.Traffic
                    ),
                    HelplineItem(
                        title = "Women Helpline",
                        number = "181",
                        description = "Tamil Nadu State Women Helpline",
                        icon = Icons.Default.SupportAgent
                    ),
                    HelplineItem(
                        title = "Child Helpline",
                        number = "1098",
                        description = "Child line emergency support",
                        icon = Icons.Default.ChildCare
                    )
                )
            )
        )
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneInTalk,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Emergency Helpline Directory",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Official National & Regional Helplines 🇮🇳",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand"
                    )
                }
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(14.dp))

                helplineCategories.forEach { category ->
                    Text(
                        text = category.categoryName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 10.dp, bottom = 8.dp)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        category.items.forEach { item ->
                            HelplineItemCard(
                                item = item,
                                onCallClick = { itemToCall = item },
                                onCopyClick = {
                                    clipboardManager.setText(AnnotatedString(item.number))
                                    Toast.makeText(context, "${item.title} number (${item.number}) copied!", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Confirmation Dialog before placing a phone call
    itemToCall?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToCall = null },
            icon = {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = if (item.isPrimarySos) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Call ${item.title}?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "Are you sure you want to dial ${item.number}?",
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val number = item.number
                        itemToCall = null
                        dialNumber(context, number)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (item.isPrimarySos) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Call ${item.number}")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToCall = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun HelplineItemCard(
    item: HelplineItem,
    onCallClick: () -> Unit,
    onCopyClick: () -> Unit
) {
    val cardBg = if (item.isPrimarySos) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (item.isPrimarySos) MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = if (item.isPrimarySos) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = item.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = item.number,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (item.isPrimarySos) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = item.description,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 14.sp
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onCopyClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy number",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
                FilledTonalIconButton(
                    onClick = onCallClick,
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (item.isPrimarySos) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (item.isPrimarySos) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Call",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun dialNumber(context: Context, number: String) {
    try {
        val cleanNumber = number.replace(Regex("[^0-9+]"), "")
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$cleanNumber")
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "Unable to initiate call for $number", Toast.LENGTH_SHORT).show()
    }
}
