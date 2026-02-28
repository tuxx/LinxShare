package com.kolktech.linxshare

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private const val LEGACY_PREFS_FILE = "_preferences"
private const val DATASTORE_NAME = "settings"

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = DATASTORE_NAME,
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, LEGACY_PREFS_FILE))
    }
)

data class SettingsData(
    val linxUrl: String = "https://",
    val apiKey: String = "",
    val deleteKey: String = "",
    val expirationValue: String = "0",
    val randomizeFilename: Boolean = true,
    val convertHeicToJpeg: Boolean = false,
    val notifSingle: Boolean = false,
    val notifMulti: Boolean = true
)

class SettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.settingsDataStore

    private object Keys {
        val linxUrl = stringPreferencesKey("linx_url")
        val apiKey = stringPreferencesKey("api_key")
        val deleteKey = stringPreferencesKey("delete_key")
        val expiration = stringPreferencesKey("expiration")
        val randomizeFilename = booleanPreferencesKey("randomize_filename")
        val convertHeicToJpeg = booleanPreferencesKey("convert_heic_to_jpeg")
        val notifSingle = booleanPreferencesKey("notif_single_enable")
        val notifMulti = booleanPreferencesKey("notif_multi_enable")
    }

    val settingsFlow: Flow<SettingsData> = dataStore.data.map { prefs ->
        SettingsData(
            linxUrl = prefs[Keys.linxUrl] ?: "https://",
            apiKey = prefs[Keys.apiKey] ?: "",
            deleteKey = prefs[Keys.deleteKey] ?: "",
            expirationValue = prefs[Keys.expiration] ?: "0",
            randomizeFilename = prefs[Keys.randomizeFilename] ?: true,
            convertHeicToJpeg = prefs[Keys.convertHeicToJpeg] ?: false,
            notifSingle = prefs[Keys.notifSingle] ?: false,
            notifMulti = prefs[Keys.notifMulti] ?: true
        )
    }

    suspend fun getSnapshot(): SettingsData = settingsFlow.first()

    suspend fun setLinxUrl(value: String) = dataStore.edit { it[Keys.linxUrl] = value }
    suspend fun setApiKey(value: String) = dataStore.edit { it[Keys.apiKey] = value }
    suspend fun setDeleteKey(value: String) = dataStore.edit { it[Keys.deleteKey] = value }
    suspend fun setExpiration(value: String) = dataStore.edit { it[Keys.expiration] = value }
    suspend fun setRandomizeFilename(value: Boolean) = dataStore.edit { it[Keys.randomizeFilename] = value }
    suspend fun setConvertHeicToJpeg(value: Boolean) = dataStore.edit { it[Keys.convertHeicToJpeg] = value }
    suspend fun setNotifSingle(value: Boolean) = dataStore.edit { it[Keys.notifSingle] = value }
    suspend fun setNotifMulti(value: Boolean) = dataStore.edit { it[Keys.notifMulti] = value }
}
