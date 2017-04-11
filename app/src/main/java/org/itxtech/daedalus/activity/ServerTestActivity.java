package org.itxtech.daedalus.activity;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import de.measite.minidns.DNSClient;
import de.measite.minidns.DNSMessage;
import de.measite.minidns.Question;
import de.measite.minidns.Record;
import de.measite.minidns.record.A;
import de.measite.minidns.util.InetAddressUtil;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.R;
import org.itxtech.daedalus.util.DnsServer;

import java.net.InetAddress;
import java.util.Random;
import java.util.Set;

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
public class ServerTestActivity extends AppCompatActivity {
    private static final int MSG_DISPLAY_STATUS = 0;
    private static final int MSG_TEST_DONE = 1;

    private static boolean testing = false;
    private static Thread mThread = null;
    private ServerTestHandler mHandler = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server_test);

        final TextView textViewTestInfo = (TextView) findViewById(R.id.textView_test_info);

        final Spinner spinnerServerChoice = (Spinner) findViewById(R.id.spinner_server_choice);
        ArrayAdapter spinnerArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, DnsServer.getDnsServerNames(this));
        spinnerServerChoice.setAdapter(spinnerArrayAdapter);

        final AutoCompleteTextView textViewTestUrl = (AutoCompleteTextView) findViewById(R.id.autoCompleteTextView_test_url);
        ArrayAdapter autoCompleteArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, Daedalus.DEFAULT_TEST_DOMAINS);
        textViewTestUrl.setAdapter(autoCompleteArrayAdapter);

        final Context context = this;

        final Button startTestBut = (Button) findViewById(R.id.button_start_test);
        startTestBut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Snackbar.make(v, R.string.notice_start_test, Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                startTestBut.setVisibility(View.INVISIBLE);

                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);

                textViewTestInfo.setText("");

                if (mThread == null) {
                    Runnable runnable = new Runnable() {
                        @Override
                        public void run() {
                            try {
                                String testDomain = textViewTestUrl.getText().toString();
                                if (testDomain.equals("")) {
                                    testDomain = Daedalus.DEFAULT_TEST_DOMAINS[0];
                                }
                                StringBuilder testText = new StringBuilder();
                                String[] dnsServers = {DnsServer.getDnsServerAddressByStringDescription(context, spinnerServerChoice.getSelectedItem().toString()), "114.114.114.114", "8.8.8.8"};
                                DNSClient client = new DNSClient(null);
                                for (String dnsServer : dnsServers) {
                                    testText = testServer(client, dnsServer, testDomain, testText);
                                }
                                mHandler.obtainMessage(MSG_TEST_DONE).sendToTarget();
                            } catch (Exception e) {
                                Log.e("DVpn", e.toString());
                            }
                        }

                        private StringBuilder testServer(DNSClient client, String dnsServer, String testUrl, StringBuilder testText) {
                            Log.d("Dvpn", "Testing DNS " + dnsServer);
                            testText.append(getResources().getString(R.string.test_domain)).append(" ").append(testUrl).append("\n"
                            ).append(getResources().getString(R.string.test_dns_server)).append(" ").append(dnsServer);

                            mHandler.obtainMessage(MSG_DISPLAY_STATUS, testText.toString()).sendToTarget();

                            Question question = new Question(testUrl, Record.TYPE.getType(A.class));
                            DNSMessage.Builder message = DNSMessage.builder();
                            message.setQuestion(question);
                            message.setId((new Random()).nextInt());
                            message.setRecursionDesired(true);
                            message.getEdnsBuilder().setUdpPayloadSize(1024).setDnssecOk(false);

                            try {
                                long startTime = System.currentTimeMillis();
                                DNSMessage responseMessage = client.query(message.build(), InetAddressUtil.ipv4From(dnsServer));
                                long endTime = System.currentTimeMillis();

                                Set<A> answers = responseMessage.getAnswersFor(question);
                                for (A a : answers) {
                                    InetAddress inetAddress = a.getInetAddress();
                                    testText.append("\n").append(getResources().getString(R.string.test_result_resolved)).append(" ").append(inetAddress.getHostAddress());
                                }
                                testText.append("\n").append(getResources().getString(R.string.test_time_used)).append(" ").append(String.valueOf(endTime - startTime)).append(" ms\n\n");

                            } catch (Exception e) {
                                testText.append("\n").append(getResources().getString(R.string.test_failed)).append("\n\n");

                                Log.e("DVpn", e.toString());
                            }

                            mHandler.obtainMessage(MSG_DISPLAY_STATUS, testText.toString()).sendToTarget();
                            return testText;
                        }
                    };
                    mThread = new Thread(runnable);
                    mThread.start();
                }
            }
        });


        mHandler = new ServerTestHandler();
        mHandler.setViews(startTestBut, textViewTestInfo);
    }

    public static class ServerTestHandler extends Handler {
        private Button startTestBtn = null;
        private TextView textViewTestInfo = null;

        void setViews(Button startTestButton, TextView textViewTestInfo) {
            this.startTestBtn = startTestButton;
            this.textViewTestInfo = textViewTestInfo;
        }

        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            switch (msg.what) {
                case MSG_DISPLAY_STATUS:
                    textViewTestInfo.setText((String) msg.obj);
                    break;
                case MSG_TEST_DONE:
                    testing = false;
                    startTestBtn.setVisibility(View.VISIBLE);
                    mThread = null;
                    break;
            }
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (testing) {
            final Button startTestBut = (Button) findViewById(R.id.button_start_test);
            startTestBut.setVisibility(View.INVISIBLE);
        }
    }
}
