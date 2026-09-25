package org.itxtech.daedalus.fragment;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.R;
import org.itxtech.daedalus.provider.DnsTransport;
import org.itxtech.daedalus.server.AbstractDnsServer;
import org.itxtech.daedalus.server.DnsServerHelper;
import org.itxtech.daedalus.util.Logger;
import org.minidns.dnsmessage.DnsMessage;
import org.minidns.dnsmessage.Question;
import org.minidns.record.Data;
import org.minidns.record.Record;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
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
    private static class Type {
        private final Record.TYPE type;
        private final String name;

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

    private volatile DnsTestHandler mHandler = null;
    private Thread mThread = null;
    private DnsQuery mQuery = null;

    private Button mStartButton = null;
    private Button mStopButton = null;
    private TextView mTestInfo = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dns_test, container, false);

        mTestInfo = view.findViewById(R.id.textView_test_info);
        mStartButton = view.findViewById(R.id.button_start_test);
        mStopButton = view.findViewById(R.id.button_stop_test);

        final Spinner spinnerServerChoice = view.findViewById(R.id.spinner_server_choice);
        ArrayAdapter<AbstractDnsServer> spinnerArrayAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, DnsServerHelper.getAllServers());
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
        ArrayAdapter<String> autoCompleteArrayAdapter = new ArrayAdapter<>(Daedalus.getInstance(), android.R.layout.simple_list_item_1, Daedalus.DEFAULT_TEST_DOMAINS);
        textViewTestDomain.setAdapter(autoCompleteArrayAdapter);

        mStartButton.setOnClickListener(v -> {
            if (mThread != null) {
                return;
            }
            InputMethodManager imm = (InputMethodManager) Daedalus.getInstance().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            mTestInfo.setText("");

            String domain = textViewTestDomain.getText().toString().trim();
            if (domain.isEmpty()) {
                domain = Daedalus.DEFAULT_TEST_DOMAINS[0];
            }
            Record.TYPE type = ((Type) spinnerType.getSelectedItem()).getType();
            ArrayList<AbstractDnsServer> servers = new ArrayList<>();
            servers.add((AbstractDnsServer) spinnerServerChoice.getSelectedItem());
            servers.addAll(getExtraTestServers());

            startTest(domain, type, servers);
        });
        mStopButton.setOnClickListener(v -> stopTest());
        mStopButton.setEnabled(false);

        mHandler = new DnsTestHandler(this);

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

        DnsQuery query = mQuery;
        if (query != null) {
            query.cancel();
        }
        mQuery = null;
        mThread = null;

        DnsTestHandler handler = mHandler;
        mHandler = null;
        if (handler != null) {
            handler.shutdown();
        }
        mStartButton = null;
        mStopButton = null;
        mTestInfo = null;
    }

    private void startTest(String domain, Record.TYPE type, List<AbstractDnsServer> servers) {
        mStartButton.setEnabled(false);
        mStopButton.setEnabled(true);

        final DnsQuery query = new DnsQuery();
        mQuery = query;
        mThread = new Thread(() -> {
            StringBuilder text = new StringBuilder();
            try {
                for (AbstractDnsServer server : servers) {
                    if (query.isCancelled()) {
                        break;
                    }
                    testServer(query, type, server, domain, text);
                }
                if (query.isCancelled()) {
                    text.append(str(R.string.test_stopped));
                    post(DnsTestHandler.MSG_DISPLAY_STATUS, text.toString());
                }
            } catch (Exception e) {
                Logger.logException(e);
            } finally {
                post(DnsTestHandler.MSG_TEST_DONE, null);
            }
        }, "DnsTest");
        mThread.start();
    }

    /**
     * Stops the running test. The socket (or HTTP call) currently blocking the worker
     * thread is closed, so the thread fails fast instead of waiting for the timeout.
     */
    private void stopTest() {
        DnsQuery query = mQuery;
        if (query != null) {
            mStopButton.setEnabled(false);
            query.cancel();
        }
    }

    private void onTestFinished() {
        mThread = null;
        mQuery = null;
        if (mStartButton != null) {
            mStartButton.setEnabled(true);
        }
        if (mStopButton != null) {
            mStopButton.setEnabled(false);
        }
    }

    private void showStatus(String status) {
        if (mTestInfo != null) {
            mTestInfo.setText(status);
        }
    }

    private void post(int what, Object obj) {
        DnsTestHandler handler = mHandler;
        if (handler != null) {
            handler.obtainMessage(what, obj).sendToTarget();
        }
    }

    private static String str(int id) {
        return Daedalus.getInstance().getString(id);
    }

    /**
     * Parses the "dns_test_servers" preference. Entries are separated by commas and are
     * either "host:port" (IPv4 address or host name), "ipv6|port", or a bare address that
     * uses the default port.
     */
    private static List<AbstractDnsServer> getExtraTestServers() {
        ArrayList<AbstractDnsServer> servers = new ArrayList<>();
        String pref = Daedalus.getPrefs().getString("dns_test_servers", "");
        if (pref == null || pref.trim().isEmpty()) {
            return servers;
        }
        for (String entry : pref.split(",")) {
            String server = entry.trim();
            if (server.isEmpty()) {
                continue;
            }
            String separator = null;
            if (server.contains(".") && server.contains(":")) {
                separator = ":";
            } else if (!server.contains(".") && server.contains("|")) {
                separator = "\\|";
            }
            if (separator == null) {
                servers.add(new AbstractDnsServer(server, AbstractDnsServer.DNS_SERVER_DEFAULT_PORT));
                continue;
            }
            String[] pieces = server.split(separator);
            int port = AbstractDnsServer.DNS_SERVER_DEFAULT_PORT;
            if (pieces.length > 1) {
                try {
                    port = Integer.parseInt(pieces[1].trim());
                } catch (NumberFormatException e) {
                    Logger.logException(e);
                }
            }
            servers.add(new AbstractDnsServer(pieces[0].trim(), port));
        }
        return servers;
    }

    private void testServer(DnsQuery query, Record.TYPE type, AbstractDnsServer server, String domain, StringBuilder text) {
        Logger.debug("Testing DNS server " + server.getRealName());
        text.append(str(R.string.test_domain)).append(" ").append(domain).append("\n")
                .append(str(R.string.test_dns_server)).append(" ").append(DnsTransport.describe(server));
        post(DnsTestHandler.MSG_DISPLAY_STATUS, text.toString());

        boolean succ = false;
        try {
            DnsMessage message = DnsMessage.builder()
                    .addQuestion(new Question(domain, type))
                    .setId(new Random().nextInt())
                    .setRecursionDesired(true)
                    .setOpcode(DnsMessage.OPCODE.QUERY)
                    .setResponseCode(DnsMessage.RESPONSE_CODE.NO_ERROR)
                    .setQrFlag(false)
                    .build();

            long startTime = SystemClock.elapsedRealtime();
            DnsMessage response = query.query(message, server);
            long timeUsed = SystemClock.elapsedRealtime() - startTime;

            for (Record<? extends Data> record : response.answerSection) {
                if (record.type == type) {
                    text.append("\n").append(str(R.string.test_result_resolved)).append(" ").append(record.getPayload().toString());
                }
            }
            if (response.answerSection.size() > 0) {
                succ = true;
            } else if (response.responseCode != DnsMessage.RESPONSE_CODE.NO_ERROR) {
                text.append("\n").append(str(R.string.test_error)).append(" ").append(response.responseCode);
            }
            text.append("\n").append(str(R.string.test_time_used)).append(" ").append(timeUsed).append(" ms");
        } catch (Exception e) {
            if (!query.isCancelled()) {
                Logger.logException(e);
                if (e instanceof SocketTimeoutException) {
                    text.append("\n").append(str(R.string.test_timeout));
                } else {
                    String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    text.append("\n").append(str(R.string.test_error)).append(" ").append(reason);
                    if (server.isProxied() && (e instanceof SocketException || e instanceof EOFException)) {
                        // The proxy accepted the connection but the target closed it, or the proxy
                        // refused the connection: the server most likely does not listen on TCP
                        text.append("\n").append(Daedalus.getInstance().getString(R.string.test_proxy_tcp_hint, server.getPort()));
                    }
                }
            }
        }

        if (!query.isCancelled() && !succ) {
            text.append("\n").append(str(R.string.test_failed));
        }
        text.append("\n\n");
        post(DnsTestHandler.MSG_DISPLAY_STATUS, text.toString());
    }

    private static class DnsTestHandler extends Handler {
        static final int MSG_DISPLAY_STATUS = 0;
        static final int MSG_TEST_DONE = 1;

        private DnsTestFragment fragment;

        DnsTestHandler(DnsTestFragment fragment) {
            super(Looper.getMainLooper());
            this.fragment = fragment;
        }

        void shutdown() {
            fragment = null;
        }

        @Override
        public void handleMessage(Message msg) {
            DnsTestFragment fragment = this.fragment;
            if (fragment == null) {
                return;
            }
            switch (msg.what) {
                case MSG_DISPLAY_STATUS:
                    fragment.showStatus((String) msg.obj);
                    break;
                case MSG_TEST_DONE:
                    fragment.onTestFinished();
                    break;
            }
        }
    }

    /**
     * Runs the queries of one test through {@link DnsTransport}, exactly like the VPN
     * does, and tracks the blocking network object so that {@link #cancel()} can abort the
     * query from another thread.
     */
    private static class DnsQuery implements DnsTransport.Hooks {
        private static final int TIMEOUT = 5000;

        private volatile boolean cancelled = false;
        private volatile Closeable current = null;

        boolean isCancelled() {
            return cancelled;
        }

        /**
         * Aborts the query in progress. Closing the socket (or cancelling the HTTP call)
         * makes the blocked worker thread fail immediately with an exception.
         */
        void cancel() {
            cancelled = true;
            Closeable closeable = current;
            if (closeable != null) {
                try {
                    closeable.close();
                } catch (IOException ignored) {
                }
            }
        }

        @Override
        public void track(Closeable closeable) throws IOException {
            current = closeable;
            if (cancelled) {
                closeable.close();
                throw new IOException("Test stopped");
            }
        }

        DnsMessage query(DnsMessage message, AbstractDnsServer server) throws IOException, GeneralSecurityException {
            return DnsTransport.query(server, message, TIMEOUT, this).message;
        }
    }
}
