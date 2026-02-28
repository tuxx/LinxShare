package com.kolktech.linxshare

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val viewModel: MainSettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val expirationValues = resources.getStringArray(R.array.expirationValues)
        val expirationLabels = resources.getStringArray(R.array.expiration)
        val expirationOptions = expirationValues.zip(expirationLabels)

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            MainSettingsScreen(
                linxUrl = state.linxUrl,
                apiKey = state.apiKey,
                deleteKey = state.deleteKey,
                expirationLabel = expirationOptions.firstOrNull { it.first == state.expirationValue }?.second ?: getString(R.string.never),
                expirationValue = state.expirationValue,
                randomizeFilename = state.randomizeFilename,
                convertHeicToJpeg = state.convertHeicToJpeg,
                notifSingle = state.notifSingle,
                notifMulti = state.notifMulti,
                expirationOptions = expirationOptions,
                onLinxUrlChanged = viewModel::setLinxUrl,
                onApiKeyChanged = viewModel::setApiKey,
                onDeleteKeyChanged = viewModel::setDeleteKey,
                onExpirationChanged = viewModel::setExpiration,
                onRandomizeFilenameChanged = viewModel::setRandomizeFilename,
                onConvertHeicToJpegChanged = viewModel::setConvertHeicToJpeg,
                onNotifSingleChanged = {
                    viewModel.setNotifSingle(it)
                    requestNotificationsIfNeeded(it)
                },
                onNotifMultiChanged = {
                    viewModel.setNotifMulti(it)
                    requestNotificationsIfNeeded(it)
                }
            )
        }
    }

    private fun requestNotificationsIfNeeded(enabled: Boolean) {
        if (!enabled) return
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1002)
            }
        }
    }
}
