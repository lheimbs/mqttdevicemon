package me.heimbs.mqttdevicemon;

import android.os.Bundle;
import android.text.InputType;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.snackbar.Snackbar;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        if (MainActivity.BAD_SETTINGS) {
            Snackbar missingSettings = Snackbar.make(findViewById(R.id.settingsCoordinatorLayout), R.string.missing_settings, Snackbar.LENGTH_LONG);
            missingSettings.show();
        }

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            final androidx.preference.EditTextPreference editTextPreferencePort = getPreferenceManager().findPreference("connection_port");
            if(editTextPreferencePort != null) {
                editTextPreferencePort.setOnBindEditTextListener(editText -> editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED));
                editTextPreferencePort.setOnPreferenceChangeListener((preference, newValue) -> {
                    int port = Integer.parseInt(newValue.toString());
                    return port >= 0 && port <= 65535;
                });
            }

            final androidx.preference.EditTextPreference editTextPreferencePassword = getPreferenceManager().findPreference("authentication_password");
            if (editTextPreferencePassword != null) {
                editTextPreferencePassword.setSummaryProvider(preference -> {
                    String getPassword = PreferenceManager.getDefaultSharedPreferences(requireContext()).getString("password", getString(R.string.authentication_password_not_set));

                    //we assume getPassword is not null
                    assert getPassword != null;

                    //return "not set" else return password with asterisks
                    if (getPassword.equals("not set")) {
                        return getPassword;
                    } else {
                        return (setAsterisks(getPassword.length()));
                    }
                });

                editTextPreferencePassword.setOnBindEditTextListener(editText -> {
                    editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    editTextPreferencePassword.setSummaryProvider(preference -> setAsterisks(editText.getText().toString().length()));
                });
            }
        }
        //return the password in asterisks
        private String setAsterisks(int length) {
            StringBuilder sb = new StringBuilder();
            for (int s = 0; s < length; s++) {
                sb.append("*");
            }
            return sb.toString();
        }
    }
}