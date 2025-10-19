package com.example.documentsummarizer.ui.settings

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import com.example.documentsummarizer.R

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        findPreference<ListPreference>("summary_bullets")?.summaryProvider =
            ListPreference.SimpleSummaryProvider.getInstance()
    }
}