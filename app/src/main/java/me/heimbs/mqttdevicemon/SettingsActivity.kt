package me.heimbs.mqttdevicemon

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.InputType
import me.heimbs.mqttdevicemon.R
import me.heimbs.mqttdevicemon.MainActivity
import com.google.android.material.snackbar.Snackbar
import me.heimbs.mqttdevicemon.SettingsActivity.SettingsFragment
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.EditTextPreference.OnBindEditTextListener
import android.widget.EditText
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.Preference.SummaryProvider
import androidx.preference.PreferenceManager
import java.lang.StringBuilder

// TODO: Add more mqtt settings
// TODO Add selection for battery safe setRepeating vs setExactAndAllowWhileIdle Alarms with warning
// TODO Save password on EncryptedSharedPreferences
class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        if (MainActivity.BAD_SETTINGS) {
            val missingSettings = Snackbar.make(
                findViewById(R.id.settingsCoordinatorLayout),
                R.string.missing_settings,
                Snackbar.LENGTH_LONG
            )
            missingSettings.show()
        }
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        val actionBar = supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(true)
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            val editTextPreferencePort = findPreference<EditTextPreference>("connection_port")
//                preferenceManager.findPreference<EditTextPreference>("connection_port")
            if (editTextPreferencePort != null) {
                editTextPreferencePort.setOnBindEditTextListener(OnBindEditTextListener { editText: EditText ->
                    editText.inputType =
                        InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
                })
                editTextPreferencePort.onPreferenceChangeListener =
                    Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                        val port = newValue.toString().toInt()
                        port in 0..65535
                    }
            }
//            val editTextPreferencePassword =
//                preferenceManager.findPreference<EditTextPreference>("authentication_password")
//            if (editTextPreferencePassword != null) {
//                editTextPreferencePassword.summaryProvider =
//                    SummaryProvider { provider: Preference? ->
//                        val getPassword =
//                            PreferenceManager.getDefaultSharedPreferences(requireContext())
//                                .getString(
//                                    "password",
//                                    getString(R.string.authentication_password_not_set)
//                                )
//
//                        //return "not set" else return password with asterisks
//                        if (getPassword == "not set") ({
//                            provider?.setSummary(getPassword)
//                        }).toString() else ({
//                            provider?.setSummary(getPassword?.length?.let { setAsterisks(it) })
//                        }).toString()
//                    }
//                editTextPreferencePassword.setOnBindEditTextListener(OnBindEditTextListener { editText: EditText ->
//                    editText.inputType =
//                        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
//                    editTextPreferencePassword.summaryProvider =
//                        SummaryProvider { preference: Preference? -> setAsterisks(editText.text.toString().length) }
//                })
//            }
        }

        //return the password in asterisks
        private fun setAsterisks(length: Int): String {
            val sb = StringBuilder()
            for (s in 0 until length) {
                sb.append("*")
            }
            return sb.toString()
        }
    }
}