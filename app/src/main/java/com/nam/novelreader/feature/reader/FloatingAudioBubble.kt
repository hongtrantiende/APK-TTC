package com.nam.novelreader.feature.reader

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
fun FloatingAudioBubble(
    novelCover: String?,
    isPlaying: Boolean,
    themeIndex: Int,
    onClick: () -> Unit
) {
    val (bgColor, textColor, primaryColor, cardColor) = when (themeIndex) {
        0 -> Quadruple(Color(0xFFD3C3A3), Color(0xFF3A3129), Color(0xFF8B5A2B), Color(0xFFC5B595)) // Kraft hoa văn (bg7.jpg)
        1 -> Quadruple(Color(0xFFFFFFFF), Color(0xFF3A342B), Color(0xFF1976D2), Color(0xFFF0F0F0)) // Trắng trơn
        2 -> Quadruple(Color(0xFFF1F7ED), Color(0xFF1B310E), Color(0xFF2E7D32), Color(0xFFE2EADF)) // Xanh lá hoa văn (bg6.png)
        3 -> Quadruple(Color(0xFFA2C0E5), Color(0xFF1B310E), Color(0xFF1976D2), Color(0xFF90B0D5)) // Xanh dương hoa văn (bg4.jpg)
        4 -> Quadruple(Color(0xFF1C1C1C), Color(0xFFCCCCCC), Color(0xFFD4A574), Color(0xFF282828)) // Đêm đen
        5 -> Quadruple(Color(0xFFF3C9D7), Color(0xFF1B310E), Color(0xFFC2185B), Color(0xFFE5B9C7)) // Hồng hoa văn (bg5.jpg)
        6 -> Quadruple(Color(0xFFECE1CA), Color(0xFF645032), Color(0xFF8B5A2B), Color(0xFFDDD2BA)) // Vàng giấy trơn
        7 -> Quadruple(Color(0xFFC2E0CD), Color(0xFF334B39), Color(0xFF2E7D32), Color(0xFFB2D0BD)) // Lục nhạt trơn
        else -> Quadruple(Color(0xFFD3C3A3), Color(0xFF3A3129), Color(0xFF8B5A2B), Color(0xFFC5B595))
    }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    val screenWidthDp = configuration.screenWidthDp.toFloat()
    val screenHeightDp = configuration.screenHeightDp.toFloat()

    // Animatable offsets for smooth drag and spring-snapping
    val offsetX = remember { Animatable(screenWidthDp - 76f) }
    val offsetY = remember { Animatable(screenHeightDp / 2f) }

    // Disk rotation transition when playing
    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    val rotationAngle by if (isPlaying) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(8000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "angle"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    Box(
        modifier = Modifier
            .offset(x = offsetX.value.dp, y = offsetY.value.dp)
            .size(56.dp)
            .shadow(10.dp, CircleShape)
            .background(cardColor, CircleShape)
            .border(2.dp, primaryColor, CircleShape)
            .pointerInput(screenWidthDp, screenHeightDp) {
                detectDragGestures(
                    onDragEnd = {
                        // Snap to nearest edge (left = 16dp, right = screenWidthDp - 72dp)
                        val center = screenWidthDp / 2f
                        val targetX = if (offsetX.value + 28f < center) 16f else (screenWidthDp - 72f)
                        coroutineScope.launch {
                            offsetX.animateTo(
                                targetValue = targetX,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            )
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val dragAmountDpX = with(density) { dragAmount.x.toDp().value }
                        val dragAmountDpY = with(density) { dragAmount.y.toDp().value }
                        coroutineScope.launch {
                            offsetX.snapTo((offsetX.value + dragAmountDpX).coerceIn(8f, screenWidthDp - 64f))
                            offsetY.snapTo((offsetY.value + dragAmountDpY).coerceIn(40f, screenHeightDp - 96f))
                        }
                    }
                )
            }
            .clickable { onClick() }
    ) {
        // Cover Art or Fallback Icon
        if (!novelCover.isNullOrEmpty()) {
            AsyncImage(
                model = novelCover,
                contentDescription = "Cover",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .rotate(rotationAngle),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Headphones,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Small Play/Pause State Indicator Badge
        Box(
            modifier = Modifier
                .size(18.dp)
                .align(Alignment.BottomEnd)
                .shadow(2.dp, CircleShape)
                .background(primaryColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = bgColor,
                modifier = Modifier.size(10.dp)
            )
        }
    }
}
