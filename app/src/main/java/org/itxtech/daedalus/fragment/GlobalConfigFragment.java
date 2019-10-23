package org.itxtech.daedalus.fragment;

import android.content.Intent;
import android.os.Bundle;
import androidx.preference.*;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.R;
import org.itxtech.daedalus.activity.AppFilterActivity;
import org.itxtech.daedalus.activity.MainActivity;
import org.itxtech.daedalus.server.DnsServerHelper;

import java.util.ArrayList;

/**
 * Daedalus Project
 *
 * @author iTX Technologies
 * @link https://itxtech.org
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
public class GlobalConfigFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Daedalus.getPrefs().edit()
                .putString("primary_server", DnsServerHelper.getPrimary())
                .putString("secondary_server", DnsServerHelper.getSecondary())
                .apply();

        addPreferencesFromResource(R.xml.perf_settings);

        boolean visible = !Daedalus.getPrefs().getBoolean("settings_use_system_dns", false);
        for (String k : new ArrayList<String>() {{
            add("primary_server");
            add("secondary_server");
        }}) {
            ListPreference listPref = findPreference(k);
            listPref.setEntries(DnsServerHelper.getNames(Daedalus.getInstance()));
            listPref.setEntryValues(DnsServerHelper.getIds());
            listPref.setSummary(DnsServerHelper.getDescription(listPref.getValue(), Daedalus.getInstance()));
            listPref.setOnPreferenceChangeListener((preference, newValue) -> {
                preference.setSummary(DnsServerHelper.getDescription((String) newValue, Daedalus.getInstance()));
                return true;
            });
        }

        EditTextPreference testDNSServers = findPreference("dns_test_servers");
        testDNSServers.setSummary(testDNSServers.getText());
        testDNSServers.setOnPreferenceChangeListener((preference, newValue) -> {
            preference.setSummary((String) newValue);
            return true;
        });

        EditTextPreference logSize = findPreference("settings_log_size");
        logSize.setSummary(logSize.getText());
        logSize.setOnPreferenceChangeListener((preference, newValue) -> {
            preference.setSummary((String) newValue);
            return true;
        });

        SwitchPreference darkTheme = findPreference("settings_dark_theme");
        darkTheme.setOnPreferenceChangeListener((preference, o) -> {
            getActivity().startActivity(new Intent(Daedalus.getInstance(), MainActivity.class)
                    .putExtra(MainActivity.LAUNCH_FRAGMENT, MainActivity.FRAGMENT_SETTINGS)
                    .putExtra(MainActivity.LAUNCH_NEED_RECREATE, true));
            return true;
        });

        SwitchPreference advanced = findPreference("settings_advanced_switch");
        advanced.setOnPreferenceChangeListener((preference, newValue) -> {
            updateOptions((boolean) newValue, "settings_advanced");
            return true;
        });

        SwitchPreference appFilter = findPreference("settings_app_filter_switch");
        appFilter.setOnPreferenceChangeListener((p, w) -> {
            updateOptions((boolean) w, "settings_app_filter");
            return true;
        });

        findPreference("settings_app_filter_list").setOnPreferenceClickListener(preference -> {
            startActivity(new Intent(getActivity(), AppFilterActivity.class));
            return false;
        });

        findPreference("settings_check_update").setOnPreferenceClickListener(preference -> {
            Daedalus.openUri("https://github.com/iTXTech/Daedalus/releases");
            return false;
        });

        findPreference("settings_issue_tracker").setOnPreferenceClickListener(preference -> {
            Daedalus.openUri("https://github.com/iTXTech/Daedalus/issues");
            return false;
        });

        findPreference("settings_manual").setOnPreferenceClickListener(preference -> {
            Daedalus.openUri("https://github.com/iTXTech/Daedalus/wiki");
            return false;
        });

        findPreference("settings_privacy_policy").setOnPreferenceClickListener(preference -> {
            Daedalus.openUri("https://github.com/iTXTech/Daedalus/wiki/Privacy-Policy");
            return false;
        });

        updateOptions(advanced.isChecked(), "settings_advanced");
        updateOptions(appFilter.isChecked(), "settings_app_filter");
    }

    private void updateOptions(boolean checked, String pref) {
        PreferenceCategory category = findPreference(pref);
        for (int i = 1; i < category.getPreferenceCount(); i++) {
            Preference preference = category.getPreference(i);
            if (checked) {
                preference.setEnabled(true);
            } else {
                preference.setEnabled(false);
                if (preference instanceof SwitchPreference) {
                    ((SwitchPreference) preference).setChecked(false);
                }
            }
        }
    }
}
