package org.itxtech.daedalus.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.R;
import org.itxtech.daedalus.activity.DnsServerConfigActivity;
import org.itxtech.daedalus.util.CustomDnsServer;

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
public class DnsServerConfigFragment extends PreferenceFragment implements Toolbar.OnMenuItemClickListener {
    private Intent intent = null;
    private int index;

    public void setIntent(Intent intent) {
        this.intent = intent;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        intent = null;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.perf_server);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);

        EditTextPreference serverName = (EditTextPreference) findPreference("serverName");
        serverName.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                preference.setSummary((String) newValue);
                return true;
            }
        });

        EditTextPreference serverAddress = (EditTextPreference) findPreference("serverAddress");
        serverAddress.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                preference.setSummary((String) newValue);
                return true;
            }
        });

        EditTextPreference serverPort = (EditTextPreference) findPreference("serverPort");
        serverPort.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                preference.setSummary((String) newValue);
                return true;
            }
        });


        index = intent.getIntExtra(DnsServerConfigActivity.LAUNCH_ACTION_CUSTOM_DNS_SERVER_ID, DnsServerConfigActivity.CUSTOM_DNS_SERVER_ID_NONE);
        if (index != DnsServerConfigActivity.CUSTOM_DNS_SERVER_ID_NONE) {
            CustomDnsServer server = Daedalus.configurations.getCustomDnsServers().get(index);
            serverName.setText(server.getName());
            serverName.setSummary(server.getName());
            serverAddress.setText(server.getAddress());
            serverAddress.setSummary(server.getAddress());
            serverPort.setText(String.valueOf(server.getPort()));
            serverPort.setSummary(String.valueOf(server.getPort()));
        } else {
            serverName.setText("");
            serverAddress.setText("");
            serverPort.setText("");
        }
        return view;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();

        switch (id) {
            case R.id.action_apply:
                if (index == DnsServerConfigActivity.CUSTOM_DNS_SERVER_ID_NONE) {
                    Daedalus.configurations.getCustomDnsServers().add(new CustomDnsServer(
                            ((EditTextPreference) findPreference("serverName")).getText(),
                            ((EditTextPreference) findPreference("serverAddress")).getText(),
                            Integer.parseInt(((EditTextPreference) findPreference("serverPort")).getText())
                    ));
                } else {
                    CustomDnsServer server = Daedalus.configurations.getCustomDnsServers().get(index);
                    server.setName(((EditTextPreference) findPreference("serverName")).getText());
                    server.setAddress(((EditTextPreference) findPreference("serverAddress")).getText());
                    server.setPort(Integer.parseInt(((EditTextPreference) findPreference("serverPort")).getText()));
                }
                getActivity().finish();
                break;
            case R.id.action_delete:
                if (index != DnsServerConfigActivity.CUSTOM_DNS_SERVER_ID_NONE) {
                    Daedalus.configurations.getCustomDnsServers().remove(index);
                }
                getActivity().finish();
                break;
        }

        return true;
    }
}
