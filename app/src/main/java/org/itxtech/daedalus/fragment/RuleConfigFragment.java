package org.itxtech.daedalus.fragment;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.support.design.widget.Snackbar;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.R;
import org.itxtech.daedalus.activity.ConfigActivity;
import org.itxtech.daedalus.util.Rule;

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
public class RuleConfigFragment extends ConfigFragment {
    private Intent intent = null;
    private View view;
    private int index;

    public void setIntent(Intent intent) {
        this.intent = intent;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        intent = null;
        view = null;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.perf_rule);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        view = super.onCreateView(inflater, container, savedInstanceState);

        EditTextPreference ruleName = (EditTextPreference) findPreference("ruleName");
        ruleName.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                preference.setSummary((String) newValue);
                return true;
            }
        });

        ListPreference ruleType = (ListPreference) findPreference("ruleType");
        final String[] entries = {"hosts", "DNSMasq"};
        String[] values = {"0", "1"};
        ruleType.setEntries(entries);
        ruleType.setEntryValues(values);
        ruleType.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                preference.setSummary(entries[Integer.parseInt((String) newValue)]);
                return true;
            }
        });

        EditTextPreference ruleDownloadUrl = (EditTextPreference) findPreference("ruleDownloadUrl");
        ruleDownloadUrl.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                preference.setSummary((String) newValue);
                return true;
            }
        });

        EditTextPreference ruleFilename = (EditTextPreference) findPreference("ruleFilename");
        ruleFilename.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                preference.setSummary((String) newValue);
                return true;
            }
        });


        index = intent.getIntExtra(ConfigActivity.LAUNCH_ACTION_ID, ConfigActivity.ID_NONE);
        if (index != ConfigActivity.ID_NONE) {
            Rule rule = Daedalus.configurations.getRules().get(index);
            ruleName.setText(rule.getName());
            ruleName.setSummary(rule.getName());
            int type = rule.getType();
            ruleType.setValue(String.valueOf(type));
            ruleType.setSummary(entries[type]);
            ruleFilename.setText(rule.getFileName());
            ruleFilename.setSummary(rule.getFileName());
            ruleDownloadUrl.setText(rule.getDownloadUrl());
            ruleDownloadUrl.setSummary(rule.getDownloadUrl());
        } else {
            ruleName.setText("");
            ruleName.setSummary("");
            ruleType.setValue("0");
            ruleType.setSummary(entries[Rule.TYPE_HOSTS]);
            ruleFilename.setText("");
            ruleFilename.setSummary("");
            ruleDownloadUrl.setText("");
            ruleDownloadUrl.setSummary("");
        }
        return view;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();

        switch (id) {
            case R.id.action_apply:
                String ruleName = ((EditTextPreference) findPreference("ruleName")).getText();
                String ruleType = ((ListPreference) findPreference("ruleType")).getValue();
                String ruleFilename = ((EditTextPreference) findPreference("ruleFilename")).getText();
                String ruleDownloadUrl = ((EditTextPreference) findPreference("ruleDownloadUrl")).getText();

                if (ruleName.equals("") | ruleType.equals("") | ruleFilename.equals("") | ruleDownloadUrl.equals("")) {
                    Snackbar.make(view, R.string.notice_fill_in_all, Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                    break;
                }

                if (index == ConfigActivity.ID_NONE) {
                    Daedalus.configurations.getRules().add(new Rule(ruleName, ruleFilename, Integer.parseInt(ruleType), ruleDownloadUrl));
                } else {
                    Rule rule = Daedalus.configurations.getRules().get(index);
                    rule.setName(ruleName);
                    rule.setType(Integer.parseInt(ruleType));
                    rule.setFileName(ruleFilename);
                    rule.setDownloadUrl(ruleDownloadUrl);
                }
                getActivity().finish();
                break;
            case R.id.action_delete:
                if (index != ConfigActivity.ID_NONE) {
                    new AlertDialog.Builder(getActivity())
                            .setTitle(R.string.notice_delete_confirm_prompt)
                            .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Daedalus.configurations.getRules().remove(index);
                                    getActivity().finish();
                                }
                            })
                            .setNegativeButton(R.string.no, null)
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
