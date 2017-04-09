package org.itxtech.daedalus.fragment;

import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceFragment;
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
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.perf_settings);

        ListPreference primaryServer = (ListPreference) findPreference("primary_server");
        primaryServer.setEntries(DnsServer.getDnsServerNames(Daedalus.getInstance()));
        primaryServer.setEntryValues(DnsServer.getDnsServerIds());
        primaryServer.setDefaultValue(Daedalus.DNS_SERVERS.get(0).getId());

        ListPreference secondaryServer = (ListPreference) findPreference("secondary_server");
        secondaryServer.setEntries(DnsServer.getDnsServerNames(Daedalus.getInstance()));
        secondaryServer.setEntryValues(DnsServer.getDnsServerIds());
        primaryServer.setDefaultValue(Daedalus.DNS_SERVERS.get(1).getId());
    }
}
