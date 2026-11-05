package com.sadit.rulesbrowser

import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, RulesFragment())
            .commit()
    }

    class RulesFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.rules, rootKey)
            findPreference<Preference>("clear_now")?.setOnPreferenceClickListener {
                CookieManager.getInstance().removeAllCookies(null)
                WebStorage.getInstance().deleteAllData()
                WebView(requireContext()).apply {
                    clearCache(true)
                    clearHistory()
                    clearFormData()
                    destroy()
                }
                Toast.makeText(requireContext(), "Browsing data cleared", Toast.LENGTH_SHORT).show()
                true
            }
        }
    }
}
