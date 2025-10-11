package com.kolktech.linxshare

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

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
    }
}