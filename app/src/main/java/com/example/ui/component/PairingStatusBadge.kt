package com.example.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Real-time status badge showing current pairing state (Bağlı / Beklemede)
 * with a glowing live animated indicator dot.
 */
@Composable
fun PairingStatusBadge(
    isMatched: Boolean,
    partnerName: String = "",
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "StatusDotPulse")
    val dotScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "DotScale"
    )

    val activeColor = if (isMatched) Color(0xFF10B981) else Color(0xFFF59E0B) // Emerald Green or Warm Amber
    val containerColor = if (isMatched) {
        Color(0xFF10B981).copy(alpha = 0.12f)
    } else {
        Color(0xFFF59E0B).copy(alpha = 0.14f)
    }
    val borderColor = if (isMatched) {
        Color(0xFF10B981).copy(alpha = 0.35f)
    } else {
        Color(0xFFF59E0B).copy(alpha = 0.35f)
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        modifier = modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(20.dp))
            .testTag(if (isMatched) "pairing_status_matched" else "pairing_status_waiting")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Live pulsing status dot
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .scale(dotScale)
                            .clip(CircleShape)
                            .background(activeColor.copy(alpha = 0.3f))
                    )
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(activeColor)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isMatched) "DURUM: BAĞLI" else "DURUM: BEKLEMEDE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            ),
                            color = activeColor
                        )
                    }
                    Text(
                        text = if (isMatched) {
                            "$partnerName ile gerçek zamanlı kalp bağı aktif ❤️"
                        } else {
                            "Partnerinizin bağlanması bekleniyor..."
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
            }

            // Status Icon on the right
            Icon(
                imageVector = if (isMatched) Icons.Default.Favorite else Icons.Default.HourglassTop,
                contentDescription = null,
                tint = activeColor,
                modifier = Modifier
                    .size(20.dp)
                    .padding(start = 6.dp)
            )
        }
    }
}
