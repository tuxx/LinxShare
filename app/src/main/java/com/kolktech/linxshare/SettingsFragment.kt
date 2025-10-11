package com.kolktech.linxshare

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        findPreference<Preference>("linx_url")?.summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()

        findPreference<Preference>("api_key")?.setSummaryProvider {
            if (preferenceManager.sharedPreferences?.getString(it.key, "").isNullOrEmpty()) {
                getString(R.string.no_api_key_set)
            } else {
                getString(R.string.api_key_set)
            }
        }

        findPreference<Preference>("delete_key")?.setSummaryProvider {
            if (preferenceManager.sharedPreferences?.getString(it.key, "").isNullOrEmpty()) {
                getString(R.string.no_delete_key_set)
            } else {
                getString(R.string.delete_key_set)
            }
        }

        findPreference<ListPreference>("expiration")?.let {
            it.setSummaryProvider { preference ->
                val value = preferenceManager.sharedPreferences?.getString(preference.key, "0") ?: "0"
                it.entries[it.findIndexOfValue(value)]
            }
        }

        // Request notifications permission if toggles are on and permission missing
        findPreference<SwitchPreferenceCompat>("notif_single_enable")?.setOnPreferenceChangeListener { _, newValue ->
            requestNotificationsIfNeeded(newValue as Boolean)
            true
        }
        findPreference<SwitchPreferenceCompat>("notif_multi_enable")?.setOnPreferenceChangeListener { _, newValue ->
            requestNotificationsIfNeeded(newValue as Boolean)
            true
        }
    }

    private fun requestNotificationsIfNeeded(enabled: Boolean) {
        if (!enabled) return
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val act = activity ?: return
            if (act.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(act, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1002)
            }
        }
    }
}