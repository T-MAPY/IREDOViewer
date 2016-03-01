package cz.tmapy.android.iredoviewer;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;

/**
 * Created by Kamil Svoboda on 28. 2. 2016.
 */
public class Settings extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        EditTextPreference editTextLine1 = (EditTextPreference) findPreference("pref_favorite1");
        editTextLine1.setSummary(editTextLine1.getText());

        EditTextPreference editTextLine2 = (EditTextPreference) findPreference("pref_favorite2");
        editTextLine2.setSummary(editTextLine2.getText());

        EditTextPreference editTextLine3 = (EditTextPreference) findPreference("pref_favorite3");
        editTextLine3.setSummary(editTextLine3.getText());

        EditTextPreference editText1 = (EditTextPreference) findPreference("pref_topic1");
        editText1.setSummary(editText1.getText());

        EditTextPreference editText2 = (EditTextPreference) findPreference("pref_topic2");
        editText2.setSummary(editText2.getText());

        EditTextPreference editText3 = (EditTextPreference) findPreference("pref_topic3");
        editText3.setSummary(editText3.getText());
    }

    protected void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    protected void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                          String key) {
        Preference pref = findPreference(key);
        if (pref instanceof EditTextPreference) {
            EditTextPreference etp = (EditTextPreference) pref;
            pref.setSummary(etp.getText());
        }
    }
}