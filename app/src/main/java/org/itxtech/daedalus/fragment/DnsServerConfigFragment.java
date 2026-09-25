package org.itxtech.daedalus.fragment;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import androidx.preference.EditTextPreference;
import androidx.preference.SwitchPreference;
import com.google.android.material.snackbar.Snackbar;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.R;
import org.itxtech.daedalus.activity.ConfigActivity;
import org.itxtech.daedalus.server.CustomDnsServer;
import org.itxtech.daedalus.server.DnsServer;
import org.itxtech.daedalus.util.Logger;
import org.itxtech.daedalus.util.QueryLog;
import org.itxtech.daedalus.util.SocksProxy;
import org.minidns.dnsmessage.DnsMessage;
import org.minidns.dnsmessage.Question;
import org.minidns.record.Record;

import java.io.EOFException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Random;

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
public class DnsServerConfigFragment extends ConfigFragment {
    private static final int TEST_TIMEOUT = 5000;
    private static final String[] PROXY_FIELDS = {"serverProxyHost", "serverProxyPort", "serverProxyUsername", "serverProxyPassword"};

    private int index;
    private Thread testThread = null;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.perf_server);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);

        EditTextPreference serverName = findPreference("serverName");
        serverName.setOnPreferenceChangeListener((preference, newValue) -> {
            preference.setSummary((String) newValue);
            return true;
        });
        bindText("serverAddress", R.string.settings_server_address_summary);
        bindText("serverPort", R.string.settings_server_port_summary);
        numeric("serverPort");

        EditTextPreference serverAddress = findPreference("serverAddress");
        serverAddress.setOnPreferenceChangeListener((preference, newValue) -> {
            preference.setSummary((String) newValue);
            return true;
        });

        SwitchPreference serverProxied = findPreference("serverProxied");
        serverProxied.setOnPreferenceChangeListener((preference, newValue) -> {
            setProxyFieldsEnabled((Boolean) newValue);
            return true;
        });
        bindText("serverProxyHost", R.string.settings_socks5_host_summary);
        bindText("serverProxyPort", 0);
        numeric("serverProxyPort");
        bindText("serverProxyUsername", R.string.settings_socks5_username_summary);
        EditTextPreference password = findPreference("serverProxyPassword");
        password.setOnBindEditTextListener(editText ->
                editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD));
        password.setOnPreferenceChangeListener((preference, newValue) -> {
            preference.setSummary(maskPassword((String) newValue));
            return true;
        });

        findPreference("serverTest").setOnPreferenceClickListener(preference -> {
            runTest();
            return true;
        });

        index = intent.getIntExtra(ConfigActivity.LAUNCH_ACTION_ID, ConfigActivity.ID_NONE);
        if (index != ConfigActivity.ID_NONE) {
            CustomDnsServer server = Daedalus.configurations.getCustomDNSServers().get(index);
            serverName.setText(server.getName());
            serverName.setSummary(server.getName());
            serverAddress.setText(server.getAddress());
            serverAddress.setSummary(server.getAddress());
            serverPort.setText(String.valueOf(server.getPort()));
            serverPort.setSummary(String.valueOf(server.getPort()));
        } else {
            serverName.setText("");
            serverAddress.setText("");
            String port = String.valueOf(DnsServer.DNS_SERVER_DEFAULT_PORT);
            serverPort.setText(port);
            serverPort.setSummary(port);
        }
        boolean proxied = server != null && server.isProxied();
        serverProxied.setChecked(proxied);
        setText("serverProxyHost", proxied ? server.getProxyHost() : SocksProxy.DEFAULT_HOST, R.string.settings_socks5_host_summary);
        setText("serverProxyPort", String.valueOf(proxied ? server.getProxyPort() : SocksProxy.DEFAULT_PORT), 0);
        setText("serverProxyUsername", proxied && server.getProxyUsername() != null ? server.getProxyUsername() : "",
                R.string.settings_socks5_username_summary);
        password.setText(proxied && server.getProxyPassword() != null ? server.getProxyPassword() : "");
        password.setSummary(maskPassword(password.getText()));
        setProxyFieldsEnabled(proxied);
        return view;
    }

    private void bindText(String key, int hintRes) {
        final String hint = hintRes == 0 ? null : getString(hintRes);
        findPreference(key).setOnPreferenceChangeListener((preference, newValue) -> {
            preference.setSummary(withHint((String) newValue, hint));
            return true;
        });
    }

    private void setText(String key, String value, int hintRes) {
        EditTextPreference preference = findPreference(key);
        preference.setText(value);
        preference.setSummary(withHint(value, hintRes == 0 ? null : getString(hintRes)));
    }

    private void numeric(String key) {
        ((EditTextPreference) findPreference(key)).setOnBindEditTextListener(editText ->
                editText.setInputType(InputType.TYPE_CLASS_NUMBER));
    }

    private String text(String key) {
        String value = ((EditTextPreference) findPreference(key)).getText();
        return value == null ? "" : value.trim();
    }

    private static String withHint(String value, String hint) {
        boolean empty = value == null || value.trim().isEmpty();
        if (hint == null) {
            return empty ? "" : value;
        }
        return empty ? hint : value + "\n" + hint;
    }

    private static String maskPassword(String value) {
        return value == null || value.isEmpty() ? "" : "••••••";
    }

    private void setProxyFieldsEnabled(boolean enabled) {
        for (String key : PROXY_FIELDS) {
            findPreference(key).setEnabled(enabled);
        }
    }
    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();

        switch (id) {
            case R.id.action_apply:
                String serverName = ((EditTextPreference) findPreference("serverName")).getText();
                String serverAddress = ((EditTextPreference) findPreference("serverAddress")).getText();
                String serverPort = ((EditTextPreference) findPreference("serverPort")).getText();

                if (serverName.equals("") | serverAddress.equals("") | serverPort.equals("")) {
                    Snackbar.make(getView(), R.string.notice_fill_in_all, Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                    break;
                }

                if (index == ConfigActivity.ID_NONE) {
                    Daedalus.configurations.getCustomDNSServers().add(new CustomDnsServer(serverName, serverAddress, Integer.parseInt(serverPort)));
                } else {
                    CustomDnsServer server = Daedalus.configurations.getCustomDNSServers().get(index);
                    server.setName(serverName);
                    server.setAddress(serverAddress);
                    server.setPort(Integer.parseInt(serverPort));
                    CustomDnsServer server = servers.get(index);
                    previousCertificate = server.getCertificate();
                    form.applyTo(server);
                }
                // Save right away rather than only when the activity is destroyed
                Daedalus.configurations.save();
                }
                Daedalus.setRulesChanged();
                getActivity().finish();
                break;
            case R.id.action_delete:
                if (index != ConfigActivity.ID_NONE && index < servers.size()) {
                    new AlertDialog.Builder(getActivity())
                            .setTitle(R.string.notice_delete_confirm_prompt)
                            .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                                Daedalus.configurations.getCustomDNSServers().remove(index);
                                getActivity().finish();
                            })
                            .setNegativeButton(android.R.string.no, null)
                            .create()
                            .show();
                } else {
                    Daedalus.setRulesChanged();
                    getActivity().finish();
                }
                break;
        }

        return true;
    }
}
