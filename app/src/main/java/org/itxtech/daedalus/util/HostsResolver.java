package org.itxtech.daedalus.util;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.HashMap;

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
public class HostsResolver implements Runnable {
    private static final String TAG = "DHostsResolver";

    public static final int STATUS_LOADED = 0;
    public static final int STATUS_LOADING = 1;
    public static final int STATUS_NOT_LOADED = 2;
    public static final int STATUS_PENDING_LOAD = 3;

    private static int status = STATUS_NOT_LOADED;
    private static String fileName;
    private static HashMap<String, String> hosts;
    private static boolean shutdown = false;
    private static boolean panResolution = false;

    public HostsResolver() {
        status = STATUS_NOT_LOADED;
        fileName = "";
        shutdown = false;
    }

    public static void shutdown() {
        shutdown = true;
    }

    public static boolean isLoaded() {
        return status == STATUS_LOADED;
    }

    public static void startLoad(String loadFile) {
        Log.d(TAG, "Loading file " + loadFile);
        fileName = loadFile;
        status = STATUS_PENDING_LOAD;
    }

    public static void setPanResolution(boolean resolution) {
        panResolution = resolution;
    }

    public static void clean() {
        hosts = null;
    }

    public static boolean canResolve(String hostname) {
        if (hosts == null) {
            return false;
        }
        if (hosts.containsKey(hostname)) {
            return true;
        }
        if (panResolution) {
            String[] pieces = hostname.split("\\.");
            StringBuilder builder;
            builder = new StringBuilder();
            builder.append("*");
            for (int i = 1; i < pieces.length; i++) {
                builder.append(".").append(pieces[i]);
            }
            if (hosts.containsKey(builder.toString())) {
                return true;
            }
        }
        return false;
    }

    public static String resolve(String hostname) {
        if (hosts == null) {
            return "";
        }
        if (hosts.containsKey(hostname)) {
            return hosts.get(hostname);
        }
        if (panResolution) {
            String[] pieces = hostname.split("\\.");
            StringBuilder builder;
            builder = new StringBuilder();
            builder.append("*");
            for (int i = 1; i < pieces.length; i++) {
                builder.append(".").append(pieces[i]);
            }
            if (hosts.containsKey(builder.toString())) {
                return hosts.get(builder.toString());
            }
        }
        return "";
    }

    private void load() {
        try {
            status = STATUS_LOADING;
            File file = new File(fileName);
            if (file.exists() && file.canRead()) {
                FileInputStream stream = new FileInputStream(file);
                BufferedReader dataIO = new BufferedReader(new InputStreamReader(stream));
                hosts = new HashMap<>();
                String strLine;
                String[] data;
                while ((strLine = dataIO.readLine()) != null) {
                    //Log.d(TAG, strLine);
                    if (!strLine.equals("") && !strLine.startsWith("#")) {
                        data = strLine.split("\\s+");
                        hosts.put(data[1], data[0]);
                        Log.d(TAG, "Putting " + data[0] + " " + data[1]);
                    }
                }

                dataIO.close();
                stream.close();
                status = STATUS_LOADED;
            } else {
                status = STATUS_NOT_LOADED;
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    @Override
    public void run() {
        try {
            while (!shutdown) {
                if (status == STATUS_PENDING_LOAD) {
                    load();
                }
                Thread.sleep(100);
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }
}
