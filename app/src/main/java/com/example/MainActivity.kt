package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.service.CoupleNotificationHelper
import com.example.ui.component.NotificationPermissionModal
import com.example.ui.screen.AuthScreen
import com.example.ui.screen.HomeScreen
import com.example.ui.screen.PairingScreen
import com.example.ui.theme.MimuLoveAppTheme
import com.example.ui.viewmodel.CoupleViewModel
import com.example.util.HeartSoundPlayer
import com.example.util.HeartVibrator
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {

    private var incomingSenderName: String? = null
    private var incomingNoteText: String? = null
    private var shouldTriggerSendHeart: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        parseNotificationIntent(intent)

        setContent {
            val coupleViewModel: CoupleViewModel = viewModel()
            val themeMode by coupleViewModel.themeMode.collectAsState()

            // Handle intent extras if opened from notification
            LaunchedEffect(incomingSenderName, incomingNoteText) {
                val sender = incomingSenderName
                val note = incomingNoteText
                if (sender != null || note != null) {
                    coupleViewModel.handleNotificationOpen(sender ?: "Aşkın", note ?: "")
                    incomingSenderName = null
                    incomingNoteText = null
                }
            }

            // Handle quick heart send trigger from 1x1 widget
            LaunchedEffect(shouldTriggerSendHeart) {
                if (shouldTriggerSendHeart) {
                    coupleViewModel.triggerSendHeartWhenReady()
                    shouldTriggerSendHeart = false
                }
            }

            MimuLoveAppTheme(themeMode = themeMode) {
                AppRootContent(viewModel = coupleViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseNotificationIntent(intent)
        if (intent.action == CoupleNotificationHelper.ACTION_TRIGGER_SEND_HEART) {
            shouldTriggerSendHeart = true
        }
    }

    private fun parseNotificationIntent(intent: Intent?) {
        if (intent?.action == CoupleNotificationHelper.ACTION_OPEN_HEART) {
            incomingSenderName = intent.getStringExtra(CoupleNotificationHelper.EXTRA_SENDER_NAME)
            incomingNoteText = intent.getStringExtra(CoupleNotificationHelper.EXTRA_NOTE_TEXT)
        } else if (intent?.action == CoupleNotificationHelper.ACTION_TRIGGER_SEND_HEART) {
            shouldTriggerSendHeart = true
        }
    }
}

@Composable
fun AppRootContent(viewModel: CoupleViewModel) {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = { uid, displayName, email ->
                viewModel.initUser(uid, displayName, email)
            }
        )
    } else {
        // Notification permission request shown in a stylish modal only after Google login
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val context = LocalContext.current
            var showPermissionModal by remember {
                mutableStateOf(
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                )
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                showPermissionModal = false
                if (isGranted) {
                    HeartSoundPlayer.playLoveChime()
                    HeartVibrator.vibrateTouch(context)
                }
            }

            if (showPermissionModal) {
                NotificationPermissionModal(
                    onGrantClicked = {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                    onDismiss = {
                        showPermissionModal = false
                    }
                )
            }
        }

        key(user.uid) {
            LaunchedEffect(user.uid) {
                viewModel.initUser(
                    uid = user.uid,
                    displayName = user.displayName ?: "Aşkım",
                    email = user.email ?: ""
                )
            }

            val userModel by viewModel.currentUser.collectAsState()
            val coupleModel by viewModel.currentCouple.collectAsState()
            val themeMode by viewModel.themeMode.collectAsState()
            val isPairing by viewModel.isPairing.collectAsState()
            val errorMessage by viewModel.errorMessage.collectAsState()
            val successMessage by viewModel.successMessage.collectAsState()
            val heartBurstTrigger by viewModel.heartBurstTrigger.collectAsState()
            val incomingNoteAlert by viewModel.incomingNoteAlert.collectAsState()
            val selectedHeartColor by viewModel.selectedHeartColor.collectAsState()

            val activeUser = userModel
            if (activeUser == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (coupleModel != null && coupleModel!!.isMatched) {
                HomeScreen(
                    currentUser = activeUser,
                    couple = coupleModel!!,
                    heartBurstTrigger = heartBurstTrigger,
                    incomingNoteAlert = incomingNoteAlert,
                    currentThemeMode = themeMode,
                    selectedHeartColor = selectedHeartColor,
                    onThemeChange = viewModel::setThemeMode,
                    onHeartColorChange = viewModel::setHeartColor,
                    onSendHeart = viewModel::sendHeart,
                    onSendLoveNote = viewModel::sendLoveNote,
                    onUpdateAnniversary = viewModel::updateAnniversary,
                    onUnlinkCouple = viewModel::unlinkCouple,
                    onClearIncomingAlert = viewModel::clearIncomingAlert
                )
            } else {
                PairingScreen(
                    currentUser = activeUser,
                    isPairing = isPairing,
                    errorMessage = errorMessage,
                    successMessage = successMessage,
                    currentThemeMode = themeMode,
                    onThemeChange = viewModel::setThemeMode,
                    onPairRequested = viewModel::pairWithCode,
                    onClearMessages = viewModel::clearMessages
                )
            }
        }
    }
}
