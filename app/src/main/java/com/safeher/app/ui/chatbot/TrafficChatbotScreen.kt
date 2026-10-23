package com.safeher.app.ui.chatbot

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safeher.app.R
import com.safeher.app.data.model.ChatMessage
import com.safeher.app.data.model.HelplineItem
import com.safeher.app.data.model.TrafficRuleItem

// ==========================================
// Soft Pale Pink & Navy Blue Theme Palette
// ==========================================
private val PalePinkBg = Color(0xFFFFF2F5)          // Soft pale pink background
private val PalePinkCard = Color(0xFFFFFFFF)        // Clean white card surface
private val PalePinkSubCard = Color(0xFFFDE8ED)     // Soft pink interior container
private val PalePinkHeader = Color(0xFFFFEBF0)      // Elegant header pale pink
private val NavyBlueText = Color(0xFF1E293B)        // High contrast readable dark slate/navy
private val NavyBlueTitle = Color(0xFF0F172A)       // Bold navy title
private val RosePinkAccent = Color(0xFFFF4B6E)      // SafeHer vibrant rose pink
private val SoftPinkBorder = Color(0xFFF5CAD3)      // Soft pastel outline border
private val OnlineGreen = Color(0xFF10B981)         // Status green
private val OfflineOrange = Color(0xFFF59E0B)       // Status orange

/**
 * Floating Chatbot with Mascot Avatar Button and Side-Popup Panel
 */
@Composable
fun FloatingTryHackMeChatbot(
    viewModel: ChatbotViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    var isOpen by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomEnd
    ) {
        // Backdrop overlay (click to close)
        AnimatedVisibility(
            visible = isOpen,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f))
                    .clickable { isOpen = false }
            )
        }

        // Side-Popup Window
        AnimatedVisibility(
            visible = isOpen,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(200)) + fadeOut(),
            modifier = Modifier
                .padding(bottom = 88.dp, end = 16.dp, start = 16.dp)
                .align(Alignment.BottomEnd)
        ) {
            SafeHerChatPanel(
                viewModel = viewModel,
                onClose = { isOpen = false }
            )
        }

        // Mascot Floating Trigger Button (Bottom-Right)
        if (!isOpen) {
            MascotFloatingTrigger(
                onClick = { isOpen = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 20.dp)
            )
        }
    }
}

/**
 * Walking Mascot Floating Button with continuous left-to-right walking loop,
 * natural bobbing cadence, smooth directional flipping, and interactive tap/hover.
 *
 * Note on illustration: The provided asset is a single static PNG illustration.
 * Individual limb rigging (skeletal deformation or multi-frame sprite sheets) requires
 * separate body-part layers. To deliver the most natural and realistic walking locomotion
 * possible with the single asset, we compose:
 * 1. Horizontal pacing across a dedicated small walking track
 * 2. Natural vertical stepping bob (sinusoidal bounce matching gait cadence)
 * 3. Forward-motion stride lean (gentle rhythmic tilt back and forth)
 * 4. Smooth horizontal flip (scaleX) on turnaround so she faces the direction she walks
 * 5. Dynamic grounding shadow that compresses and expands with her stride
 */
@Composable
fun MascotFloatingTrigger(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 1. Horizontal walking pacing (0f = far left, 1f = far right)
    val walkingTransition = rememberInfiniteTransition(label = "mascotWalkingCycle")

    // Pacing left-to-right and back
    val walkingProgress by walkingTransition.animateFloat(
        initialValue = -18f,
        targetValue = 18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pacingProgress"
    )

    // Direction facing based on derivative/direction of movement:
    // When progress is moving positive, face right (scaleX = 1f); when reversing, face left (scaleX = -1f)
    var previousX by remember { mutableFloatStateOf(0f) }
    var isFacingRight by remember { mutableStateOf(true) }

    LaunchedEffect(walkingProgress) {
        if (walkingProgress > previousX + 0.1f) {
            isFacingRight = true
        } else if (walkingProgress < previousX - 0.1f) {
            isFacingRight = false
        }
        previousX = walkingProgress
    }

    val animatedScaleX by animateFloatAsState(
        targetValue = if (isFacingRight) 1f else -1f,
        animationSpec = tween(durationMillis = 180, easing = EaseInOutQuad),
        label = "turnDirection"
    )

    // 2. Vertical stepping bob (rhythmic stride bounce - twice the frequency of walking stride)
    val steppingBob by walkingTransition.animateFloat(
        initialValue = 0f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 350, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "steppingBob"
    )

    // 3. Natural stride tilt (gentle forward and backward swing matching stride steps)
    val strideTilt by walkingTransition.animateFloat(
        initialValue = -3.5f,
        targetValue = 3.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 350, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "strideTilt"
    )

    // Track container: Keeps mascot bounded in a neat, fixed area
    Box(
        modifier = modifier
            .width(110.dp)
            .height(78.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Grounding shadow that pulses with her step
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(x = walkingProgress.dp, y = (-2).dp)
                .width((38 + (steppingBob * 1.2f)).dp)
                .height(6.dp)
                .clip(CircleShape)
                .background(Color(0xFFE5B8C2).copy(alpha = 0.55f))
        )

        // Walking Mascot Avatar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(x = walkingProgress.dp, y = steppingBob.dp)
                .graphicsLayer(
                    scaleX = animatedScaleX,
                    rotationZ = if (isFacingRight) strideTilt else -strideTilt
                )
                .clickable { onClick() }
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .border(
                        2.5.dp,
                        Brush.linearGradient(listOf(RosePinkAccent, Color(0xFFD946EF))),
                        CircleShape
                    )
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_mascot),
                    contentDescription = "Confident Walking Girl Assistant Mascot",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Online green indicator dot
            Box(
                modifier = Modifier
                    .size(13.dp)
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .background(OnlineGreen)
                    .border(2.dp, PalePinkBg, CircleShape)
            )
        }
    }
}

/**
 * SafeHer Pale Pink Chatbot Panel Window
 */
@Composable
fun SafeHerChatPanel(
    viewModel: ChatbotViewModel,
    onClose: () -> Unit
) {
    val messages by viewModel.messages.collectAsState()
    val isTyping by viewModel.isTyping.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(messages.size, isTyping) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = PalePinkBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, SoftPinkBorder),
        shadowElevation = 18.dp,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 460.dp, max = 560.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PalePinkHeader)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, RosePinkAccent, CircleShape)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_mascot),
                            contentDescription = "Assistant Mascot",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Column {
                        Text(
                            text = "SHEVA",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = NavyBlueTitle
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "🟢 Online",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF047857)
                            )
                        }
                        Text(
                            text = "Every Road, Her Right.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = RosePinkAccent
                        )
                    }
                }

                // Close Button
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = NavyBlueTitle,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            HorizontalDivider(color = SoftPinkBorder, thickness = 1.dp)

            // Message Body
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 10.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    AnimatedMessageItem(message = message) { selectedReply ->
                        viewModel.sendMessage(selectedReply)
                    }
                }

                if (isTyping) {
                    item(key = "typing_indicator") {
                        TypingIndicatorBubble()
                    }
                }
            }

            HorizontalDivider(color = SoftPinkBorder, thickness = 1.dp)

            // Input Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            "Type your traffic or safety question...",
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RosePinkAccent,
                        unfocusedBorderColor = SoftPinkBorder,
                        focusedContainerColor = Color(0xFFFFF9FA),
                        unfocusedContainerColor = Color(0xFFFFF9FA),
                        focusedTextColor = NavyBlueTitle,
                        unfocusedTextColor = NavyBlueTitle
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputText.isNotBlank()) {
                                val textToSend = inputText
                                inputText = ""
                                viewModel.sendMessage(textToSend)
                                keyboardController?.hide()
                            }
                        }
                    )
                )

                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            val textToSend = inputText
                            inputText = ""
                            viewModel.sendMessage(textToSend)
                            keyboardController?.hide()
                        }
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(RosePinkAccent),
                    enabled = inputText.isNotBlank() && !isTyping
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Hero Card with Mascot Illustration on Welcome Screen
 */
@Composable
fun MascotWelcomeHero() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, SoftPinkBorder),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_mascot),
                contentDescription = "Confident Girl Mascot",
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Confident & Safe On Roads",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = NavyBlueTitle
                )
                Text(
                    text = "I'm here to assist with verified traffic rules, fines, and road emergency protocols.",
                    fontSize = 11.sp,
                    color = Color.DarkGray,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
fun SmartSuggestionChipsRow(
    onSuggestionClick: (String) -> Unit
) {
    val suggestions = listOf(
        "🚦 Explain Traffic Signs",
        "🪖 Helmet Rules",
        "🚑 Road Accident Help"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        suggestions.forEach { chipText ->
            Surface(
                onClick = { onSuggestionClick(chipText) },
                shape = RoundedCornerShape(16.dp),
                color = PalePinkSubCard,
                border = androidx.compose.foundation.BorderStroke(1.dp, SoftPinkBorder),
                shadowElevation = 1.dp
            ) {
                Text(
                    text = chipText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NavyBlueTitle,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun AnimatedMessageItem(
    message: ChatMessage,
    onQuickReplyClick: (String) -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(250)) + slideInVertically(
            initialOffsetY = { 30 },
            animationSpec = tween(250, easing = EaseOutQuad)
        )
    ) {
        if (message.isUser) {
            UserMessageBubble(message = message)
        } else {
            BotMessageCard(
                message = message,
                onQuickReplyClick = onQuickReplyClick
            )
        }
    }
}

@Composable
fun UserMessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            color = RosePinkAccent,
            shadowElevation = 2.dp,
            modifier = Modifier.widthIn(max = 270.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = message.text,
                    fontSize = 13.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = message.timestamp,
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.align(Alignment.End).padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
fun BotMessageCard(
    message: ChatMessage,
    onQuickReplyClick: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            color = Color.White,
            border = androidx.compose.foundation.BorderStroke(1.dp, SoftPinkBorder),
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(0.96f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (message.text.isNotBlank()) {
                    Text(
                        text = message.text,
                        fontSize = 13.sp,
                        color = NavyBlueText,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 17.sp
                    )
                }

                message.trafficRule?.let { rule ->
                    Spacer(modifier = Modifier.height(8.dp))
                    TrafficRuleCard(rule = rule)
                }

                if (message.helplines.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    EmergencyHelplinesList(helplines = message.helplines)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Verified Official Database",
                        fontSize = 9.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = message.timestamp,
                        fontSize = 9.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun TrafficRuleCard(rule: TrafficRuleItem) {
    val context = LocalContext.current

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFFFF9FA),
        border = androidx.compose.foundation.BorderStroke(1.dp, SoftPinkBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(PalePinkSubCard),
                    contentAlignment = Alignment.Center
                ) {
                    Text(rule.iconEmoji, fontSize = 17.sp)
                }

                Text(
                    text = rule.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = NavyBlueTitle
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Regulation:",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = RosePinkAccent
            )
            Text(
                text = rule.rule,
                fontSize = 12.sp,
                color = NavyBlueText,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFFFF0F3),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, RosePinkAccent.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(7.dp)) {
                    Text(
                        text = "Penalty & Legal Consequence:",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = RosePinkAccent
                    )
                    Text(
                        text = rule.penalty,
                        fontSize = 11.sp,
                        color = NavyBlueTitle,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(rule.sourceUrl))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // Ignored
                        }
                    }
                    .padding(vertical = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = OnlineGreen,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Official Source: ${rule.sourceName}",
                    fontSize = 10.sp,
                    color = Color(0xFF2563EB),
                    textDecoration = TextDecoration.Underline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun EmergencyHelplinesList(helplines: List<HelplineItem>) {
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        helplines.forEach { hl ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, SoftPinkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = hl.name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NavyBlueTitle
                        )
                        if (hl.description.isNotBlank()) {
                            Text(
                                text = hl.description,
                                fontSize = 9.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${hl.number}"))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // Ignore
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RosePinkAccent),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Call",
                            modifier = Modifier.size(11.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = hl.number,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TypingIndicatorBubble() {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")

    val dot1Scale by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )

    val dot2Scale by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 150, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )

    val dot3Scale by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 300, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )

    Row(
        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color.White,
            border = androidx.compose.foundation.BorderStroke(1.dp, SoftPinkBorder)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = "Checking regulations",
                    fontSize = 10.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(2.dp))
                Box(
                    modifier = Modifier
                        .size((5 * dot1Scale).dp)
                        .clip(CircleShape)
                        .background(RosePinkAccent)
                )
                Box(
                    modifier = Modifier
                        .size((5 * dot2Scale).dp)
                        .clip(CircleShape)
                        .background(RosePinkAccent)
                )
                Box(
                    modifier = Modifier
                        .size((5 * dot3Scale).dp)
                        .clip(CircleShape)
                        .background(RosePinkAccent)
                )
            }
        }
    }
}

@Composable
fun TrafficChatbotScreen(
    onNavigateBack: (() -> Unit)? = null,
    viewModel: ChatbotViewModel = viewModel()
) {
    SafeHerChatPanel(
        viewModel = viewModel,
        onClose = { onNavigateBack?.invoke() }
    )
}
