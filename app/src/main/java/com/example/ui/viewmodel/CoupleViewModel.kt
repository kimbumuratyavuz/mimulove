package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Couple
import com.example.data.model.User
import com.example.data.repository.CoupleRepository
import com.example.service.CoupleHeartbeatService
import com.example.service.CoupleNotificationHelper
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.HeartColor
import com.example.util.HeartSoundPlayer
import com.example.util.HeartVibrator
import com.example.widget.MimuLoveAppWidgetProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CoupleViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("mimu_user_prefs", Context.MODE_PRIVATE)

    private val repository = CoupleRepository(application)

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _currentCouple = MutableStateFlow<Couple?>(null)
    val currentCouple: StateFlow<Couple?> = _currentCouple.asStateFlow()

    private val _themeMode = MutableStateFlow(AppThemeMode.SYSTEM)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _selectedHeartColor = MutableStateFlow(
        HeartColor.fromName(prefs.getString("selected_heart_color", HeartColor.RUBY_RED.name))
    )
    val selectedHeartColor: StateFlow<HeartColor> = _selectedHeartColor.asStateFlow()

    private val _heartBurstTrigger = MutableStateFlow(0L)
    val heartBurstTrigger: StateFlow<Long> = _heartBurstTrigger.asStateFlow()

    private val _incomingNoteAlert = MutableStateFlow<String?>(null)
    val incomingNoteAlert: StateFlow<String?> = _incomingNoteAlert.asStateFlow()

    private val _isPairing = MutableStateFlow(false)
    val isPairing: StateFlow<Boolean> = _isPairing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    private var userObservationJob: Job? = null
    private var coupleObservationJob: Job? = null
    private var lastObservedCoupleId: String = ""
    private var lastObservedHeartTime: Long = 0L
    private var lastObservedNoteTime: Long = 0L
    private var lastNotifiedPendingKey: String = ""
    private var wasPendingBefore: Boolean = false

    fun initUser(uid: String, displayName: String, email: String) {
        viewModelScope.launch {
            try {
                // Start foreground service so background notifications work immediately
                CoupleHeartbeatService.start(getApplication(), uid)

                val user = repository.getOrCreateUser(uid, displayName, email)
                _currentUser.value = user

                // Start observing couple documents where user is user1Id or user2Id
                startCoupleObservation(uid, user.coupleId)

                // Observe user document updates
                userObservationJob?.cancel()
                userObservationJob = viewModelScope.launch {
                    repository.observeUser(uid).collectLatest { updatedUser ->
                        if (updatedUser != null) {
                            _currentUser.value = updatedUser
                            if (updatedUser.coupleId.isNotBlank() && updatedUser.coupleId != lastObservedCoupleId) {
                                startCoupleObservation(uid, updatedUser.coupleId)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "Kullanıcı profili yüklenemedi: ${e.localizedMessage}"
            }
        }
    }

    private fun startCoupleObservation(uid: String, coupleIdHint: String) {
        lastObservedCoupleId = coupleIdHint
        coupleObservationJob?.cancel()
        coupleObservationJob = viewModelScope.launch {
            repository.observeCoupleForUser(uid, coupleIdHint).collectLatest { couple ->
                if (couple == null || (!couple.isMatched && !couple.isPending)) {
                    wasPendingBefore = false
                    _currentCouple.value = null
                    MimuLoveAppWidgetProvider.saveAndRefreshWidget(
                        context = getApplication(),
                        coupleNames = "Mimu LoveApp ❤️",
                        anniversaryDate = System.currentTimeMillis(),
                        lastNote = "",
                        isMatched = false
                    )
                    return@collectLatest
                }

                _currentCouple.value = couple

                // Handle Pending Pair Request state
                if (couple.isPending) {
                    wasPendingBefore = true
                    if (couple.pendingRequesterId.isNotBlank() && couple.pendingRequesterId != uid) {
                        val pendingKey = "${couple.coupleId}_${couple.anniversaryDate}"
                        if (lastNotifiedPendingKey != pendingKey) {
                            lastNotifiedPendingKey = pendingKey
                            val partnerName = couple.pendingRequesterName.ifBlank {
                                if (couple.user1Id == uid) couple.user2Name else couple.user1Name
                            }.ifBlank { "Sevdiğin" }
                            CoupleNotificationHelper.showPairRequestNotification(
                                getApplication(),
                                partnerName = partnerName
                            )
                        }
                    }
                    return@collectLatest
                }

                // Couple is Matched!
                if (wasPendingBefore) {
                    wasPendingBefore = false
                    _successMessage.value = "Tebrikler! Eşleşme onaylandı, kalpleriniz bağlandı ❤️"
                    try {
                        HeartSoundPlayer.playLoveChime()
                        HeartVibrator.vibrateHeartbeat(getApplication())
                    } catch (_: Exception) {}
                }

                // Update Home Screen Widget
                val coupleTitle = "${couple.user1Name} & ${couple.user2Name}"
                MimuLoveAppWidgetProvider.saveAndRefreshWidget(
                    context = getApplication(),
                    coupleNames = coupleTitle,
                    anniversaryDate = couple.anniversaryDate,
                    lastNote = couple.lastNoteText,
                    isMatched = true
                )

                // Initialize baseline timestamps on first load so old hearts/notes don't re-alert on startup
                if (lastObservedHeartTime == 0L) {
                    lastObservedHeartTime = couple.lastHeartTimestamp
                }
                if (lastObservedNoteTime == 0L) {
                    lastObservedNoteTime = couple.lastNoteTimestamp
                }

                // Detect live incoming heart from partner
                if (couple.lastHeartSenderId.isNotBlank() &&
                    couple.lastHeartSenderId != uid &&
                    couple.lastHeartTimestamp > lastObservedHeartTime
                ) {
                    lastObservedHeartTime = couple.lastHeartTimestamp
                    val hasNewNoteWithHeart = couple.lastNoteTimestamp > lastObservedNoteTime ||
                        couple.lastNoteTimestamp == couple.lastHeartTimestamp
                    if (couple.lastNoteTimestamp > lastObservedNoteTime) {
                        lastObservedNoteTime = couple.lastNoteTimestamp
                    }
                    val noteToDisplay = if (hasNewNoteWithHeart) couple.lastNoteText else ""
                    triggerHeartBurst(couple.lastHeartSenderName, noteToDisplay)
                } else if (couple.lastNoteSenderId.isNotBlank() &&
                    couple.lastNoteSenderId != uid &&
                    couple.lastNoteTimestamp > lastObservedNoteTime
                ) {
                    // Detect live incoming standalone love note from partner
                    lastObservedNoteTime = couple.lastNoteTimestamp
                    triggerHeartBurst(couple.lastNoteSenderName, couple.lastNoteText)
                }
            }
        }
    }

    fun pairWithCode(partnerCode: String) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            _isPairing.value = true
            _errorMessage.value = null
            _successMessage.value = null
            val result = repository.pairWithCode(user, partnerCode)
            _isPairing.value = false
            result.onSuccess { couple ->
                _currentCouple.value = couple
                _currentUser.value = user.copy(coupleId = couple.coupleId)
                startCoupleObservation(user.userId, couple.coupleId)
                CoupleHeartbeatService.start(getApplication(), user.userId)
                if (couple.isMatched) {
                    _successMessage.value = "Tebrikler! Sevdiğinle başarıyla eşleştiniz ❤️"
                } else {
                    val partnerName = couple.user2Name.ifBlank { "Eşin" }
                    _successMessage.value = "$partnerName adlı eşine eşleşme bildirimi gönderildi! Onayladığında ekran açılacak 💞"
                }
                try {
                    HeartSoundPlayer.playLoveChime()
                    HeartVibrator.vibrateHeartbeat(getApplication())
                } catch (_: Exception) {}
            }.onFailure { error ->
                _errorMessage.value = error.localizedMessage ?: "Eşleşme isteği gönderilemedi."
            }
        }
    }

    fun acceptPairRequest() {
        val user = _currentUser.value ?: return
        val couple = _currentCouple.value ?: return
        viewModelScope.launch {
            _isPairing.value = true
            _errorMessage.value = null
            val result = repository.acceptPairRequest(couple, user)
            _isPairing.value = false
            result.onSuccess { matchedCouple ->
                lastObservedHeartTime = matchedCouple.lastHeartTimestamp
                lastObservedNoteTime = matchedCouple.lastNoteTimestamp
                _currentCouple.value = matchedCouple
                _currentUser.value = user.copy(coupleId = matchedCouple.coupleId)
                _successMessage.value = "Eşleşme onaylandı! Kalpleriniz artık bir ❤️"
                try {
                    HeartSoundPlayer.playLoveChime()
                    HeartVibrator.vibrateHeartbeat(getApplication())
                } catch (_: Exception) {}
            }.onFailure { error ->
                _errorMessage.value = "Onaylama hatası: ${error.localizedMessage}"
            }
        }
    }

    fun rejectOrCancelPairRequest() {
        val user = _currentUser.value ?: return
        val couple = _currentCouple.value ?: return
        viewModelScope.launch {
            _isPairing.value = true
            repository.rejectOrCancelPairRequest(couple, user.userId)
            _isPairing.value = false
            _currentCouple.value = null
            _successMessage.value = "Eşleşme isteği iptal edildi."
        }
    }

    fun sendHeart(optionalNote: String = "") {
        val user = _currentUser.value ?: return
        val couple = _currentCouple.value ?: return
        viewModelScope.launch {
            try {
                // Immediate local visual feedback
                HeartSoundPlayer.playHeartThump()
                HeartVibrator.vibrateHeartbeat(getApplication())
                _heartBurstTrigger.value = System.currentTimeMillis()

                repository.sendHeart(
                    coupleId = couple.coupleId,
                    senderId = user.userId,
                    senderName = user.displayName,
                    optionalNote = optionalNote
                )
            } catch (e: Exception) {
                _errorMessage.value = "Kalp gönderilemedi: ${e.localizedMessage}"
            }
        }
    }

    fun sendLoveNote(noteText: String) {
        val user = _currentUser.value ?: return
        val couple = _currentCouple.value ?: return
        if (noteText.isBlank()) return
        viewModelScope.launch {
            try {
                HeartSoundPlayer.playLoveChime()
                HeartVibrator.vibrateTouch(getApplication())
                repository.sendLoveNote(
                    coupleId = couple.coupleId,
                    senderId = user.userId,
                    senderName = user.displayName,
                    noteText = noteText.trim()
                )
                _successMessage.value = "Aşk notun iletildi 💌"
            } catch (e: Exception) {
                _errorMessage.value = "Not gönderilemedi: ${e.localizedMessage}"
            }
        }
    }

    fun updateAnniversary(anniversaryMillis: Long) {
        val couple = _currentCouple.value ?: return
        viewModelScope.launch {
            try {
                repository.updateAnniversary(couple.coupleId, anniversaryMillis)
                _successMessage.value = "Yıldönümü tarihi güncellendi 💖"
            } catch (e: Exception) {
                _errorMessage.value = "Tarih güncellenemedi: ${e.localizedMessage}"
            }
        }
    }

    fun unlinkCouple() {
        val couple = _currentCouple.value ?: return
        viewModelScope.launch {
            try {
                repository.unlinkCouple(couple.coupleId, couple.user1Id, couple.user2Id)
                _currentCouple.value = null
                _successMessage.value = "Eşleşme her iki cihaz için de sonlandırıldı."
            } catch (e: Exception) {
                _errorMessage.value = "Eşleşme sonlandırılamadı: ${e.localizedMessage}"
            }
        }
    }

    /**
     * Called when the app is launched or resumed from a notification tap.
     */
    fun handleNotificationOpen(senderName: String, noteText: String) {
        triggerHeartBurst(senderName, noteText)
    }

    /**
     * Called when the app is launched via the 1x1 Quick Heart Widget.
     * Retries or waits if the couple model is still loading asynchronously.
     */
    fun triggerSendHeartWhenReady() {
        viewModelScope.launch {
            var attempts = 0
            while ((_currentUser.value == null || _currentCouple.value == null) && attempts < 15) {
                kotlinx.coroutines.delay(200)
                attempts++
            }
            if (_currentCouple.value?.isMatched == true && _currentUser.value != null) {
                sendHeart(optionalNote = "")
                _successMessage.value = "Kalbin sevgiline anında iletildi! ❤️"
            }
        }
    }

    private fun triggerHeartBurst(senderName: String, noteText: String) {
        _heartBurstTrigger.value = System.currentTimeMillis()
        HeartSoundPlayer.playLoveChime()
        HeartVibrator.vibrateHeartbeat(getApplication())
        if (noteText.isNotBlank()) {
            _incomingNoteAlert.value = "❤️ $senderName: \"$noteText\""
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
    }

    fun setHeartColor(heartColor: HeartColor) {
        _selectedHeartColor.value = heartColor
        prefs.edit().putString("selected_heart_color", heartColor.name).apply()
    }

    fun clearIncomingAlert() {
        _incomingNoteAlert.value = null
    }

    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }
}
