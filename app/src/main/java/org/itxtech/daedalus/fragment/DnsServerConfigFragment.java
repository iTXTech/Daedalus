package org.itxtech.daedalus.fragment;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreference;
import com.google.android.material.snackbar.Snackbar;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.R;
import org.itxtech.daedalus.activity.ConfigActivity;
import org.itxtech.daedalus.provider.DnsTransport;
import org.itxtech.daedalus.server.AbstractDnsServer;
import org.itxtech.daedalus.server.CustomDnsServer;
import org.itxtech.daedalus.server.DnsServer;
import org.itxtech.daedalus.util.Logger;
import org.itxtech.daedalus.util.QueryLog;
import org.itxtech.daedalus.util.SocksProxy;
import org.itxtech.daedalus.util.TlsCertificates;
import org.minidns.dnsmessage.DnsMessage;
import org.minidns.dnsmessage.Question;
import org.minidns.record.Record;

import java.io.EOFException;
import java.net.SocketException;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
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
    private static final int IMPORT_CERTIFICATE_REQUEST_CODE = 1;
    private static final int TEST_TIMEOUT = 5000;
    private static final String[] PROXY_FIELDS = {"serverProxyHost", "serverProxyPort", "serverProxyUsername", "serverProxyPassword"};

    private int index;
    // Certificate chosen for this server; applied to the server together with the other fields
    private String certificate = null;
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

        findPreference("serverCertificate").setOnPreferenceClickListener(preference -> {
            onCertificateClicked();
            return true;
        });

        ListPreference serverTransport = findPreference("serverTransport");
        serverTransport.setOnPreferenceChangeListener((preference, newValue) -> {
            setTransportSummary(Integer.parseInt((String) newValue));
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
        ArrayList<CustomDnsServer> servers = Daedalus.configurations.getCustomDNSServers();
        CustomDnsServer server = index != ConfigActivity.ID_NONE && index < servers.size() ? servers.get(index) : null;

        serverName.setText(server != null ? server.getName() : "");
        serverName.setSummary(server != null ? server.getName() : "");
        setText("serverAddress", server != null ? server.getAddress() : "", R.string.settings_server_address_summary);
        setText("serverPort", String.valueOf(server != null ? server.getPort() : DnsServer.DNS_SERVER_DEFAULT_PORT),
                R.string.settings_server_port_summary);
        int transport = server != null ? server.getTransport() : AbstractDnsServer.TRANSPORT_AUTO;
        serverTransport.setValue(String.valueOf(transport));
        setTransportSummary(transport);

        boolean proxied = server != null && server.isProxied();
        serverProxied.setChecked(proxied);
        setText("serverProxyHost", proxied ? server.getProxyHost() : SocksProxy.DEFAULT_HOST, R.string.settings_socks5_host_summary);
        setText("serverProxyPort", String.valueOf(proxied ? server.getProxyPort() : SocksProxy.DEFAULT_PORT), 0);
        setText("serverProxyUsername", proxied && server.getProxyUsername() != null ? server.getProxyUsername() : "",
                R.string.settings_socks5_username_summary);
        password.setText(proxied && server.getProxyPassword() != null ? server.getProxyPassword() : "");
        password.setSummary(maskPassword(password.getText()));
        setProxyFieldsEnabled(proxied);

        certificate = server != null ? server.getCertificate() : null;
        updateCertificateSummary();
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

    /**
     * Shows the chosen protocol above the explanation of what each choice does.
     */
    private void setTransportSummary(int transport) {
        findPreference("serverTransport").setSummary(getString(transportName(transport)) + "\n"
                + getString(R.string.settings_server_transport_summary));
    }

    private static int transportName(int transport) {
        switch (transport) {
            case AbstractDnsServer.TRANSPORT_UDP:
                return R.string.settings_dns_udp;
            case AbstractDnsServer.TRANSPORT_TCP:
                return R.string.settings_dns_tcp;
            default:
                return R.string.settings_server_transport_auto;
        }
    }

    /**
     * The fields as filled in right now. Applied to a CustomDnsServer on save, or turned
     * into a stand-alone server for the connection test.
     */
    private static class Form {
        String name;
        String address;
        int port;
        int transport;
        boolean proxied;
        String proxyHost;
        int proxyPort;
        String proxyUsername;
        String proxyPassword;
        String certificate;

        void applyTo(CustomDnsServer server) {
            server.setName(name);
            server.setAddress(address);
            server.setPort(port);
            server.setTransport(transport);
            if (proxied) {
                server.setProxy(proxyHost, proxyPort, proxyUsername, proxyPassword);
            } else {
                server.clearProxy();
            }
            server.setCertificate(certificate);
        }

        AbstractDnsServer toServer() {
            return new AbstractDnsServer(address, port) {
                @Override
                public int getTransport() {
                    return transport;
                }

                @Override
                public String getProxyHost() {
                    return proxied ? proxyHost : null;
                }

                @Override
                public int getProxyPort() {
                    return proxied ? proxyPort : 0;
                }

                @Override
                public String getProxyUsername() {
                    return proxied && !proxyUsername.isEmpty() ? proxyUsername : null;
                }

                @Override
                public String getProxyPassword() {
                    return proxied ? proxyPassword : null;
                }

                @Override
                public String getCertificate() {
                    return certificate;
                }
            };
        }
    }

    /**
     * Reads the form, or shows a message and returns null when a required field is
     * missing or a port is not a number.
     */
    private Form readForm(boolean requireName) {
        Form form = new Form();
        form.name = text("serverName");
        form.address = text("serverAddress");
        String port = text("serverPort");
        form.proxied = ((SwitchPreference) findPreference("serverProxied")).isChecked();
        form.proxyHost = text("serverProxyHost");
        String proxyPort = text("serverProxyPort");
        form.proxyUsername = text("serverProxyUsername");
        String password = ((EditTextPreference) findPreference("serverProxyPassword")).getText();
        form.proxyPassword = password == null ? "" : password;
        form.certificate = certificate;
        String transport = ((ListPreference) findPreference("serverTransport")).getValue();
        form.transport = transport == null ? AbstractDnsServer.TRANSPORT_AUTO : Integer.parseInt(transport);

        if ((requireName && form.name.isEmpty()) || form.address.isEmpty() || port.isEmpty()
                || (form.proxied && (form.proxyHost.isEmpty() || proxyPort.isEmpty()))) {
            Snackbar.make(getView(), R.string.notice_fill_in_all, Snackbar.LENGTH_LONG).show();
            return null;
        }
        try {
            form.port = Integer.parseInt(port);
            form.proxyPort = form.proxied ? Integer.parseInt(proxyPort) : 0;
        } catch (NumberFormatException e) {
            Snackbar.make(getView(), R.string.notice_fill_in_all, Snackbar.LENGTH_LONG).show();
            return null;
        }
        return form;
    }

    /**
     * Sends one query with the settings as they are filled in now, without saving them,
     * and shows the outcome under the test button.
     */
    private void runTest() {
        if (testThread != null) {
            return;
        }
        Form form = readForm(false);
        if (form == null) {
            return;
        }
        final AbstractDnsServer server = form.toServer();
        Preference test = findPreference("serverTest");
        test.setEnabled(false);
        test.setSummary(R.string.server_test_running);
        SocksProxy.setExtraServer(server);
        testThread = new Thread(() -> {
            Daedalus app = Daedalus.getInstance();
            String result;
            try {
                DnsMessage message = DnsMessage.builder()
                        .addQuestion(new Question(Daedalus.DEFAULT_TEST_DOMAINS[0], Record.TYPE.A))
                        .setId(new Random().nextInt())
                        .setRecursionDesired(true)
                        .setOpcode(DnsMessage.OPCODE.QUERY)
                        .setResponseCode(DnsMessage.RESPONSE_CODE.NO_ERROR)
                        .setQrFlag(false)
                        .build();
                long start = SystemClock.elapsedRealtime();
                DnsTransport.Result response = DnsTransport.query(server, message, TEST_TIMEOUT, DnsTransport.NO_HOOKS);
                result = app.getString(R.string.server_test_ok, DnsTransport.describe(server),
                        SystemClock.elapsedRealtime() - start, QueryLog.summarize(response.message));
            } catch (Exception e) {
                Logger.logException(e);
                String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                result = app.getString(R.string.server_test_failed, reason);
                if (server.isProxied() && (e instanceof SocketException || e instanceof EOFException)) {
                    result += "\n" + app.getString(R.string.test_proxy_tcp_hint, server.getPort());
                }
            } finally {
                SocksProxy.setExtraServer(null);
            }
            final String text = result;
            Activity activity = getActivity();
            if (activity == null) {
                testThread = null;
                return;
            }
            activity.runOnUiThread(() -> {
                testThread = null;
                Preference preference = isAdded() ? findPreference("serverTest") : null;
                if (preference != null) {
                    preference.setSummary(text);
                    preference.setEnabled(true);
                }
            });
        }, "ServerTest");
        testThread.start();
    }

    private void onCertificateClicked() {
        if (certificate == null) {
            pickCertificate();
            return;
        }
        new AlertDialog.Builder(getActivity())
                .setItems(new CharSequence[]{getString(R.string.cert_replace), getString(R.string.cert_remove)},
                        (dialog, which) -> {
                            if (which == 0) {
                                pickCertificate();
                            } else {
                                certificate = null;
                                updateCertificateSummary();
                            }
                        })
                .show();
    }

    private void pickCertificate() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, IMPORT_CERTIFICATE_REQUEST_CODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != IMPORT_CERTIFICATE_REQUEST_CODE || resultCode != Activity.RESULT_OK
                || data == null || data.getData() == null) {
            return;
        }
        try {
            certificate = TlsCertificates.importFrom(getActivity().getContentResolver(), data.getData());
            updateCertificateSummary();
        } catch (Exception e) {
            Logger.logException(e);
            Snackbar.make(getView(), R.string.cert_import_failed, Snackbar.LENGTH_LONG).show();
        }
    }

    private void updateCertificateSummary() {
        Preference preference = findPreference("serverCertificate");
        if (certificate == null) {
            preference.setSummary(R.string.cert_none);
            return;
        }
        try {
            X509Certificate first = TlsCertificates.load(certificate).get(0);
            preference.setSummary(TlsCertificates.getCommonName(first.getSubjectX500Principal()) + "\n"
                    + getString(R.string.cert_details,
                    TlsCertificates.getCommonName(first.getIssuerX500Principal()),
                    DateFormat.getDateInstance().format(first.getNotAfter())));
        } catch (Exception e) {
            Logger.logException(e);
            preference.setSummary(certificate);
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();
        ArrayList<CustomDnsServer> servers = Daedalus.configurations.getCustomDNSServers();

        switch (id) {
            case R.id.action_apply:
                Form form = readForm(true);
                if (form == null) {
                    break;
                }
                String previousCertificate = null;
                if (index == ConfigActivity.ID_NONE || index >= servers.size()) {
                    CustomDnsServer server = new CustomDnsServer(form.name, form.address, form.port);
                    form.applyTo(server);
                    servers.add(server);
                } else {
                    CustomDnsServer server = servers.get(index);
                    previousCertificate = server.getCertificate();
                    form.applyTo(server);
                }
                // Save right away rather than only when the activity is destroyed
                Daedalus.configurations.save();
                if (previousCertificate != null && !previousCertificate.equals(form.certificate)) {
                    TlsCertificates.deleteIfUnused(previousCertificate);
                }
                Daedalus.setRulesChanged();
                getActivity().finish();
                break;
            case R.id.action_delete:
                if (index != ConfigActivity.ID_NONE && index < servers.size()) {
                    new AlertDialog.Builder(getActivity())
                            .setTitle(R.string.notice_delete_confirm_prompt)
                            .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                                CustomDnsServer removed = servers.remove(index);
                                Daedalus.configurations.save();
                                TlsCertificates.deleteIfUnused(removed.getCertificate());
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
