package org.itxtech.daedalus.fragment;

import android.app.Fragment;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.Snackbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.R;
import org.itxtech.daedalus.util.HostsProvider;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;

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
public class HostsFragment extends Fragment {

    private Thread mThread = null;
    private View view = null;
    private HostsHandler mHandler = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_hosts, container, false);

        mHandler = new HostsHandler().setView(view);

        final Spinner spinnerHosts = (Spinner) view.findViewById(R.id.spinner_hosts);
        ArrayAdapter spinnerArrayAdapter = new ArrayAdapter<>(Daedalus.getInstance(), android.R.layout.simple_list_item_1, HostsProvider.getHostsProviderNames());
        spinnerHosts.setAdapter(spinnerArrayAdapter);
        spinnerHosts.setSelection(0);

        Button buttonDownload = (Button) view.findViewById(R.id.button_download_hosts);
        buttonDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mThread == null) {
                    Snackbar.make(view, R.string.notice_start_download, Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                    mThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                URLConnection connection = new URL(HostsProvider.getDownloadUrlByName(spinnerHosts.getSelectedItem().toString())).openConnection();
                                InputStream inputStream = connection.getInputStream();
                                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                                StringBuilder builder = new StringBuilder();
                                String result;
                                while ((result = reader.readLine()) != null) {
                                    builder.append("\n").append(result);
                                }
                                reader.close();

                                mHandler.obtainMessage(HostsHandler.MSG_DOWNLOADED, builder.toString()).sendToTarget();
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

    private static class HostsHandler extends Handler {
        static final int MSG_DOWNLOADED = 0;

        private View view = null;

        HostsHandler setView(View view) {
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
                case MSG_DOWNLOADED:
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
                    break;
            }
        }
    }
}
