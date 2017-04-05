package org.itxtech.daedalus;

import android.os.Handler;
import android.os.Message;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import de.measite.minidns.DNSClient;
import de.measite.minidns.DNSMessage;
import de.measite.minidns.Question;
import de.measite.minidns.Record;
import de.measite.minidns.record.A;
import de.measite.minidns.util.InetAddressUtil;

import java.net.InetAddress;
import java.util.*;

public class ServerTestActivity extends AppCompatActivity {
    private static final int MSG_DISPLAY_STATUS = 0;
    private static final int MSG_TEST_DONE = 1;

    private boolean testing = false;
    private Thread mThread = null;
    private Handler mHandler = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server_test);

        final TextView textViewTestInfo = (TextView) findViewById(R.id.textView_test_info);

        final Spinner spinnerServerChoice = (Spinner) findViewById(R.id.spinner_server_choice);

        final Button startTestBut = (Button) findViewById(R.id.button_start_test);
        startTestBut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Snackbar.make(v, R.string.start_test, Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                startTestBut.setVisibility(View.INVISIBLE);

                textViewTestInfo.setText("");

                if (mThread == null) {
                    Runnable runnable = new Runnable() {
                        @Override
                        public void run() {
                            try {
                                String testUrl = "www.google.com";
                                String testText = "";
                                String[] dnsServers = {DnsServers.getDnsServerAddress(String.valueOf(spinnerServerChoice.getSelectedItemId())), "114.114.114.114", "8.8.8.8"};
                                DNSClient client = new DNSClient(null);
                                for (String dnsServer : dnsServers) {
                                    testText = testServer(client, dnsServer, testUrl, testText);
                                }
                                mHandler.obtainMessage(MSG_TEST_DONE).sendToTarget();
                            } catch (Exception e) {
                                Log.e("DVpn", e.toString());
                            }
                        }

                        private String testServer(DNSClient client, String dnsServer, String testUrl, String testText) {
                            Log.d("Dvpn", "Testing DNS " + dnsServer);
                            testText += getResources().getString(R.string.test_server_address) + " " + testUrl + "\n"
                                    + getResources().getString(R.string.test_dns_server) + " " + dnsServer;

                            mHandler.obtainMessage(MSG_DISPLAY_STATUS, testText).sendToTarget();

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
                                    testText += "\n" + getResources().getString(R.string.test_resolved_address) + " " + inetAddress.getHostAddress();
                                }
                                testText += "\n" + getResources().getString(R.string.test_time_used) + " " + String.valueOf(endTime - startTime) + " ms\n\n";

                            } catch (Exception e) {
                                testText += "\n" + getResources().getString(R.string.test_failed) + "\n\n";

                                Log.e("DVpn", e.toString());
                            }

                            mHandler.obtainMessage(MSG_DISPLAY_STATUS, testText).sendToTarget();
                            return testText;
                        }
                    };
                    mThread = new Thread(runnable);
                    mThread.start();
                }
            }
        });


        mHandler = new Handler() {
            public void handleMessage(Message msg) {
                super.handleMessage(msg);

                switch (msg.what) {
                    case MSG_DISPLAY_STATUS:
                        textViewTestInfo.setText((String) msg.obj);
                        break;
                    case MSG_TEST_DONE:
                        testing = false;
                        startTestBut.setVisibility(View.VISIBLE);
                        mThread = null;
                        break;
                }
            }
        };
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
