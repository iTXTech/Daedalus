package org.itxtech.daedalus.fragment;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import com.google.android.material.snackbar.Snackbar;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.R;
import org.itxtech.daedalus.activity.ConfigActivity;
import org.itxtech.daedalus.util.Logger;
import org.itxtech.daedalus.util.Rule;
import org.itxtech.daedalus.widget.ClickPreference;

import java.io.*;
import java.util.concurrent.TimeUnit;

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
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    private static final int READ_REQUEST_CODE = 1;
    private Intent intent = null;
    private Thread mThread = null;
    private RuleConfigHandler mHandler = null;
    private int id;

    public void setIntent(Intent intent) {
        this.intent = intent;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        stopThread();
        intent = null;
        mHandler.shutdown();
        mHandler = null;
    }

    private void stopThread() {
        if (mThread != null) {
            mThread.interrupt();
            mThread = null;
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.perf_rule);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);

        mHandler = new RuleConfigHandler().setView(view);

        final EditTextPreference ruleName = findPreference("ruleName");
        ruleName.setOnPreferenceChangeListener((preference, newValue) -> {
            preference.setSummary((String) newValue);
            return true;
        });

        final ListPreference ruleType = findPreference("ruleType");
        final String[] entries = {"Hosts", "DNSMasq"};
        String[] values = {"0", "1"};
        ruleType.setEntries(entries);
        ruleType.setEntryValues(values);
        ruleType.setOnPreferenceChangeListener((preference, newValue) -> {
            preference.setSummary(entries[Integer.parseInt((String) newValue)]);
            return true;
        });

        final EditTextPreference ruleDownloadUrl = findPreference("ruleDownloadUrl");
        ruleDownloadUrl.setOnPreferenceChangeListener((preference, newValue) -> {
            preference.setSummary((String) newValue);
            return true;
        });

        final EditTextPreference ruleFilename = findPreference("ruleFilename");
        ruleFilename.setOnPreferenceChangeListener((preference, newValue) -> {
            preference.setSummary((String) newValue);
            return true;
        });

        ClickPreference ruleSync = findPreference("ruleSync");
        ruleSync.setOnPreferenceClickListener(preference -> {
            save();
            if (mThread == null) {
                Snackbar.make(getView(), R.string.notice_start_download, Snackbar.LENGTH_SHORT).show();
                if (ruleDownloadUrl.getText().startsWith("content:/")) {
                    mThread = new Thread(() -> {
                        try {
                            InputStream inputStream = getActivity().getContentResolver().openInputStream(Uri.parse(ruleDownloadUrl.getText()));
                            int readLen;
                            byte[] data = new byte[1024];
                            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                            while ((readLen = inputStream.read(data)) != -1) {
                                buffer.write(data, 0, readLen);
                            }
                            inputStream.close();
                            buffer.flush();
                            mHandler.obtainMessage(RuleConfigHandler.MSG_RULE_DOWNLOADED,
                                    new RuleData(ruleFilename.getText(), buffer.toByteArray())).sendToTarget();
                        } catch (Exception e) {
                            Logger.logException(e);
                        } finally {
                            stopThread();
                        }
                    });
                } else {
                    mThread = new Thread(() -> {
                        try {
                            Request request = new Request.Builder()
                                    .url(ruleDownloadUrl.getText()).get().build();
                            Response response = HTTP_CLIENT.newCall(request).execute();
                            Logger.info("Downloaded " + ruleDownloadUrl.getText());
                            if (response.isSuccessful() && mHandler != null) {
                                mHandler.obtainMessage(RuleConfigHandler.MSG_RULE_DOWNLOADED,
                                        new RuleData(ruleFilename.getText(), response.body().bytes())).sendToTarget();
                            }
                        } catch (Exception e) {
                            Logger.logException(e);
                            if (mHandler != null) {
                                mHandler.obtainMessage(RuleConfigHandler.MSG_RULE_DOWNLOADED,
                                        new RuleData(ruleFilename.getText(), new byte[0])).sendToTarget();
                            }
                        } finally {
                            stopThread();
                        }
                    });
                }
                mThread.start();
            } else {
                Snackbar.make(getView(), R.string.notice_now_downloading, Snackbar.LENGTH_LONG).show();
            }
            return false;
        });

        ListPreference ruleImportBuildIn = findPreference("ruleImportBuildIn");
        ruleImportBuildIn.setEntries(Rule.getBuildInRuleNames());
        ruleImportBuildIn.setEntryValues(Rule.getBuildInRuleEntries());
        ruleImportBuildIn.setOnPreferenceChangeListener((preference, newValue) -> {
            Rule rule = Daedalus.RULES.get(Integer.parseInt((String) newValue));
            ruleName.setText(rule.getName());
            ruleName.setSummary(rule.getName());
            ruleType.setValue(String.valueOf(rule.getType()));
            ruleType.setSummary(Rule.getTypeById(rule.getType()));
            ruleFilename.setText(rule.getFileName());
            ruleFilename.setSummary(rule.getFileName());
            ruleDownloadUrl.setText(rule.getDownloadUrl());
            ruleDownloadUrl.setSummary(rule.getDownloadUrl());
            return true;
        });

        ClickPreference ruleImportExternal = findPreference("ruleImportExternal");
        ruleImportExternal.setOnPreferenceClickListener(preference -> {
            performFileSearch();
            return false;
        });


        ruleImportBuildIn.setValue("0");
        if (intent != null) {
            id = intent.getIntExtra(ConfigActivity.LAUNCH_ACTION_ID, ConfigActivity.ID_NONE);
        } else {
            id = ConfigActivity.ID_NONE;
        }
        Rule rule;
        if (id != ConfigActivity.ID_NONE && (rule = Rule.getRuleById(String.valueOf(id))) != null) {
            Rule.getRuleById(String.valueOf(id));
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

    private void performFileSearch() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);

        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

        startActivityForResult(intent, READ_REQUEST_CODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (resultData != null) {
                Uri uri = resultData.getData();
                try {
                    getActivity().getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    String file = System.currentTimeMillis() + ".dr";//Daedalus Rule
                    InputStream inputStream = getActivity().getContentResolver().openInputStream(uri);
                    OutputStream outputStream = new FileOutputStream(Daedalus.rulePath + file);
                    byte[] b = new byte[1024];
                    while ((inputStream.read(b)) != -1) {
                        outputStream.write(b);
                    }
                    inputStream.close();
                    outputStream.close();

                    ((EditTextPreference) findPreference("ruleFilename")).setText(file);
                    findPreference("ruleFilename").setSummary(file);
                    ((EditTextPreference) findPreference("ruleDownloadUrl")).setText(uri.toString());
                    findPreference("ruleDownloadUrl").setSummary(uri.toString());

                    Snackbar.make(getView(), R.string.notice_importing_rule, Snackbar.LENGTH_LONG).show();
                } catch (Exception e) {
                    Logger.logException(e);
                }
            }
        }
    }

    private boolean save() {
        String ruleName = ((EditTextPreference) findPreference("ruleName")).getText();
        String ruleType = ((ListPreference) findPreference("ruleType")).getValue();
        String ruleFilename = ((EditTextPreference) findPreference("ruleFilename")).getText();
        String ruleDownloadUrl = ((EditTextPreference) findPreference("ruleDownloadUrl")).getText();

        if (ruleName.equals("") | ruleType.equals("") | ruleFilename.equals("")) {
            Snackbar.make(getView(), R.string.notice_fill_in_all, Snackbar.LENGTH_SHORT)
                    .setAction("Action", null).show();
            return false;
        }

        if (id == ConfigActivity.ID_NONE) {
            Rule rule = new Rule(ruleName, ruleFilename, Integer.parseInt(ruleType), ruleDownloadUrl);
            rule.addToConfig();
            id = Integer.parseInt(rule.getId());
        } else {
            Rule rule = Rule.getRuleById(String.valueOf(id));
            if (rule != null) {
                rule.setName(ruleName);
                rule.setType(Integer.parseInt(ruleType));
                rule.setFileName(ruleFilename);
                rule.setDownloadUrl(ruleDownloadUrl);
            }
        }
        return true;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();

        switch (id) {
            case R.id.action_apply:
                if (save()) {
                    getActivity().finish();
                }
                break;
            case R.id.action_delete:
                if (this.id != ConfigActivity.ID_NONE) {
                    new AlertDialog.Builder(getActivity())
                            .setTitle(R.string.notice_delete_confirm_prompt)
                            .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                                Rule rule = Rule.getRuleById(String.valueOf(RuleConfigFragment.this.id));
                                if (rule != null) {
                                    rule.removeFromConfig();
                                }
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

    private class RuleData {
        private byte[] data;
        private String filename;

        RuleData(String filename, byte[] data) {
            this.data = data;
            this.filename = filename;
        }

        byte[] getData() {
            return data;
        }

        String getFilename() {
            return filename;
        }
    }

    private static class RuleConfigHandler extends Handler {
        static final int MSG_RULE_DOWNLOADED = 0;

        private View view = null;

        RuleConfigHandler setView(View view) {
            this.view = view;
            return this;
        }

        void shutdown() {
            view = null;
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            switch (msg.what) {
                case MSG_RULE_DOWNLOADED:
                    RuleData ruleData = (RuleData) msg.obj;
                    if (ruleData.data.length == 0) {
                        if (view != null) {
                            Snackbar.make(view, R.string.notice_download_failed, Snackbar.LENGTH_SHORT).show();
                        }
                        break;
                    }
                    try {
                        File file = new File(Daedalus.rulePath + ruleData.getFilename());
                        FileOutputStream stream = new FileOutputStream(file);
                        stream.write(ruleData.getData());
                        stream.close();
                    } catch (Exception e) {
                        Logger.logException(e);
                    }

                    if (view != null) {
                        Snackbar.make(view, R.string.notice_downloaded, Snackbar.LENGTH_SHORT).show();
                    }
                    break;
            }
        }
    }
}
