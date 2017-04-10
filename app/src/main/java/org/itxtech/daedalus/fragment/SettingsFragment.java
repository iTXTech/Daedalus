package org.itxtech.daedalus.fragment;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;
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

        ListPreference secondaryServer = (ListPreference) findPreference("secondary_server");
        secondaryServer.setEntries(DnsServer.getDnsServerNames(Daedalus.getInstance()));
        secondaryServer.setEntryValues(DnsServer.getDnsServerIds());

        if (Build.VERSION.SDK_INT < 21) {
            SwitchPreference countQueryTimes = (SwitchPreference) findPreference("settings_count_query_times");
            countQueryTimes.setChecked(false);
            countQueryTimes.setEnabled(false);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        view = super.onCreateView(inflater, container, savedInstanceState);

        ListPreference checkUpdate = (ListPreference) findPreference("settings_check_update");
        checkUpdate.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Snackbar.make(view, R.string.notice_checking_update, Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/iTXTech/Daedalus/releases")));
                return false;
            }
        });

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (view != null && Build.VERSION.SDK_INT < 21) {
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
