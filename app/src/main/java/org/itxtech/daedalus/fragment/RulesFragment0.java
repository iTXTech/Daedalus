package org.itxtech.daedalus.fragment;

import android.app.Fragment;

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
public class RulesFragment0 extends Fragment {
/*
    private Thread mThread = null;
    private View view = null;
    private RulesHandler mHandler = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_rules, container, false);

        mHandler = new RulesHandler().setView(view).setHostsFragment(this);

        final Spinner spinnerHosts = (Spinner) view.findViewById(R.id.spinner_hosts);
        ArrayAdapter hostsArrayAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, RulesProvider.getHostsProviderNames());
        spinnerHosts.setAdapter(hostsArrayAdapter);
        spinnerHosts.setSelection(0);

        final Spinner spinnerDnsmasq = (Spinner) view.findViewById(R.id.spinner_dnsmasq);
        ArrayAdapter dnsmasqArrayAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, RulesProvider.getDnsmasqProviderNames());
        spinnerDnsmasq.setAdapter(dnsmasqArrayAdapter);
        spinnerDnsmasq.setSelection(0);

        Button buttonDownloadHosts = (Button) view.findViewById(R.id.button_download_hosts);
        buttonDownloadHosts.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mThread == null) {
                    Snackbar.make(view, R.string.notice_start_download, Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                    mThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                URLConnection connection = new URL(RulesProvider.getDownloadUrlByName(spinnerHosts.getSelectedItem().toString())).openConnection();
                                InputStream inputStream = connection.getInputStream();
                                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                                StringBuilder builder = new StringBuilder();
                                String result;
                                while ((result = reader.readLine()) != null) {
                                    builder.append("\n").append(result);
                                }
                                reader.close();

                                mHandler.obtainMessage(RulesHandler.MSG_HOSTS_DOWNLOADED, builder.toString()).sendToTarget();
                                stopThread();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });
                    mThread.start();
                } else {
                    Snackbar.make(view, R.string.notice_now_downloading, Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                }
            }
        });

        Button buttonDownloadDnsmasq = (Button) view.findViewById(R.id.button_download_dnsmasq);
        buttonDownloadDnsmasq.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mThread == null) {
                    Snackbar.make(view, R.string.notice_start_download, Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                    mThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                RulesProvider provider = RulesProvider.getProviderByName(spinnerDnsmasq.getSelectedItem().toString());
                                URLConnection connection = new URL(provider.getDownloadURL()).openConnection();
                                InputStream inputStream = connection.getInputStream();
                                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                                StringBuilder builder = new StringBuilder();
                                String result;
                                while ((result = reader.readLine()) != null) {
                                    builder.append("\n").append(result);
                                }
                                reader.close();

                                provider.setData(builder.toString());
                                mHandler.obtainMessage(RulesHandler.MSG_DNSMASQ_DOWNLOADED, provider).sendToTarget();
                                stopThread();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });
                    mThread.start();
                } else {
                    Snackbar.make(view, R.string.notice_now_downloading, Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                }
            }
        });
        return view;
    }

    private void updateUserInterface() {
        File hosts = new File(Daedalus.hostsPath);
        TextView info = (TextView) view.findViewById(R.id.textView_hosts);
        StringBuilder builder = new StringBuilder();
        builder.append(getString(R.string.hosts_path)).append(" ").append(Daedalus.hostsPath).append("\n");
        if (!hosts.exists()) {
            builder.append(getString(R.string.hosts_not_found));
        } else {
            builder.append(getString(R.string.hosts_last_modified)).append(" ").append(new Date(hosts.lastModified()).toString()).append("\n")
                    .append(getString(R.string.hosts_size)).append(" ").append(new DecimalFormat("0.00").format(((float) hosts.length() / 1024))).append(" KB");
        }
        builder.append("\n");
        File dnsmasq = new File(Daedalus.dnsmasqPath);
        builder.append(getString(R.string.dnsmasq_path)).append(" ").append(Daedalus.dnsmasqPath);
        if (dnsmasq.exists()) {
            for (File conf : dnsmasq.listFiles()) {
                builder.append("\n").append(conf.getName()).append(" ")
                        .append(new DecimalFormat("0.00").format(((float) conf.length() / 1024))).append(" KB");
            }
        }
        info.setText(builder.toString());
    }

    @Override
    public void onResume() {
        super.onResume();

        updateUserInterface();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        stopThread();
        mHandler.shutdown();
        mHandler = null;
        view = null;
    }

    private void stopThread() {
        if (mThread != null) {
            mThread.interrupt();
            mThread = null;
        }
    }

    private static class RulesHandler extends Handler {
        static final int MSG_HOSTS_DOWNLOADED = 0;
        static final int MSG_DNSMASQ_DOWNLOADED = 1;

        private View view = null;
        private RulesFragment0 mFragment = null;

        RulesHandler setView(View view) {
            this.view = view;
            return this;
        }

        RulesHandler setHostsFragment(RulesFragment0 fragment) {
            mFragment = fragment;
            return this;
        }

        void shutdown() {
            view = null;
            mFragment = null;
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            switch (msg.what) {
                case MSG_HOSTS_DOWNLOADED:
                    try {
                        String result = (String) msg.obj;
                        File file = new File(Daedalus.hostsPath);
                        FileOutputStream stream = new FileOutputStream(file);
                        stream.write(result.getBytes());
                        stream.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    Snackbar.make(view, R.string.notice_downloaded, Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();

                    mFragment.updateUserInterface();
                    break;
                case MSG_DNSMASQ_DOWNLOADED:
                    try {
                        RulesProvider provider = (RulesProvider) msg.obj;
                        File file = new File(Daedalus.dnsmasqPath + provider.getFileName());
                        FileOutputStream stream = new FileOutputStream(file);
                        stream.write(provider.getData().getBytes());
                        stream.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    Snackbar.make(view, R.string.notice_downloaded, Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();

                    mFragment.updateUserInterface();
                    break;
            }
        }
    }*/
}
