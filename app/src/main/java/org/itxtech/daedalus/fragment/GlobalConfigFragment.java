package org.itxtech.daedalus.fragment;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.preference.*;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.R;
import org.itxtech.daedalus.activity.AppFilterActivity;
import org.itxtech.daedalus.activity.MainActivity;
import org.itxtech.daedalus.server.DnsServerHelper;
import org.itxtech.daedalus.service.DaedalusVpnService;
import org.itxtech.daedalus.util.ConfigBackup;
import org.itxtech.daedalus.util.Logger;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

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
    private static final int EXPORT_CONFIG_REQUEST_CODE = 11;
    private static final int IMPORT_CONFIG_REQUEST_CODE = 12;
    private static final String PREF_CONFIG_URL = "config_remote_url";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.perf_settings);

        for (String k : new String[]{"primary_server", "secondary_server"}) {
            ListPreference listPref = findPreference(k);
            final String hint = getString(k.equals("primary_server") ? R.string.primary_server_summary : R.string.secondary_server_summary);
            listPref.setOnPreferenceChangeListener((preference, newValue) -> {
                preference.setSummary(DnsServerHelper.getDescription((String) newValue, Daedalus.getInstance()) + "\n" + hint);
                // Picked up by the running VPN after the value has been persisted
                DaedalusVpnService.notifyConfigurationChanged();
                return true;
            });
        }
        updateServerLists();

        findPreference("settings_server_management").setOnPreferenceClickListener(preference -> {
            Fragment parent = getParentFragment();
            if (parent instanceof SettingsFragment) {
                ((SettingsFragment) parent).showServerManagement();
            }
            return true;
        });
        findPreference("settings_network_rules").setOnPreferenceClickListener(preference -> {
            Fragment parent = getParentFragment();
            if (parent instanceof SettingsFragment) {
                ((SettingsFragment) parent).showNetworkRules();
            }
            return true;
        });

        setSummaryWithHint(findPreference("dns_test_servers"), R.string.settings_dns_test_servers_summary);
        setSummaryWithHint(findPreference("settings_log_size"), R.string.settings_log_size_summary);

        ListPreference theme = findPreference("settings_theme");
        theme.setOnPreferenceChangeListener((preference, newValue) -> {
            if (!newValue.equals(theme.getValue())) {
                recreateSettings();
            }
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

        findPreference("settings_export_config").setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, "daedalus-config.json");
            startActivityForResult(intent, EXPORT_CONFIG_REQUEST_CODE);
            return true;
        });
        findPreference("settings_import_config").setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            startActivityForResult(intent, IMPORT_CONFIG_REQUEST_CODE);
            return true;
        });
        findPreference("settings_import_config_url").setOnPreferenceClickListener(preference -> {
            promptConfigUrl();
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

    @Override
    public void onResume() {
        super.onResume();
        // Servers may have been switched on or off in Server Management meanwhile
        updateServerLists();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        try {
            if (requestCode == EXPORT_CONFIG_REQUEST_CODE) {
                try (OutputStream out = requireContext().getContentResolver().openOutputStream(uri, "rwt")) {
                    if (out == null) {
                        throw new IllegalStateException("Cannot open " + uri);
                    }
                    out.write(ConfigBackup.export().getBytes(StandardCharsets.UTF_8));
                }
                showMessage(getString(R.string.notice_config_exported));
            } else if (requestCode == IMPORT_CONFIG_REQUEST_CODE) {
                String json;
                try (InputStream in = requireContext().getContentResolver().openInputStream(uri)) {
                    if (in == null) {
                        throw new IllegalStateException("Cannot open " + uri);
                    }
                    json = readAll(in);
                }
                applyImport(json);
            }
        } catch (Exception e) {
            Logger.logException(e);
            showMessage(getString(R.string.notice_servers_import_failed, e.getMessage()));
        }
    }

    private void promptConfigUrl() {
        EditText input = new EditText(getActivity());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint(R.string.config_remote_url_hint);
        input.setText(Daedalus.getPrefs().getString(PREF_CONFIG_URL, ""));
        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.settings_import_config_url)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String url = input.getText().toString().trim();
                    if (url.isEmpty()) {
                        return;
                    }
                    Daedalus.getPrefs().edit().putString(PREF_CONFIG_URL, url).apply();
                    fetchConfig(url);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void fetchConfig(String url) {
        new Thread(() -> {
            Activity activity = getActivity();
            try {
                String json = ConfigBackup.fetch(url);
                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        try {
                            applyImport(json);
                        } catch (Exception e) {
                            Logger.logException(e);
                            showMessage(getString(R.string.notice_servers_import_failed, e.getMessage()));
                        }
                    });
                }
            } catch (Exception e) {
                Logger.logException(e);
                if (activity != null) {
                    activity.runOnUiThread(() -> showMessage(getString(R.string.notice_servers_import_failed, e.getMessage())));
                }
            }
        }, "ConfigFetch").start();
    }

    private void applyImport(String json) throws Exception {
        ConfigBackup.Result result = ConfigBackup.importJson(json);
        String message = getString(R.string.notice_config_imported,
                result.serversAdded, result.serversUpdated,
                result.networkRulesAdded, result.networkRulesUpdated,
                result.rulesAdded, result.rulesUpdated,
                result.apps, result.preferences);
        if (!result.warnings.isEmpty()) {
            message += "\n" + TextUtils.join("\n", result.warnings);
        }
        showMessage(message);
        // Rebuild the settings screen so that every widget shows the imported values
        recreateSettings();
    }

    private void recreateSettings() {
        Activity activity = getActivity();
        if (activity != null) {
            activity.startActivity(new Intent(Daedalus.getInstance(), MainActivity.class)
                    .putExtra(MainActivity.LAUNCH_FRAGMENT, MainActivity.FRAGMENT_SETTINGS)
                    .putExtra(MainActivity.LAUNCH_NEED_RECREATE, true));
        }
    }

    private void showMessage(String message) {
        Activity activity = getActivity();
        if (activity != null) {
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
        }
    }

    private static String readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    /**
     * Fills the primary/secondary lists with the servers that are switched on. A saved
     * selection that has been switched off falls back to an available server.
     */
    private void updateServerLists() {
        String[] names = DnsServerHelper.getNames(Daedalus.getInstance());
        String[] ids = DnsServerHelper.getIds();
        for (String k : new String[]{"primary_server", "secondary_server"}) {
            ListPreference listPref = findPreference(k);
            listPref.setEntries(names);
            listPref.setEntryValues(ids);
            boolean primary = k.equals("primary_server");
            String value = primary ? DnsServerHelper.getPrimary() : DnsServerHelper.getSecondary();
            listPref.setValue(value);
            listPref.setSummary(DnsServerHelper.getDescription(value, Daedalus.getInstance()) + "\n"
                    + getString(primary ? R.string.primary_server_summary : R.string.secondary_server_summary));
        }
    }

    /**
     * Shows the current value of a text setting with an explanation underneath, or only
     * the explanation while the value is empty.
     */
    private void setSummaryWithHint(EditTextPreference preference, int hintRes) {
        final String hint = hintRes == 0 ? null : getString(hintRes);
        preference.setSummary(withHint(preference.getText(), hint));
        preference.setOnPreferenceChangeListener((p, newValue) -> {
            p.setSummary(withHint((String) newValue, hint));
            return true;
        });
    }

    private static String withHint(CharSequence value, String hint) {
        boolean empty = value == null || value.toString().trim().isEmpty();
        if (hint == null) {
            return empty ? "" : value.toString();
        }
        return empty ? hint : value + "\n" + hint;
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
