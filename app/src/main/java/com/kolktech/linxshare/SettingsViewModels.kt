package com.kolktech.linxshare

import android.app.Application
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.core.content.IntentCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)
    private val _uiState = MutableStateFlow(SettingsData())
    val uiState: StateFlow<SettingsData> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.settingsFlow.collect { settings -> _uiState.value = settings }
        }
    }

    fun setLinxUrl(value: String) {
        viewModelScope.launch { repository.setLinxUrl(value) }
    }

    fun setApiKey(value: String) {
        viewModelScope.launch { repository.setApiKey(value) }
    }

    fun setDeleteKey(value: String) {
        viewModelScope.launch { repository.setDeleteKey(value) }
    }

    fun setExpiration(value: String) {
        viewModelScope.launch { repository.setExpiration(value) }
    }

    fun setRandomizeFilename(value: Boolean) {
        viewModelScope.launch { repository.setRandomizeFilename(value) }
    }

    fun setConvertHeicToJpeg(value: Boolean) {
        viewModelScope.launch { repository.setConvertHeicToJpeg(value) }
    }

    fun setNotifSingle(value: Boolean) {
        viewModelScope.launch { repository.setNotifSingle(value) }
    }

    fun setNotifMulti(value: Boolean) {
        viewModelScope.launch { repository.setNotifMulti(value) }
    }
}

data class UploadUiState(
    val compatibleIntent: Boolean = false,
    val isUploading: Boolean = false,
    val deleteKey: String = "",
    val expirationValue: String = "0",
    val randomizeFilename: Boolean = true,
    val convertHeicToJpeg: Boolean = false,
    val filename: String = ""
)

sealed class UploadUiEvent {
    object Success : UploadUiEvent()
    data class Failure(val message: String) : UploadUiEvent()
}

class UploadViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)
    private val uploadCoordinator = UploadCoordinator(application, repository)
    private var initialized = false

    private val _uiState = MutableStateFlow(UploadUiState())
    val uiState: StateFlow<UploadUiState> = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<UploadUiEvent>()
    val events: SharedFlow<UploadUiEvent> = _events

    init {
        viewModelScope.launch {
            val settings = repository.getSnapshot()
            _uiState.value = _uiState.value.copy(
                deleteKey = settings.deleteKey,
                expirationValue = settings.expirationValue,
                randomizeFilename = settings.randomizeFilename,
                convertHeicToJpeg = settings.convertHeicToJpeg
            )
        }
    }

    fun initializeFromIntent(intent: Intent?) {
        if (initialized) return
        initialized = true

        val compatibleIntent =
            intent?.action == Intent.ACTION_SEND || intent?.action == Intent.ACTION_SEND_MULTIPLE

        val initialFilename = if (intent?.action == Intent.ACTION_SEND) {
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, android.net.Uri::class.java)?.let { uri ->
                val last = uri.lastPathSegment ?: ""
                if (last.contains('.')) {
                    last
                } else {
                    val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(intent.type)
                    if (ext.isNullOrEmpty()) last else "$last.$ext"
                }
            } ?: ""
        } else {
            ""
        }

        _uiState.value = _uiState.value.copy(
            compatibleIntent = compatibleIntent,
            filename = initialFilename
        )
    }

    fun setDeleteKey(value: String) {
        _uiState.value = _uiState.value.copy(deleteKey = value)
    }

    fun setExpirationValue(value: String) {
        _uiState.value = _uiState.value.copy(expirationValue = value)
    }

    fun setRandomizeFilename(value: Boolean) {
        _uiState.value = _uiState.value.copy(randomizeFilename = value)
    }

    fun setConvertHeicToJpeg(value: Boolean) {
        _uiState.value = _uiState.value.copy(convertHeicToJpeg = value)
    }

    fun setFilename(value: String) {
        _uiState.value = _uiState.value.copy(filename = value)
    }

    fun startUpload(intent: Intent) {
        val state = _uiState.value
        if (!state.compatibleIntent || state.isUploading) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true)
            val result = uploadCoordinator.run(intent, state)
            _uiState.value = _uiState.value.copy(isUploading = false)

            when (result) {
                is UploadResult.Success -> _events.emit(UploadUiEvent.Success)
                is UploadResult.Failure -> _events.emit(UploadUiEvent.Failure(result.message))
            }
        }
    }
}
