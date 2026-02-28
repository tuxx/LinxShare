package com.kolktech.linxshare

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

class UploadActivity : ComponentActivity() {
    private val viewModel: UploadViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureNotificationPermission()

        val expirationValues = resources.getStringArray(R.array.expirationValues)
        val expirationLabels = resources.getStringArray(R.array.expiration)
        val expirationOptions = expirationValues.zip(expirationLabels)
        viewModel.initializeFromIntent(intent)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is UploadUiEvent.Success -> finish()
                        is UploadUiEvent.Failure -> {
                            Toast.makeText(applicationContext, event.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            UploadSettingsScreen(
                compatibleIntent = state.compatibleIntent,
                isUploading = state.isUploading,
                deleteKey = state.deleteKey,
                expirationLabel = expirationOptions.firstOrNull { it.first == state.expirationValue }?.second ?: getString(R.string.never),
                expirationValue = state.expirationValue,
                randomizeFilename = state.randomizeFilename,
                convertHeicToJpeg = state.convertHeicToJpeg,
                filename = state.filename,
                expirationOptions = expirationOptions,
                onDeleteKeyChanged = viewModel::setDeleteKey,
                onExpirationChanged = viewModel::setExpirationValue,
                onRandomizeFilenameChanged = viewModel::setRandomizeFilename,
                onConvertHeicToJpegChanged = viewModel::setConvertHeicToJpeg,
                onFilenameChanged = viewModel::setFilename,
                onUploadClick = { viewModel.startUpload(intent) }
            )
        }
    }

    private fun ensureNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
    }
}
