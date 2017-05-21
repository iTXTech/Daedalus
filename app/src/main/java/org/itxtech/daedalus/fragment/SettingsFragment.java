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
import org.itxtech.daedalus.util.DnsServerHelper;

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
public class SettingsFragment extends PreferenceFragment {
    private View view = null;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Daedalus.getPrefs().edit()
                .putString("primary_server", DnsServerHelper.getPrimary())
                .putString("secondary_server", DnsServerHelper.getSecondary())
                .apply();

        addPreferencesFromResource(R.xml.perf_settings);

        ListPreference primaryServer = (ListPreference) findPreference("primary_server");
        primaryServer.setEntries(DnsServerHelper.getNames(Daedalus.getInstance()));
        primaryServer.setEntryValues(DnsServerHelper.getIds());
        primaryServer.setSummary(DnsServerHelper.getDescription(primaryServer.getValue(), Daedalus.getInstance()));
        primaryServer.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                preference.setSummary(DnsServerHelper.getDescription((String) newValue, Daedalus.getInstance()));
                Snackbar.make(view, R.string.notice_need_restart, Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                return true;
            }
        });

        ListPreference secondaryServer = (ListPreference) findPreference("secondary_server");
        secondaryServer.setEntries(DnsServerHelper.getNames(Daedalus.getInstance()));
        secondaryServer.setEntryValues(DnsServerHelper.getIds());
        secondaryServer.setSummary(DnsServerHelper.getDescription(secondaryServer.getValue(), Daedalus.getInstance()));
        secondaryServer.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                preference.setSummary(DnsServerHelper.getDescription((String) newValue, Daedalus.getInstance()));
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
            SwitchPreference boot = (SwitchPreference) findPreference("settings_boot");
            boot.setEnabled(false);
            boot.setChecked(false);
        }

        SwitchPreference advanced = (SwitchPreference) findPreference("settings_advanced_switch");
        advanced.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                updateAdvancedOptions((boolean) newValue);
                return true;
            }
        });

        findPreference("settings_check_update").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/iTXTech/Daedalus/releases")));
                return false;
            }
        });

        findPreference("settings_issue_tracker").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/iTXTech/Daedalus/issues")));
                return false;
            }
        });

        findPreference("settings_manual").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/iTXTech/Daedalus/wiki")));
                return false;
            }
        });

        updateAdvancedOptions(advanced.isChecked());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        view = super.onCreateView(inflater, container, savedInstanceState);
        return view;
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
