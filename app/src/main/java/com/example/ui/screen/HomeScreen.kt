package com.example.ui.screen

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Couple
import com.example.data.model.User
import com.example.ui.component.PairingStatusBadge
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.HeartColor
import com.example.util.HeartSoundPlayer
import com.example.util.HeartVibrator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    currentUser: User,
    couple: Couple,
    heartBurstTrigger: Long,
    incomingNoteAlert: String?,
    currentThemeMode: AppThemeMode,
    selectedHeartColor: HeartColor = HeartColor.RUBY_RED,
    onThemeChange: (AppThemeMode) -> Unit,
    onHeartColorChange: (HeartColor) -> Unit = {},
    onSendHeart: (optionalNote: String) -> Unit,
    onSendLoveNote: (text: String) -> Unit,
    onUpdateAnniversary: (millis: Long) -> Unit,
    onUnlinkCouple: () -> Unit,
    onClearIncomingAlert: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Dialog states
    var showDatePicker by remember { mutableStateOf(false) }
    var showCustomNoteDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showUnlinkConfirmDialog by remember { mutableStateOf(false) }
    var customNoteInput by remember { mutableStateOf("") }

    // Visual heart click state for punchy animation
    var isHeartPressed by remember { mutableStateOf(false) }

    // Listen to external burst trigger (e.g. from partner or notification)
    LaunchedEffect(heartBurstTrigger) {
        if (heartBurstTrigger > 0L) {
            isHeartPressed = true
            delay(350)
            isHeartPressed = false
        }
    }

    // Dynamic relationship time counter
    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTimeMillis = System.currentTimeMillis()
        }
    }

    val partnerName = if (couple.user1Id == currentUser.userId) couple.user2Name else couple.user1Name
    val anniversaryTime = if (couple.anniversaryDate > 0L) couple.anniversaryDate else currentTimeMillis
    val elapsedMillis = (currentTimeMillis - anniversaryTime).coerceAtLeast(0L)

    val daysTogether = TimeUnit.MILLISECONDS.toDays(elapsedMillis)
    val hoursTogether = TimeUnit.MILLISECONDS.toHours(elapsedMillis) % 24
    val minutesTogether = TimeUnit.MILLISECONDS.toMinutes(elapsedMillis) % 60
    val secondsTogether = TimeUnit.MILLISECONDS.toSeconds(elapsedMillis) % 60

    // Gentle breathing animation for the heart
    val infiniteTransition = rememberInfiniteTransition(label = "HeartBeatPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    // Animated punch scale when pressed
    val targetScale = if (isHeartPressed) 1.28f else pulseScale
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "AnimatedScale"
    )

    val rippleAlpha by animateFloatAsState(
        targetValue = if (isHeartPressed) 0.45f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "RippleAlpha"
    )

    val rippleScale by animateFloatAsState(
        targetValue = if (isHeartPressed) 1.35f else 0.85f,
        animationSpec = tween(durationMillis = 350),
        label = "RippleScale"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${currentUser.displayName} & $partnerName",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "Bağlı ❤️",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Mimu LoveApp Canlı",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // Quick Theme Toggle
                    IconButton(
                        onClick = {
                            val nextMode = when (currentThemeMode) {
                                AppThemeMode.SYSTEM -> AppThemeMode.LIGHT
                                AppThemeMode.LIGHT -> AppThemeMode.DARK
                                AppThemeMode.DARK -> AppThemeMode.SYSTEM
                            }
                            HeartVibrator.vibrateTouch(context)
                            onThemeChange(nextMode)
                        },
                        modifier = Modifier.testTag("theme_toggle_home")
                    ) {
                        Icon(
                            imageVector = when (currentThemeMode) {
                                AppThemeMode.SYSTEM -> Icons.Outlined.BrightnessAuto
                                AppThemeMode.LIGHT -> Icons.Default.LightMode
                                AppThemeMode.DARK -> Icons.Default.DarkMode
                            },
                            contentDescription = "Tema Değiştir",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Settings Button
                    IconButton(
                        onClick = {
                            HeartVibrator.vibrateTouch(context)
                            showSettingsDialog = true
                        },
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Ayarlar",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Real-time Pairing Status Badge (Bağlı)
            PairingStatusBadge(
                isMatched = true,
                partnerName = partnerName
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Relationship Counter Card (Aşk Sayacı)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        HeartVibrator.vibrateTouch(context)
                        showDatePicker = true
                    }
                    .testTag("love_counter_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "BİRLİKTE GEÇEN ZAMAN",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Düzenle",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CounterBox(value = daysTogether.toString(), label = "GÜN")
                        Text(":", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        CounterBox(value = String.format(Locale.getDefault(), "%02d", hoursTogether), label = "SAAT")
                        Text(":", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        CounterBox(value = String.format(Locale.getDefault(), "%02d", minutesTogether), label = "DAKİKA")
                        Text(":", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        CounterBox(value = String.format(Locale.getDefault(), "%02d", secondsTogether), label = "SANİYE")
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Giant Pulsating Heart Interactive Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                contentAlignment = Alignment.Center
            ) {
                // Expanding ripple waves when pressed
                if (rippleAlpha > 0.01f) {
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .scale(rippleScale)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = rippleAlpha),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }

                // Interactive Heart Button
                Box(
                    modifier = Modifier
                        .size(190.dp)
                        .scale(animatedScale)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            scope.launch {
                                isHeartPressed = true
                                onSendHeart("")
                                delay(300)
                                isHeartPressed = false
                            }
                        }
                        .testTag("big_pulsing_heart"),
                    contentAlignment = Alignment.Center
                ) {
                    // Custom Drawn High-Resolution Glossy Vector Heart
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val width = size.width
                        val height = size.height

                        val heartPath = Path().apply {
                            moveTo(width * 0.5f, height * 0.82f)
                            cubicTo(
                                width * 0.15f, height * 0.58f,
                                width * 0.02f, height * 0.32f,
                                width * 0.25f, height * 0.16f
                            )
                            cubicTo(
                                width * 0.40f, height * 0.06f,
                                width * 0.50f, height * 0.26f,
                                width * 0.50f, height * 0.26f
                            )
                            cubicTo(
                                width * 0.50f, height * 0.26f,
                                width * 0.60f, height * 0.06f,
                                width * 0.75f, height * 0.16f
                            )
                            cubicTo(
                                width * 0.98f, height * 0.32f,
                                width * 0.85f, height * 0.58f,
                                width * 0.50f, height * 0.82f
                            )
                            close()
                        }

                        // Heart Gradient Fill
                        drawPath(
                            path = heartPath,
                            brush = Brush.radialGradient(
                                colors = selectedHeartColor.gradientColors,
                                center = Offset(width * 0.4f, height * 0.35f),
                                radius = width * 0.65f
                            )
                        )
                    }

                    // Floating text inside or around heart
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = "❤️",
                            fontSize = 32.sp
                        )
                        Text(
                            text = "Dokun",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = Color.White
                        )
                    }
                }
            }

            // Heart Stats & Partner status
            Text(
                text = "Kalbe dokunarak sevdiğinin telefonunu anında titret ve ses çal!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ) {
                Text(
                    text = "Toplam Kalp: ${couple.lastHeartCount} ❤️",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Quick Love Whispers (Aşk Fısıltıları)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.ChatBubbleOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Aşk Fısıltıları (Anlık Not)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val quickNotes = listOf(
                            "Seni çok seviyorum ❤️",
                            "Aklımdasın birtanem ✨",
                            "Çok özledim 🥺",
                            "Kocaman bir öpücük 💋",
                            "Günün harika geçsin 🌹"
                        )

                        quickNotes.forEach { note ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    HeartSoundPlayer.playLoveChime()
                                    HeartVibrator.vibrateTouch(context)
                                    onSendHeart(note)
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Notun gönderildi: $note")
                                    }
                                },
                                label = { Text(note) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            )
                        }

                        // Custom note button
                        FilterChip(
                            selected = false,
                            onClick = {
                                HeartVibrator.vibrateTouch(context)
                                showCustomNoteDialog = true
                            },
                            label = { Text("✍️ Özel Not Yaz...") },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Latest Received Note Card
            if (couple.lastNoteText.isNotBlank()) {
                val isFromPartner = couple.lastNoteSenderId != currentUser.userId
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isFromPartner) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isFromPartner) "$partnerName'den Son Mesaj:" else "Senin Son Mesajın:",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "\"${couple.lastNoteText}\"",
                            style = MaterialTheme.typography.bodyLarge.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
            }
        }
    }

    // Settings Modal
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Ayarlar",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    // Anniversary row
                    Text(
                        text = "Yıldönümü / Tanışma Tarihi",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val dateFormatted = remember(couple.anniversaryDate) {
                        if (couple.anniversaryDate > 0L) {
                            SimpleDateFormat("dd MMMM yyyy", Locale("tr")).format(Date(couple.anniversaryDate))
                        } else "Belirlenmedi"
                    }
                    Text(
                        text = "Kayıtlı Tarih: $dateFormatted",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = {
                            showSettingsDialog = false
                            showDatePicker = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tarihi Değiştir")
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Theme selector
                    Text(
                        text = "Görünüm Teması",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    listOf(
                        AppThemeMode.SYSTEM to "Sistem Teması (Otomatik Gece/Gündüz)",
                        AppThemeMode.LIGHT to "Açık Tema (Gül Pembesi)",
                        AppThemeMode.DARK to "Koyu Tema (Gece Kadifesi)"
                    ).forEach { (mode, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onThemeChange(mode)
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = (currentThemeMode == mode),
                                onClick = { onThemeChange(mode) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Heart Color Palette Panel
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = selectedHeartColor.primaryColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Kalp Rengi Seçimi",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Ana ekrandaki kalbin parıltısını dilediğin tonda kişiselleştir:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HeartColor.entries.forEach { colorOption ->
                            val isSelected = (selectedHeartColor == colorOption)
                            Surface(
                                onClick = {
                                    onHeartColorChange(colorOption)
                                    HeartVibrator.vibrateTouch(context)
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) {
                                    colorOption.primaryColor.copy(alpha = 0.15f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                },
                                border = if (isSelected) {
                                    androidx.compose.foundation.BorderStroke(2.dp, colorOption.primaryColor)
                                } else null,
                                modifier = Modifier.testTag("heart_color_${colorOption.name.lowercase()}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.radialGradient(
                                                    colors = colorOption.gradientColors
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Seçili",
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = colorOption.title,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = if (isSelected) colorOption.primaryColor else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    // End Pairing (Unlink) Button
                    Text(
                        text = "Çift Eşleşmesi",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Eşleşmeyi sonlandırdığınızda her iki cihaz da birbirinden ayrılır.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            showSettingsDialog = false
                            showUnlinkConfirmDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("unlink_couple_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Icon(imageVector = Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Eşleşmeyi Sonlandır")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Tamam")
                }
            }
        )
    }

    // Unlink Confirmation Dialog
    if (showUnlinkConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showUnlinkConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Uyarı",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Eşleşmeyi Sonlandır?",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "$partnerName ile olan eşleşmenizi sonlandırmak istediğinizden emin misiniz?\n\nBu işlem hem sizin hem de partnerinizin cihazında eşleşmeyi sonlandıracak ve ikinizi de kod ekranına döndürecektir.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUnlinkConfirmDialog = false
                        HeartVibrator.vibrateTouch(context)
                        onUnlinkCouple()
                    },
                    modifier = Modifier.testTag("confirm_unlink_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Evet, Sonlandır")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnlinkConfirmDialog = false }) {
                    Text("Vazgeç")
                }
            }
        )
    }

    // Custom Note Input Dialog
    if (showCustomNoteDialog) {
        AlertDialog(
            onDismissRequest = { showCustomNoteDialog = false },
            title = {
                Text(
                    text = "Aşk Notu Gönder",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column {
                    Text(
                        text = "Sevdiğin kişinin telefonuna anında iletilecek mesajını yaz:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = customNoteInput,
                        onValueChange = { customNoteInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_note_input"),
                        placeholder = { Text("Bugün aklımdan hiç çıkmadın...") },
                        maxLines = 4,
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (customNoteInput.isNotBlank()) {
                            onSendHeart(customNoteInput)
                            customNoteInput = ""
                            showCustomNoteDialog = false
                            scope.launch {
                                snackbarHostState.showSnackbar("Notun iletildi! ❤️")
                            }
                        }
                    },
                    modifier = Modifier.testTag("send_custom_note_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Gönder")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomNoteDialog = false }) {
                    Text("Vazgeç")
                }
            }
        )
    }

    // Anniversary DatePicker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = if (couple.anniversaryDate > 0L) couple.anniversaryDate else System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selected = datePickerState.selectedDateMillis
                        if (selected != null) {
                            onUpdateAnniversary(selected)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("Kaydet")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("İptal")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Incoming Note Alert Dialog (Shown when opened from notification or live incoming note)
    if (incomingNoteAlert != null) {
        AlertDialog(
            onDismissRequest = onClearIncomingAlert,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("❤️")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Kalp ve Aşk Notu Geldi!",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            text = {
                Text(
                    text = incomingNoteAlert,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearIncomingAlert()
                        // Reply with heart
                        onSendHeart("Ben de seni çok seviyorum! ❤️")
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Ben de Kalp Gönder ❤️")
                }
            },
            dismissButton = {
                TextButton(onClick = onClearIncomingAlert) {
                    Text("Kapat")
                }
            }
        )
    }
}

@Composable
private fun CounterBox(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
