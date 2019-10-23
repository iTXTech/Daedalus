package org.itxtech.daedalus.fragment;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.R;
import org.itxtech.daedalus.util.Logger;
import org.itxtech.daedalus.server.AbstractDnsServer;
import org.itxtech.daedalus.server.DnsServerHelper;
import org.minidns.dnsmessage.DnsMessage;
import org.minidns.dnsmessage.Question;
import org.minidns.record.Record;
import org.minidns.source.NetworkDataSource;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
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
public class DnsTestFragment extends ToolbarFragment {
    private class Type {
        private Record.TYPE type;
        private String name;

        private Type(String name, Record.TYPE type) {
            this.name = name;
            this.type = type;
        }

        private Record.TYPE getType() {
            return type;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static Thread mThread = null;
    private static Runnable mRunnable = null;
    private DnsTestHandler mHandler = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dns_test, container, false);

        final TextView textViewTestInfo = view.findViewById(R.id.textView_test_info);

        final Spinner spinnerServerChoice = view.findViewById(R.id.spinner_server_choice);
        ArrayAdapter spinnerArrayAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, DnsServerHelper.getAllServers());
        spinnerServerChoice.setAdapter(spinnerArrayAdapter);
        spinnerServerChoice.setSelection(DnsServerHelper.getPosition(DnsServerHelper.getPrimary()));

        ArrayList<Type> types = new ArrayList<Type>() {{
            add(new Type("A", Record.TYPE.A));
            add(new Type("NS", Record.TYPE.NS));
            add(new Type("CNAME", Record.TYPE.CNAME));
            add(new Type("SOA", Record.TYPE.SOA));
            add(new Type("PTR", Record.TYPE.PTR));
            add(new Type("MX", Record.TYPE.MX));
            add(new Type("TXT", Record.TYPE.TXT));
            add(new Type("AAAA", Record.TYPE.AAAA));
            add(new Type("SRV", Record.TYPE.SRV));
            add(new Type("OPT", Record.TYPE.OPT));
            add(new Type("DS", Record.TYPE.DS));
            add(new Type("RRSIG", Record.TYPE.RRSIG));
            add(new Type("NSEC", Record.TYPE.NSEC));
            add(new Type("DNSKEY", Record.TYPE.DNSKEY));
            add(new Type("NSEC3", Record.TYPE.NSEC3));
            add(new Type("NSEC3PARAM", Record.TYPE.NSEC3PARAM));
            add(new Type("TLSA", Record.TYPE.TLSA));
            add(new Type("OPENPGPKEY", Record.TYPE.OPENPGPKEY));
            add(new Type("DLV", Record.TYPE.DLV));
        }};

        final Spinner spinnerType = view.findViewById(R.id.spinner_type);
        ArrayAdapter<Type> typeAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, types);
        spinnerType.setAdapter(typeAdapter);

        final AutoCompleteTextView textViewTestDomain = view.findViewById(R.id.autoCompleteTextView_test_url);
        ArrayAdapter autoCompleteArrayAdapter = new ArrayAdapter<>(Daedalus.getInstance(), android.R.layout.simple_list_item_1, Daedalus.DEFAULT_TEST_DOMAINS);
        textViewTestDomain.setAdapter(autoCompleteArrayAdapter);

        mRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    String testDomain = textViewTestDomain.getText().toString();
                    if (testDomain.equals("")) {
                        testDomain = Daedalus.DEFAULT_TEST_DOMAINS[0];
                    }
                    StringBuilder testText = new StringBuilder();
                    ArrayList<AbstractDnsServer> dnsServers = new ArrayList<AbstractDnsServer>() {{
                        add(((AbstractDnsServer) spinnerServerChoice.getSelectedItem()));
                        String servers = Daedalus.getPrefs().getString("dns_test_servers", "");
                        if (!servers.equals("")) {
                            for (String server : servers.split(",")) {
                                if (server.contains(".") && server.contains(":")) {//IPv4
                                    String[] pieces = servers.split(":");
                                    int port = AbstractDnsServer.DNS_SERVER_DEFAULT_PORT;
                                    try {
                                        port = Integer.parseInt(pieces[1]);
                                    } catch (Exception e) {
                                        Logger.logException(e);
                                    }
                                    add(new AbstractDnsServer(pieces[0], port));
                                } else if (!server.contains(".") && server.contains("|")) {//IPv6
                                    String[] pieces = servers.split("\\|");
                                    int port = AbstractDnsServer.DNS_SERVER_DEFAULT_PORT;
                                    try {
                                        port = Integer.parseInt(pieces[1]);
                                    } catch (Exception e) {
                                        Logger.logException(e);
                                    }
                                    add(new AbstractDnsServer(pieces[0], port));
                                } else {
                                    add(new AbstractDnsServer(server, AbstractDnsServer.DNS_SERVER_DEFAULT_PORT));
                                }
                            }
                        }
                    }};
                    DnsQuery dnsQuery = new DnsQuery();
                    Record.TYPE type = ((Type) spinnerType.getSelectedItem()).getType();
                    for (AbstractDnsServer dnsServer : dnsServers) {
                        testText = testServer(dnsQuery, type, dnsServer, testDomain, testText);
                    }
                    mHandler.obtainMessage(DnsTestHandler.MSG_TEST_DONE).sendToTarget();
                } catch (IllegalStateException ignored) {
                } catch (Exception e) {
                    Logger.logException(e);
                }
            }

            private StringBuilder testServer(DnsQuery dnsQuery, Record.TYPE type, AbstractDnsServer server, String domain, StringBuilder testText) {
                Logger.debug("Testing DNS server " + server.getRealName());
                testText.append(getString(R.string.test_domain)).append(" ").append(domain).append("\n")
                        .append(getString(R.string.test_dns_server)).append(" ").append(server.getRealName());

                mHandler.obtainMessage(DnsTestHandler.MSG_DISPLAY_STATUS, testText.toString()).sendToTarget();

                boolean succ = false;
                if (!server.isHttpsServer()) {//TODO: DoH Server Test
                    try {
                        DnsMessage.Builder message = DnsMessage.builder()
                                .addQuestion(new Question(domain, type))
                                .setId((new Random()).nextInt())
                                .setRecursionDesired(true)
                                .setOpcode(DnsMessage.OPCODE.QUERY)
                                .setResponseCode(DnsMessage.RESPONSE_CODE.NO_ERROR)
                                .setQrFlag(false);

                        long startTime = System.currentTimeMillis();
                        DnsMessage response = dnsQuery.query(message.build(), InetAddress.getByName(server.getAddress()), server.getPort());
                        long endTime = System.currentTimeMillis();

                        if (response.answerSection.size() > 0) {
                            for (Record record : response.answerSection) {
                                if (record.getPayload().getType() == type) {
                                    testText.append("\n").append(getString(R.string.test_result_resolved)).append(" ").append(record.getPayload().toString());
                                }
                            }
                            testText.append("\n").append(getString(R.string.test_time_used)).append(" ").
                                    append(endTime - startTime).append(" ms");
                            succ = true;
                        }
                    } catch (SocketTimeoutException ignored) {
                    } catch (Exception e) {
                        Logger.logException(e);
                    }
                }

                if (!succ){
                    testText.append("\n").append(getString(R.string.test_failed));
                }
                testText.append("\n\n");

                mHandler.obtainMessage(DnsTestHandler.MSG_DISPLAY_STATUS, testText.toString()).sendToTarget();
                return testText;
            }
        };

        final Button startTestBut = view.findViewById(R.id.button_start_test);
        startTestBut.setOnClickListener(v -> {
            startTestBut.setEnabled(false);
            InputMethodManager imm = (InputMethodManager) Daedalus.getInstance().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            textViewTestInfo.setText("");

            if (mThread == null) {
                mThread = new Thread(mRunnable);
                mThread.start();
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

            if (startTestBtn == null) {
                return;
            }

            switch (msg.what) {
                case MSG_DISPLAY_STATUS:
                    textViewTestInfo.setText((String) msg.obj);
                    break;
                case MSG_TEST_DONE:
                    startTestBtn.setEnabled(true);
                    stopThread();
                    break;
            }
        }
    }

    private class DnsQuery extends NetworkDataSource {
        public DnsMessage query(DnsMessage message, InetAddress address, int port) throws IOException {
            return queryUdp(message, address, port);
        }
    }
}
