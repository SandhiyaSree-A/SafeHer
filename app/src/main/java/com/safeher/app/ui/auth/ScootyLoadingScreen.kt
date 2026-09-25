package com.safeher.app.ui.auth

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeher.app.R

@Composable
fun ScootyLoadingScreen(
    modifier: Modifier = Modifier,
    message: String = "Travelling safely with SafeHer..."
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scooty_travel_infinite")

    // 1. Main Left-to-Right Continuous Travel Motion (-1.15f to +1.15f screen width ratio)
    val travelProgress by infiniteTransition.animateFloat(
        initialValue = -1.15f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "travelProgress"
    )

    // 2. Scooter Road Vibration (slight subtle vertical vibration)
    val scooterBounceY by infiniteTransition.animateFloat(
        initialValue = -2.5f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 400, easing = FastOutLinearInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scooterBounceY"
    )

    // 3. Independent Rider Riding Bobbing (lady seat bounce relative to scooter)
    val riderBobY by infiniteTransition.animateFloat(
        initialValue = -5.5f,
        targetValue = 4.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "riderBobY"
    )

    // 4. Independent Rider Riding Posture Sway / Tilt
    val riderTiltAngle by infiniteTransition.animateFloat(
        initialValue = -2.0f,
        targetValue = 2.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "riderTiltAngle"
    )

    // 5. Shawl / Dupatta Wind Wave Flutter (scale pulse towards the back/left)
    val dupattaWindScaleX by infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dupattaWindScaleX"
    )

    // 6. Road Dash Motion Effect (right to left speed lines)
    val roadDashOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 400f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "roadDashOffset"
    )

    // 7. Loading text opacity pulse
    val textAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "textAlpha"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        val screenWidthPx = constraints.maxWidth.toFloat()
        val currentTravelX = travelProgress * (screenWidthPx / 2f)

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Road speed lines at bottom
            val roadLineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
            ) {
                val width = size.width
                val dashWidth = 50f
                val gap = 35f
                val step = dashWidth + gap
                var startX = -roadDashOffset % step

                while (startX < width + step) {
                    drawLine(
                        color = roadLineColor,
                        start = Offset(startX, size.height / 2),
                        end = Offset(startX + dashWidth, size.height / 2),
                        strokeWidth = 7f
                    )
                    startX += step
                }
            }

            // Layered Riding Animation Container (Scooter Layer + Rider Layer moving left -> right)
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.80f)
                    .aspectRatio(1f)
                    .graphicsLayer {
                        translationX = currentTravelX
                    },
                contentAlignment = Alignment.Center
            ) {
                // Layer 1: Scooter Base Layer (moving left -> right with slight road vibration)
                Image(
                    painter = painterResource(id = R.drawable.ic_scooty_scooter),
                    contentDescription = "SafeHer Scooter Layer",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationY = scooterBounceY
                        },
                    contentScale = ContentScale.Fit
                )

                // Layer 2: Rider Layer (Lady, Hair, Dupatta independently bobbing & flowing with wind)
                Image(
                    painter = painterResource(id = R.drawable.ic_scooty_rider),
                    contentDescription = "SafeHer Lady Rider Layer",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationY = scooterBounceY + riderBobY
                            rotationZ = riderTiltAngle
                            scaleX = dupattaWindScaleX
                        },
                    contentScale = ContentScale.Fit
                )
            }

            // Loading text banner at bottom
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 60.dp)
            ) {
                Text(
                    text = message,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = textAlpha)
                )
            }
        }
    }
}
