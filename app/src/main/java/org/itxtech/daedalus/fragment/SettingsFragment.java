package org.itxtech.daedalus.fragment;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.*;
import android.support.design.widget.Snackbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.R;
import org.itxtech.daedalus.util.DnsServer;

/**
 * Daedalus Project
 *
 * @author iTXTech
 * @link https://itxtech.org
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 */
public class SettingsFragment extends PreferenceFragment {
    private View view = null;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.perf_settings);

        ListPreference primaryServer = (ListPreference) findPreference("primary_server");
        primaryServer.setEntries(DnsServer.getDnsServerNames(Daedalus.getInstance()));
        primaryServer.setEntryValues(DnsServer.getDnsServerIds());
        primaryServer.setSummary(DnsServer.getDnsServerById(primaryServer.getValue()).getStringDescription(Daedalus.getInstance()));
        primaryServer.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                preference.setSummary(DnsServer.getDnsServerById((String) newValue).getStringDescription(Daedalus.getInstance()));
                Snackbar.make(view, R.string.notice_need_restart, Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                return true;
            }
        });

        ListPreference secondaryServer = (ListPreference) findPreference("secondary_server");
        secondaryServer.setEntries(DnsServer.getDnsServerNames(Daedalus.getInstance()));
        secondaryServer.setEntryValues(DnsServer.getDnsServerIds());
        secondaryServer.setSummary(DnsServer.getDnsServerById(secondaryServer.getValue()).getStringDescription(Daedalus.getInstance()));
        secondaryServer.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                preference.setSummary(DnsServer.getDnsServerById((String) newValue).getStringDescription(Daedalus.getInstance()));
                Snackbar.make(view, R.string.notice_need_restart, Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                return true;
            }
        });

        EditTextPreference testDNSServers = (EditTextPreference) findPreference("dns_test_servers");
        testDNSServers.setSummary(testDNSServers.getText());
        testDNSServers.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                preference.setSummary((String) newValue);
                return true;
            }
        });

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            SwitchPreference advanced = (SwitchPreference) findPreference("settings_advanced_switch");
            advanced.setEnabled(false);
            advanced.setChecked(false);
        }

        SwitchPreference advanced = (SwitchPreference) findPreference("settings_advanced_switch");
        advanced.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                updateAdvancedOptions((boolean) newValue);
                return true;
            }
        });

        ListPreference checkUpdate = (ListPreference) findPreference("settings_check_update");
        checkUpdate.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/iTXTech/Daedalus/releases")));
                return false;
            }
        });

        ListPreference issueTracker = (ListPreference) findPreference("settings_issue_tracker");
        issueTracker.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/iTXTech/Daedalus/issues")));
                return false;
            }
        });

        updateAdvancedOptions(advanced.isChecked());
    }

    private void updateAdvancedOptions(boolean checked) {
        PreferenceCategory category = (PreferenceCategory) findPreference("settings_advanced");
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
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        view = super.onCreateView(inflater, container, savedInstanceState);
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (view != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Snackbar.make(view, R.string.notice_legacy_api, Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        view = null;
    }
}
