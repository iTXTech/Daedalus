package org.itxtech.daedalus.fragment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.R;
import org.itxtech.daedalus.activity.ConfigActivity;
import org.itxtech.daedalus.server.DnsServerHelper;
import org.itxtech.daedalus.util.NetworkRule;
import org.itxtech.daedalus.util.NetworkRules;

import java.util.List;

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
 * Settings > Network Rules: the rules that pick the primary/secondary servers by the
 * network the device is on. Tapping a rule opens it in ConfigActivity.
 */
public class NetworkRulesFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferenceScreen(getPreferenceManager().createPreferenceScreen(getPreferenceManager().getContext()));
        rebuild();
    }

    @Override
    public void onResume() {
        super.onResume();
        rebuild();
    }

    private void rebuild() {
        Context context = getPreferenceManager().getContext();
        PreferenceScreen screen = getPreferenceScreen();
        screen.removeAll();

        Preference add = new Preference(context);
        add.setTitle(R.string.network_rules_add);
        add.setIconSpaceReserved(false);
        add.setOnPreferenceClickListener(preference -> {
            open(ConfigActivity.ID_NONE);
            return true;
        });
        screen.addPreference(add);

        List<NetworkRule> rules = Daedalus.configurations.getNetworkRules();
        if (rules.isEmpty()) {
            Preference none = new Preference(context);
            none.setSummary(R.string.network_rules_none);
            none.setSelectable(false);
            none.setIconSpaceReserved(false);
            screen.addPreference(none);
        }
        for (int i = 0; i < rules.size(); i++) {
            final int index = i;
            NetworkRule rule = rules.get(i);
            Preference preference = new Preference(context);
            preference.setIconSpaceReserved(false);
            preference.setTitle(NetworkRules.describe(rule, context));
            preference.setSummary(getString(R.string.network_rule_servers,
                    DnsServerHelper.getDescription(rule.getPrimary(), context),
                    DnsServerHelper.getDescription(rule.getSecondary(), context)));
            preference.setOnPreferenceClickListener(p -> {
                open(index);
                return true;
            });
            screen.addPreference(preference);
        }
    }

    private void open(int index) {
        startActivity(new Intent(getActivity(), ConfigActivity.class)
                .putExtra(ConfigActivity.LAUNCH_ACTION_ID, index)
                .putExtra(ConfigActivity.LAUNCH_ACTION_FRAGMENT, ConfigActivity.LAUNCH_FRAGMENT_NETWORK_RULE));
    }
}
