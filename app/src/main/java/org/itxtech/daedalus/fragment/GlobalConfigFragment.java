package org.itxtech.daedalus.fragment;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.preference.*;
import android.view.View;

import com.google.android.material.snackbar.Snackbar;

import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.R;
import org.itxtech.daedalus.activity.FilterAppProxyActivity;
import org.itxtech.daedalus.activity.MainActivity;
import org.itxtech.daedalus.util.server.DNSServerHelper;

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
public class GlobalConfigFragment extends PreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Daedalus.getPrefs().edit()
                .putString("primary_server", DNSServerHelper.getPrimary())
                .putString("secondary_server", DNSServerHelper.getSecondary())
                .apply();

        addPreferencesFromResource(R.xml.perf_settings);

        ListPreference primaryServer = (ListPreference) findPreference("primary_server");
        primaryServer.setEntries(DNSServerHelper.getNames(Daedalus.getInstance()));
        primaryServer.setEntryValues(DNSServerHelper.getIds());
        primaryServer.setSummary(DNSServerHelper.getDescription(primaryServer.getValue(), Daedalus.getInstance()));
        primaryServer.setOnPreferenceChangeListener((preference, newValue) -> {
            preference.setSummary(DNSServerHelper.getDescription((String) newValue, Daedalus.getInstance()));
            return true;
        });

        ListPreference secondaryServer = (ListPreference) findPreference("secondary_server");
        secondaryServer.setEntries(DNSServerHelper.getNames(Daedalus.getInstance()));
        secondaryServer.setEntryValues(DNSServerHelper.getIds());
        secondaryServer.setSummary(DNSServerHelper.getDescription(secondaryServer.getValue(), Daedalus.getInstance()));
        secondaryServer.setOnPreferenceChangeListener((preference, newValue) -> {
            preference.setSummary(DNSServerHelper.getDescription((String) newValue, Daedalus.getInstance()));
            return true;
        });

        EditTextPreference testDNSServers = (EditTextPreference) findPreference("dns_test_servers");
        testDNSServers.setSummary(testDNSServers.getText());
        testDNSServers.setOnPreferenceChangeListener((preference, newValue) -> {
            preference.setSummary((String) newValue);
            return true;
        });

        EditTextPreference logSize = (EditTextPreference) findPreference("settings_log_size");
        logSize.setSummary(logSize.getText());
        logSize.setOnPreferenceChangeListener((preference, newValue) -> {
            preference.setSummary((String) newValue);
            return true;
        });

        SwitchPreference darkTheme = (SwitchPreference) findPreference("settings_dark_theme");
        darkTheme.setOnPreferenceChangeListener((preference, o) -> {
            getActivity().startActivity(new Intent(Daedalus.getInstance(), MainActivity.class)
                    .putExtra(MainActivity.LAUNCH_FRAGMENT, MainActivity.FRAGMENT_SETTINGS)
                    .putExtra(MainActivity.LAUNCH_NEED_RECREATE, true));
            return true;
        });

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            SwitchPreference advanced = (SwitchPreference) findPreference("settings_advanced_switch");
            advanced.setEnabled(false);
            advanced.setChecked(false);
            SwitchPreference boot = (SwitchPreference) findPreference("settings_boot");
            boot.setEnabled(false);
            boot.setChecked(false);
            SwitchPreference app_filter = (SwitchPreference) findPreference("settings_app_filter_switch");
            app_filter.setEnabled(false);
            app_filter.setChecked(false);
        }

        SwitchPreference advanced = (SwitchPreference) findPreference("settings_advanced_switch");
        advanced.setOnPreferenceChangeListener((preference, newValue) -> {
            updateOptions((boolean) newValue, "settings_advanced");
            return true;
        });

        SwitchPreference app_filter = (SwitchPreference) findPreference("settings_app_filter_switch");
        app_filter.setOnPreferenceChangeListener((p, w) -> {
            updateOptions((boolean) w, "settings_app_filter");
            return true;
        });

        findPreference("settings_app_filter_list").setOnPreferenceClickListener(preference -> {
            startActivity(new Intent(getActivity(), FilterAppProxyActivity.class));
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
        updateOptions(app_filter.isChecked(), "settings_app_filter");
    }

    private void updateOptions(boolean checked, String pref) {
        PreferenceCategory category = (PreferenceCategory) findPreference(pref);
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

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Snackbar.make(view, R.string.notice_legacy_api, Snackbar.LENGTH_LONG).show();
        }
    }
}
