package org.itxtech.daedalus.fragment;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import com.google.android.material.snackbar.Snackbar;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.R;
import org.itxtech.daedalus.activity.ConfigActivity;
import org.itxtech.daedalus.server.DnsServerHelper;
import org.itxtech.daedalus.util.NetworkRule;

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

/**
 * Edits one network rule: its condition and the primary/secondary servers to use.
 */
public class NetworkRuleConfigFragment extends ConfigFragment {
    private int index;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.perf_network_rule);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);

        ListPreference type = findPreference("ruleType");
        EditTextPreference subnets = findPreference("ruleSubnets");
        ListPreference primary = findPreference("rulePrimary");
        ListPreference secondary = findPreference("ruleSecondary");

        String[] names = DnsServerHelper.getNames(Daedalus.getInstance());
        String[] ids = DnsServerHelper.getIds();
        primary.setEntries(names);
        primary.setEntryValues(ids);
        secondary.setEntries(names);
        secondary.setEntryValues(ids);

        index = intent.getIntExtra(ConfigActivity.LAUNCH_ACTION_ID, ConfigActivity.ID_NONE);
        ArrayList<NetworkRule> rules = Daedalus.configurations.getNetworkRules();
        NetworkRule rule = index != ConfigActivity.ID_NONE && index < rules.size() ? rules.get(index) : null;

        type.setValue(String.valueOf(rule != null ? rule.getType() : NetworkRule.TYPE_SUBNET));
        subnets.setText(rule != null ? rule.getSubnets() : "");
        primary.setValue(rule != null && rule.getPrimary() != null ? rule.getPrimary() : DnsServerHelper.getPrimary());
        secondary.setValue(rule != null && rule.getSecondary() != null ? rule.getSecondary() : DnsServerHelper.getSecondary());

        type.setSummary(type.getEntry());
        type.setOnPreferenceChangeListener((preference, newValue) -> {
            int position = type.findIndexOfValue((String) newValue);
            preference.setSummary(position >= 0 ? type.getEntries()[position] : "");
            subnets.setEnabled(String.valueOf(NetworkRule.TYPE_SUBNET).equals(newValue));
            return true;
        });
        subnets.setEnabled(String.valueOf(NetworkRule.TYPE_SUBNET).equals(type.getValue()));
        updateSubnetsSummary(subnets, subnets.getText());
        subnets.setOnPreferenceChangeListener((preference, newValue) -> {
            updateSubnetsSummary((EditTextPreference) preference, (String) newValue);
            return true;
        });
        for (ListPreference preference : new ListPreference[]{primary, secondary}) {
            preference.setSummary(DnsServerHelper.getDescription(preference.getValue(), Daedalus.getInstance()));
            preference.setOnPreferenceChangeListener((p, newValue) -> {
                p.setSummary(DnsServerHelper.getDescription((String) newValue, Daedalus.getInstance()));
                return true;
            });
        }
        return view;
    }

    private void updateSubnetsSummary(EditTextPreference preference, String value) {
        preference.setSummary(value == null || value.trim().isEmpty()
                ? getString(R.string.network_rule_subnets_summary) : value);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();
        ArrayList<NetworkRule> rules = Daedalus.configurations.getNetworkRules();

        switch (id) {
            case R.id.action_apply:
                int type = Integer.parseInt(((ListPreference) findPreference("ruleType")).getValue());
                String subnets = ((EditTextPreference) findPreference("ruleSubnets")).getText();
                String primary = ((ListPreference) findPreference("rulePrimary")).getValue();
                String secondary = ((ListPreference) findPreference("ruleSecondary")).getValue();

                if (type == NetworkRule.TYPE_SUBNET && (subnets == null || subnets.trim().isEmpty())) {
                    Snackbar.make(getView(), R.string.notice_fill_in_all, Snackbar.LENGTH_LONG).show();
                    break;
                }

                NetworkRule rule = index != ConfigActivity.ID_NONE && index < rules.size() ? rules.get(index) : new NetworkRule();
                rule.setType(type);
                rule.setSubnets(subnets == null ? "" : subnets.trim());
                rule.setPrimary(primary);
                rule.setSecondary(secondary);
                if (index == ConfigActivity.ID_NONE || index >= rules.size()) {
                    rules.add(rule);
                }
                Daedalus.configurations.save();
                getActivity().finish();
                break;
            case R.id.action_delete:
                if (index != ConfigActivity.ID_NONE && index < rules.size()) {
                    new AlertDialog.Builder(getActivity())
                            .setTitle(R.string.notice_delete_confirm_prompt)
                            .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                                rules.remove(index);
                                Daedalus.configurations.save();
                                getActivity().finish();
                            })
                            .setNegativeButton(android.R.string.no, null)
                            .create()
                            .show();
                } else {
                    getActivity().finish();
                }
                break;
        }

        return true;
    }
}
