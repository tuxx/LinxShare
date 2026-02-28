package com.kolktech.linxshare

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSettingsScreen(
    settings: SettingsData,
    expirationLabel: String,
    expirationOptions: List<Pair<String, String>>,
    onLinxUrlChanged: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onDeleteKeyChanged: (String) -> Unit,
    onExpirationChanged: (String) -> Unit,
    onRandomizeFilenameChanged: (Boolean) -> Unit,
    onConvertHeicToJpegChanged: (Boolean) -> Unit,
    onNotifSingleChanged: (Boolean) -> Unit,
    onNotifMultiChanged: (Boolean) -> Unit
) {
    var editField by remember { mutableStateOf<String?>(null) }
    var openExpiration by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                SectionHeader(stringResource(R.string.server_settings))
                ClickableSettingRow(stringResource(R.string.linx_url), settings.linxUrl, { editField = "linx_url" })
                ClickableSettingRow(
                    stringResource(R.string.api_key),
                    if (settings.apiKey.isBlank()) stringResource(R.string.no_api_key_set) else stringResource(R.string.api_key_set),
                    { editField = "api_key" }
                )

                SectionHeader(stringResource(R.string.default_upload_settings))
                ClickableSettingRow(
                    stringResource(R.string.delete_key),
                    if (settings.deleteKey.isBlank()) stringResource(R.string.no_delete_key_set) else stringResource(R.string.delete_key_set),
                    { editField = "delete_key" }
                )
                ClickableSettingRow(stringResource(R.string.expiration), expirationLabel, { openExpiration = true })
                SwitchSettingRow(stringResource(R.string.randomize_filename), settings.randomizeFilename, onRandomizeFilenameChanged)
                SwitchSettingRow(stringResource(R.string.convert_heic_to_jpeg), settings.convertHeicToJpeg, onConvertHeicToJpegChanged)

                SectionHeader(stringResource(R.string.notification_settings))
                SwitchSettingRow(stringResource(R.string.notif_single), settings.notifSingle, onNotifSingleChanged)
                SwitchSettingRow(stringResource(R.string.notif_multi), settings.notifMulti, onNotifMultiChanged)
            }
        }
    }

    when (editField) {
        "linx_url" -> TextInputDialog(stringResource(R.string.linx_url), settings.linxUrl, { editField = null }) {
            onLinxUrlChanged(it)
            editField = null
        }
        "api_key" -> TextInputDialog(stringResource(R.string.api_key), settings.apiKey, { editField = null }) {
            onApiKeyChanged(it)
            editField = null
        }
        "delete_key" -> TextInputDialog(stringResource(R.string.delete_key), settings.deleteKey, { editField = null }) {
            onDeleteKeyChanged(it)
            editField = null
        }
    }

    if (openExpiration) {
        ChoiceDialog(
            title = stringResource(R.string.expiration),
            options = expirationOptions,
            selectedValue = settings.expirationValue,
            onDismiss = { openExpiration = false },
            onSelected = {
                onExpirationChanged(it)
                openExpiration = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadSettingsScreen(
    compatibleIntent: Boolean,
    isUploading: Boolean,
    deleteKey: String,
    expirationLabel: String,
    expirationValue: String,
    randomizeFilename: Boolean,
    convertHeicToJpeg: Boolean,
    filename: String,
    expirationOptions: List<Pair<String, String>>,
    onDeleteKeyChanged: (String) -> Unit,
    onExpirationChanged: (String) -> Unit,
    onRandomizeFilenameChanged: (Boolean) -> Unit,
    onConvertHeicToJpegChanged: (Boolean) -> Unit,
    onFilenameChanged: (String) -> Unit,
    onUploadClick: () -> Unit
) {
    var editField by remember { mutableStateOf<String?>(null) }
    var openExpiration by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    TextButton(onClick = onUploadClick, enabled = compatibleIntent && !isUploading) {
                        Text(stringResource(R.string.upload), color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    SectionHeader(stringResource(R.string.upload_settings))
                    ClickableSettingRow(
                        stringResource(R.string.delete_key),
                        if (deleteKey.isBlank()) stringResource(R.string.no_delete_key_set) else stringResource(R.string.delete_key_set),
                        { editField = "delete_key" }
                    )
                    ClickableSettingRow(stringResource(R.string.expiration), expirationLabel, { openExpiration = true })
                    SwitchSettingRow(stringResource(R.string.randomize_filename), randomizeFilename, onRandomizeFilenameChanged)
                    SwitchSettingRow(stringResource(R.string.convert_heic_to_jpeg), convertHeicToJpeg, onConvertHeicToJpegChanged)
                    if (!randomizeFilename) {
                        ClickableSettingRow(
                            stringResource(R.string.filename),
                            filename.ifBlank { stringResource(R.string.tap_to_set_filename) }
                        ) { editField = "filename" }
                    }
                }
            }

            if (isUploading) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0x80000000)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    when (editField) {
        "delete_key" -> TextInputDialog(stringResource(R.string.delete_key), deleteKey, { editField = null }) {
            onDeleteKeyChanged(it)
            editField = null
        }
        "filename" -> TextInputDialog(stringResource(R.string.filename), filename, { editField = null }) {
            onFilenameChanged(it)
            editField = null
        }
    }

    if (openExpiration) {
        ChoiceDialog(
            title = stringResource(R.string.expiration),
            options = expirationOptions,
            selectedValue = expirationValue,
            onDismiss = { openExpiration = false },
            onSelected = {
                onExpirationChanged(it)
                openExpiration = false
            }
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun ClickableSettingRow(title: String, summary: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Text(text = summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider()
}

@Composable
private fun SwitchSettingRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
    HorizontalDivider()
}

@Composable
private fun TextInputDialog(title: String, value: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TextField(value = text, onValueChange = { text = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selectedValue: String,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(value) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedValue == value, onClick = { onSelected(value) })
                        Text(text = label)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
