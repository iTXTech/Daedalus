package org.itxtech.daedalus.fragment;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.Snackbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import de.measite.minidns.DNSMessage;
import de.measite.minidns.Question;
import de.measite.minidns.Record;
import de.measite.minidns.record.A;
import de.measite.minidns.source.NetworkDataSource;
import de.measite.minidns.util.InetAddressUtil;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.R;
import org.itxtech.daedalus.activity.MainActivity;
import org.itxtech.daedalus.util.DnsServerHelper;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.Set;

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
public class DnsTestFragment extends ToolbarFragment {
    private static final String TAG = "DServerTest";

    private static Thread mThread = null;
    private static Runnable mRunnable = null;
    private DnsTestHandler mHandler = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dns_test, container, false);

        final TextView textViewTestInfo = (TextView) view.findViewById(R.id.textView_test_info);

        final Spinner spinnerServerChoice = (Spinner) view.findViewById(R.id.spinner_server_choice);
        ArrayAdapter spinnerArrayAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, DnsServerHelper.getNames(Daedalus.getInstance()));
        spinnerServerChoice.setAdapter(spinnerArrayAdapter);
        spinnerServerChoice.setSelection(DnsServerHelper.getPosition(DnsServerHelper.getPrimary()));

        final AutoCompleteTextView textViewTestUrl = (AutoCompleteTextView) view.findViewById(R.id.autoCompleteTextView_test_url);
        ArrayAdapter autoCompleteArrayAdapter = new ArrayAdapter<>(Daedalus.getInstance(), android.R.layout.simple_list_item_1, Daedalus.DEFAULT_TEST_DOMAINS);
        textViewTestUrl.setAdapter(autoCompleteArrayAdapter);

        mRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    String testDomain = textViewTestUrl.getText().toString();
                    if (testDomain.equals("")) {
                        testDomain = Daedalus.DEFAULT_TEST_DOMAINS[0];
                    }
                    StringBuilder testText = new StringBuilder();
                    ArrayList<String> dnsServers = new ArrayList<String>() {{
                        add(DnsServerHelper.getAddressByDescription(Daedalus.getInstance(), spinnerServerChoice.getSelectedItem().toString()));
                        String servers = Daedalus.getPrefs().getString("dns_test_servers", "");
                        if (!servers.equals("")) {
                            addAll(Arrays.asList(servers.split(",")));
                        }
                    }};
                    DNSQuery dnsQuery = new DNSQuery();
                    for (String dnsServer : dnsServers) {
                        testText = testServer(dnsQuery, dnsServer, testDomain, testText);
                    }
                    mHandler.obtainMessage(DnsTestHandler.MSG_TEST_DONE).sendToTarget();
                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                }
            }

            private StringBuilder testServer(DNSQuery dnsQuery, String dnsServer, String testUrl, StringBuilder testText) {
                Log.d(TAG, "Testing DNS " + dnsServer);
                testText.append(getResources().getString(R.string.test_domain)).append(" ").append(testUrl).append("\n").append(getResources().getString(R.string.test_dns_server)).append(" ").append(dnsServer);

                mHandler.obtainMessage(DnsTestHandler.MSG_DISPLAY_STATUS, testText.toString()).sendToTarget();

                Question question = new Question(testUrl, Record.TYPE.getType(A.class));
                DNSMessage.Builder message = DNSMessage.builder();
                message.setQuestion(question);
                message.setId((new Random()).nextInt());
                message.setRecursionDesired(true);
                message.getEdnsBuilder().setUdpPayloadSize(1024).setDnssecOk(false);

                try {
                    long startTime = System.currentTimeMillis();
                    DNSMessage responseMessage = dnsQuery.query(message.build(), InetAddressUtil.ipv4From(dnsServer), 53);//Auto forward ports
                    long endTime = System.currentTimeMillis();

                    Set<A> answers = responseMessage.getAnswersFor(question);
                    if (answers != null && answers.size() > 0) {
                        for (A a : answers) {
                            InetAddress inetAddress = a.getInetAddress();
                            testText.append("\n").append(getResources().getString(R.string.test_result_resolved)).append(" ").append(inetAddress.getHostAddress());
                        }
                        testText.append("\n").append(getResources().getString(R.string.test_time_used)).append(" ").
                                append(String.valueOf(endTime - startTime)).append(" ms\n\n");
                    } else {
                        testText.append("\n").append(getResources().getString(R.string.test_failed)).append("\n\n");
                    }
                } catch (Exception e) {
                    testText.append("\n").append(getResources().getString(R.string.test_failed)).append("\n\n");

                    Log.e(TAG, e.toString());
                }

                mHandler.obtainMessage(DnsTestHandler.MSG_DISPLAY_STATUS, testText.toString()).sendToTarget();
                return testText;
            }
        };

        final Button startTestBut = (Button) view.findViewById(R.id.button_start_test);
        startTestBut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Snackbar.make(v, R.string.notice_start_test, Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                startTestBut.setVisibility(View.INVISIBLE);

                InputMethodManager imm = (InputMethodManager) Daedalus.getInstance().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);

                textViewTestInfo.setText("");

                if (mThread == null) {
                    mThread = new Thread(mRunnable);
                    mThread.start();
                }
            }
        });


        mHandler = new DnsTestHandler();
        mHandler.setViews(startTestBut, textViewTestInfo);

        return view;
    }

    @Override
    public void checkStatus() {
        menu.findItem(R.id.nav_dns_test).setChecked(true);
        toolbar.setTitle(R.string.action_dns_test);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        stopThread();
        mHandler.removeCallbacks(mRunnable);
        mRunnable = null;
        mHandler.shutdown();
        mHandler = null;
    }

    private static void stopThread() {
        try {
            if (mThread != null) {
                mThread.interrupt();
                mThread = null;
            }
        } catch (Exception ignored) {
        }
    }

    private static class DnsTestHandler extends Handler {
        static final int MSG_DISPLAY_STATUS = 0;
        static final int MSG_TEST_DONE = 1;

        private Button startTestBtn = null;
        private TextView textViewTestInfo = null;

        void setViews(Button startTestButton, TextView textViewTestInfo) {
            this.startTestBtn = startTestButton;
            this.textViewTestInfo = textViewTestInfo;
        }

        void shutdown() {
            startTestBtn = null;
            textViewTestInfo = null;
        }

        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            switch (msg.what) {
                case MSG_DISPLAY_STATUS:
                    if (MainActivity.getInstance().getCurrentFragment() instanceof DnsTestFragment) {
                        textViewTestInfo.setText((String) msg.obj);
                    }
                    break;
                case MSG_TEST_DONE:
                    if (MainActivity.getInstance().getCurrentFragment() instanceof DnsTestFragment) {
                        startTestBtn.setVisibility(View.VISIBLE);
                    }
                    stopThread();
                    break;
            }
        }
    }

    private class DNSQuery extends NetworkDataSource {
        public DNSMessage query(DNSMessage message, InetAddress address, int port) throws IOException {
            return queryUdp(message, address, port);
        }
    }
}
