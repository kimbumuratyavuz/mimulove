package com.example.ui.overlay

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.CoupleRepository
import com.example.service.CoupleNotificationHelper
import com.example.ui.theme.HeartColor
import com.example.ui.theme.MimuLoveAppTheme
import com.example.util.HeartSoundPlayer
import com.example.util.HeartVibrator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class HeartBurstOverlayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Allow showing over lock screen or active screen with translucent background
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        val coupleId = intent.getStringExtra(EXTRA_COUPLE_ID) ?: ""
        val myUserId = intent.getStringExtra(EXTRA_MY_USER_ID) ?: ""
        val myUserName = intent.getStringExtra(EXTRA_MY_USER_NAME) ?: "Aşkım"
        val senderName = intent.getStringExtra(EXTRA_SENDER_NAME) ?: "Sevdiğin"
        val noteText = intent.getStringExtra(EXTRA_NOTE_TEXT) ?: ""

        val prefs = getSharedPreferences("mimu_user_prefs", Context.MODE_PRIVATE)
        val heartColor = HeartColor.fromName(
            prefs.getString("selected_heart_color", HeartColor.RUBY_RED.name)
        )

        setContent {
            MimuLoveAppTheme {
                HeartBurstOverlayScreen(
                    coupleId = coupleId,
                    myUserId = myUserId,
                    myUserName = myUserName,
                    senderName = senderName,
                    noteText = noteText,
                    heartColor = heartColor,
                    onDismiss = {
                        CoupleNotificationHelper.dismissHeartAndNoteNotifications(this)
                        finish()
                        @Suppress("DEPRECATION")
                        overridePendingTransition(0, 0)
                    }
                )
            }
        }
    }

    companion object {
        private const val EXTRA_COUPLE_ID = "extra_couple_id"
        private const val EXTRA_MY_USER_ID = "extra_my_user_id"
        private const val EXTRA_MY_USER_NAME = "extra_my_user_name"
        private const val EXTRA_SENDER_NAME = "extra_sender_name"
        private const val EXTRA_NOTE_TEXT = "extra_note_text"

        fun createIntent(
            context: Context,
            coupleId: String,
            myUserId: String,
            myUserName: String,
            senderName: String,
            noteText: String
        ): Intent {
            return Intent(context, HeartBurstOverlayActivity::class.java).apply {
                putExtra(EXTRA_COUPLE_ID, coupleId)
                putExtra(EXTRA_MY_USER_ID, myUserId)
                putExtra(EXTRA_MY_USER_NAME, myUserName)
                putExtra(EXTRA_SENDER_NAME, senderName)
                putExtra(EXTRA_NOTE_TEXT, noteText)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
            }
        }

        fun launch(
            context: Context,
            coupleId: String,
            myUserId: String,
            myUserName: String,
            senderName: String,
            noteText: String
        ) {
            try {
                val intent = createIntent(
                    context = context,
                    coupleId = coupleId,
                    myUserId = myUserId,
                    myUserName = myUserName,
                    senderName = senderName,
                    noteText = noteText
                )
                context.startActivity(intent)
            } catch (e: Exception) {
                android.util.Log.e("HeartBurstOverlay", "Failed to launch overlay: ${e.message}")
            }
        }
    }
}

private data class BurstParticle(
    val angleRad: Float,
    val speedFactor: Float,
    val sizeFactor: Float,
    val rotationSpeed: Float,
    val color: Color,
    val isSparkle: Boolean
)

@Composable
private fun HeartBurstOverlayScreen(
    coupleId: String,
    myUserId: String,
    myUserName: String,
    senderName: String,
    noteText: String,
    heartColor: HeartColor,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Animation stages:
    // 1. Big heart grows huge (bigHeartScale: 0f -> 1.55f)
    // 2. Big heart explodes into particles (burstProgress: 0f -> 1f)
    // 3. Action buttons appear at the bottom ("Tamam" & "Sen de Gönder")
    val bigHeartScale = remember { Animatable(0.05f) }
    val bigHeartAlpha = remember { Animatable(1f) }
    val burstProgress = remember { Animatable(0f) }
    val replyBurstProgress = remember { Animatable(0f) }

    var showButtons by remember { mutableStateOf(false) }
    var isSendingReply by remember { mutableStateOf(false) }
    var replySent by remember { mutableStateOf(false) }

    val particles = remember(heartColor) {
        val rng = Random(System.currentTimeMillis())
        val palette = heartColor.gradientColors + listOf(
            Color(0xFFFFB3C6),
            Color(0xFFFFD166),
            Color(0xFFFFFFFF)
        )
        List(42) { idx ->
            val baseAngle = (2.0 * PI * idx / 42.0).toFloat()
            val jitter = (rng.nextFloat() - 0.5f) * 0.25f
            BurstParticle(
                angleRad = baseAngle + jitter,
                speedFactor = 0.35f + rng.nextFloat() * 0.85f,
                sizeFactor = 0.55f + rng.nextFloat() * 0.95f,
                rotationSpeed = (rng.nextFloat() - 0.5f) * 260f,
                color = palette[idx % palette.size],
                isSparkle = (idx % 4 == 0)
            )
        }
    }

    LaunchedEffect(Unit) {
        try {
            HeartSoundPlayer.playLoveChime()
            HeartVibrator.vibrateHeartbeat(context)
        } catch (_: Exception) {}

        // Step 1: Giant heart swells up on screen
        bigHeartScale.animateTo(
            targetValue = 1.35f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
        // Brief dramatic swell right before bursting
        bigHeartScale.animateTo(
            targetValue = 1.68f,
            animationSpec = tween(durationMillis = 220, easing = FastOutLinearInEasing)
        )

        // Step 2: EXPLODE!
        try {
            HeartSoundPlayer.playHeartThump()
            HeartVibrator.vibrateHeartbeat(context)
        } catch (_: Exception) {}

        launch {
            bigHeartScale.animateTo(
                targetValue = 2.35f,
                animationSpec = tween(durationMillis = 260, easing = LinearOutSlowInEasing)
            )
        }
        launch {
            bigHeartAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
            )
        }
        launch {
            burstProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1050, easing = LinearOutSlowInEasing)
            )
        }

        // Step 3: Reveal bottom card and "Tamam" / "Sen de Gönder" buttons
        delay(220)
        showButtons = true
    }

    val infiniteTransition = rememberInfiniteTransition(label = "AfterBurstPulse")
    val gentlePulse by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GentlePulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f))
    ) {
        // Full-screen explosion canvas (shockwaves + flying hearts + sparkles)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width * 0.5f, size.height * 0.42f)
            val maxRadius = size.minDimension * 0.72f

            // Primary incoming explosion
            val p = burstProgress.value
            if (p > 0.001f && p < 0.999f) {
                drawHeartExplosion(
                    center = center,
                    progress = p,
                    maxRadius = maxRadius,
                    particles = particles,
                    primaryColor = heartColor.primaryColor
                )
            }

            // Secondary explosion when user taps "Sen de Gönder"
            val rp = replyBurstProgress.value
            if (rp > 0.001f && rp < 0.999f) {
                drawHeartExplosion(
                    center = center,
                    progress = rp,
                    maxRadius = maxRadius * 1.1f,
                    particles = particles,
                    primaryColor = heartColor.primaryColor
                )
            }
        }

        // Giant Central Heart (grows huge then bursts, and after burst shows a glowing beating heart)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(bottom = 110.dp),
            contentAlignment = Alignment.Center
        ) {
            // Pre-burst Giant Heart
            if (bigHeartAlpha.value > 0.01f) {
                Box(
                    modifier = Modifier
                        .size(250.dp)
                        .scale(bigHeartScale.value)
                        .alpha(bigHeartAlpha.value),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawGlossyHeart(
                            width = size.width,
                            height = size.height,
                            gradientColors = heartColor.gradientColors
                        )
                    }
                    Text(
                        text = "❤️",
                        fontSize = 54.sp
                    )
                }
            }

            // Post-burst Beating Heart + Sender Info
            androidx.compose.animation.AnimatedVisibility(
                visible = showButtons,
                enter = fadeIn(tween(350)),
                exit = fadeOut(tween(200))
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .scale(if (replySent) 1.25f else gentlePulse),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawGlossyHeart(
                                width = size.width,
                                height = size.height,
                                gradientColors = heartColor.gradientColors
                            )
                        }
                        Text(
                            text = if (replySent) "💞" else "❤️",
                            fontSize = 42.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFF1A0C14).copy(alpha = 0.90f),
                        border = BorderStroke(1.5.dp, heartColor.primaryColor.copy(alpha = 0.55f))
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp)
                        ) {
                            Text(
                                text = if (replySent) {
                                    "Kalbin $senderName'e Gönderildi! ❤️"
                                } else {
                                    "❤️ $senderName Sana Kalp Gönderdi!"
                                },
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )

                            if (noteText.isNotBlank() && !replySent) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "\"$noteText\"",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontStyle = FontStyle.Italic,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = Color(0xFFFFCCD5),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Action Bar: "Tamam" and "Sen de Gönder"
        AnimatedVisibility(
            visible = showButtons,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(tween(350)),
            exit = fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 28.dp)
        ) {
            Card(
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1A0C14).copy(alpha = 0.94f)
                ),
                border = BorderStroke(1.dp, heartColor.primaryColor.copy(alpha = 0.4f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // "Tamam" Button
                    OutlinedButton(
                        onClick = {
                            HeartVibrator.vibrateTouch(context)
                            CoupleNotificationHelper.dismissHeartAndNoteNotifications(context)
                            onDismiss()
                        },
                        enabled = !isSendingReply,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.45f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                    ) {
                        Text(
                            text = "Tamam",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }

                    // "Sen de Gönder" Button
                    Button(
                        onClick = {
                            if (isSendingReply || replySent) return@Button
                            CoupleNotificationHelper.dismissHeartAndNoteNotifications(context)
                            isSendingReply = true
                            scope.launch {
                                try {
                                    HeartSoundPlayer.playHeartThump()
                                    HeartVibrator.vibrateHeartbeat(context)
                                } catch (_: Exception) {}

                                launch {
                                    replyBurstProgress.snapTo(0f)
                                    replyBurstProgress.animateTo(
                                        targetValue = 1f,
                                        animationSpec = tween(
                                            durationMillis = 850,
                                            easing = LinearOutSlowInEasing
                                        )
                                    )
                                }

                                if (coupleId.isNotBlank() && myUserId.isNotBlank()) {
                                    runCatching {
                                        CoupleRepository(context.applicationContext).sendHeart(
                                            coupleId = coupleId,
                                            senderId = myUserId,
                                            senderName = myUserName,
                                            optionalNote = ""
                                        )
                                    }
                                }

                                isSendingReply = false
                                replySent = true
                                delay(750)
                                onDismiss()
                            }
                        },
                        enabled = !isSendingReply && !replySent,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = heartColor.primaryColor,
                            contentColor = Color.White,
                            disabledContainerColor = heartColor.primaryColor.copy(alpha = 0.7f),
                            disabledContentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1.35f)
                            .height(54.dp)
                    ) {
                        if (isSendingReply) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else if (replySent) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Gönderildi ❤️",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Sen de Gönder",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawGlossyHeart(
    width: Float,
    height: Float,
    gradientColors: List<Color>
) {
    val heartPath = Path().apply {
        moveTo(width * 0.5f, height * 0.84f)
        cubicTo(
            width * 0.12f, height * 0.58f,
            width * 0.01f, height * 0.30f,
            width * 0.25f, height * 0.15f
        )
        cubicTo(
            width * 0.40f, height * 0.05f,
            width * 0.50f, height * 0.25f,
            width * 0.50f, height * 0.25f
        )
        cubicTo(
            width * 0.50f, height * 0.25f,
            width * 0.60f, height * 0.05f,
            width * 0.75f, height * 0.15f
        )
        cubicTo(
            width * 0.99f, height * 0.30f,
            width * 0.88f, height * 0.58f,
            width * 0.50f, height * 0.84f
        )
        close()
    }

    drawPath(
        path = heartPath,
        brush = Brush.radialGradient(
            colors = gradientColors,
            center = Offset(width * 0.38f, height * 0.32f),
            radius = width * 0.68f
        )
    )
}

private fun DrawScope.drawHeartExplosion(
    center: Offset,
    progress: Float,
    maxRadius: Float,
    particles: List<BurstParticle>,
    primaryColor: Color
) {
    val alpha = (1f - progress).coerceIn(0f, 1f)

    // Shockwave ring 1
    drawCircle(
        color = primaryColor.copy(alpha = (alpha * 0.65f).coerceIn(0f, 1f)),
        radius = maxRadius * progress * 1.15f,
        center = center,
        style = Stroke(width = (18f * (1f - progress)).coerceAtLeast(2f))
    )

    // Shockwave ring 2
    val innerProgress = (progress * 1.35f).coerceAtMost(1f)
    drawCircle(
        color = Color.White.copy(alpha = ((1f - innerProgress) * 0.5f).coerceIn(0f, 1f)),
        radius = maxRadius * 0.75f * innerProgress,
        center = center,
        style = Stroke(width = (10f * (1f - innerProgress)).coerceAtLeast(1f))
    )

    // Central warm glow flash
    if (progress < 0.45f) {
        val flashAlpha = (1f - (progress / 0.45f)) * 0.55f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = flashAlpha),
                    primaryColor.copy(alpha = flashAlpha * 0.6f),
                    Color.Transparent
                ),
                center = center,
                radius = maxRadius * 0.65f
            ),
            radius = maxRadius * 0.65f,
            center = center
        )
    }

    // Flying heart particles and sparkles
    particles.forEach { particle ->
        val dist = maxRadius * particle.speedFactor * progress
        val gravityDrop = 140f * progress * progress
        val px = center.x + cos(particle.angleRad) * dist
        val py = center.y + sin(particle.angleRad) * dist + gravityDrop
        val particleAlpha = (1f - progress * 0.92f).coerceIn(0f, 1f)
        val baseSize = 36f * particle.sizeFactor * (1f - progress * 0.35f)

        if (particle.isSparkle) {
            drawCircle(
                color = particle.color.copy(alpha = particleAlpha),
                radius = baseSize * 0.32f,
                center = Offset(px, py)
            )
        } else {
            withTransform({
                translate(left = px - baseSize / 2f, top = py - baseSize / 2f)
                rotate(
                    degrees = particle.rotationSpeed * progress,
                    pivot = Offset(baseSize / 2f, baseSize / 2f)
                )
            }) {
                val miniHeart = Path().apply {
                    val w = baseSize
                    val h = baseSize
                    moveTo(w * 0.5f, h * 0.85f)
                    cubicTo(w * 0.1f, h * 0.58f, 0f, h * 0.28f, w * 0.25f, h * 0.14f)
                    cubicTo(w * 0.4f, h * 0.04f, w * 0.5f, h * 0.24f, w * 0.5f, h * 0.24f)
                    cubicTo(w * 0.5f, h * 0.24f, w * 0.6f, h * 0.04f, w * 0.75f, h * 0.14f)
                    cubicTo(w, h * 0.28f, w * 0.9f, h * 0.58f, w * 0.5f, h * 0.85f)
                    close()
                }
                drawPath(
                    path = miniHeart,
                    color = particle.color.copy(alpha = particleAlpha)
                )
            }
        }
    }
}
