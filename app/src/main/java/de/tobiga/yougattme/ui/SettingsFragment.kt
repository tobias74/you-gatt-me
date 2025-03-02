package de.tobiga.yougattme.ui

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import de.tobiga.yougattme.R

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
    }
}