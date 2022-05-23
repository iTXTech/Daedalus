package org.itxtech.daedalus.util;

import android.content.Context;
import android.net.*;
import android.os.Build;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
public class DnsServersDetector {
    //https://stackoverflow.com/a/48973823
    private static final String METHOD_EXEC_PROP_DELIM = "]: [";

    public static String[] getServers(Context context) {
        String[] result;
        result = getServersMethodSystemProperties();
        if (result != null && result.length > 0) {
            return result;
        }
        result = getServersMethodConnectivityManager(context);
        if (result != null && result.length > 0) {
            return result;
        }
        result = getServersMethodExec();
        if (result != null && result.length > 0) {
            return result;
        }
        return null;
    }

    private static String[] getServersMethodConnectivityManager(Context context) {
        ArrayList<String> priorityServersArrayList = new ArrayList<>();
        ArrayList<String> serversArrayList = new ArrayList<>();
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            for (Network network : connectivityManager.getAllNetworks()) {
                NetworkInfo networkInfo = connectivityManager.getNetworkInfo(network);
                if (networkInfo.isConnected()) {
                    LinkProperties linkProperties = connectivityManager.getLinkProperties(network);
                    List<InetAddress> dnsServersList = linkProperties.getDnsServers();
                    if (linkPropertiesHasDefaultRoute(linkProperties)) {
                        for (InetAddress element : dnsServersList) {
                            String dnsHost = element.getHostAddress();
                            priorityServersArrayList.add(dnsHost);
                        }
                    } else {
                        for (InetAddress element : dnsServersList) {
                            String dnsHost = element.getHostAddress();
                            serversArrayList.add(dnsHost);
                        }
                    }
                }
            }
        }
        if (priorityServersArrayList.isEmpty()) {
            priorityServersArrayList.addAll(serversArrayList);
        }
        if (priorityServersArrayList.size() > 0) {
            return priorityServersArrayList.toArray(new String[0]);
        }
        return null;
    }

    private static String[] getServersMethodSystemProperties() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            String re1 = "^\\d+(\\.\\d+){3}$";
            String re2 = "^[0-9a-f]+(:[0-9a-f]*)+:[0-9a-f]+$";
            ArrayList<String> serversArrayList = new ArrayList<>();
            try {
                Class SystemProperties = Class.forName("android.os.SystemProperties");
                Method method = SystemProperties.getMethod("get", new Class[]{String.class});
                String[] netdns = new String[]{"net.dns1", "net.dns2", "net.dns3", "net.dns4"};
                for (String dns : netdns) {
                    Object[] args = new Object[]{dns};
                    String v = (String) method.invoke(null, args);
                    if (v != null && (v.matches(re1) || v.matches(re2)) && !serversArrayList.contains(v)) {
                        serversArrayList.add(v);
                    }
                }
                if (serversArrayList.size() > 0) {
                    return serversArrayList.toArray(new String[0]);
                }
            } catch (Exception ex) {
                Logger.logException(ex);
            }
        }
        return null;
    }

    private static String[] getServersMethodExec() {
        try {
            Process process = Runtime.getRuntime().exec("getprop");
            InputStream inputStream = process.getInputStream();
            LineNumberReader lineNumberReader = new LineNumberReader(new InputStreamReader(inputStream));
            Set<String> serversSet = methodExecParseProps(lineNumberReader);
            if (serversSet.size() > 0) {
                return serversSet.toArray(new String[0]);
            }
        } catch (Exception ex) {
            Logger.logException(ex);
        }
        return null;
    }

    private static Set<String> methodExecParseProps(BufferedReader lineNumberReader) throws Exception {
        String line;
        HashSet<String> serversSet = new HashSet<>();
        while ((line = lineNumberReader.readLine()) != null) {
            int split = line.indexOf(METHOD_EXEC_PROP_DELIM);
            if (split == -1) {
                continue;
            }
            String property = line.substring(1, split);
            int valueStart = split + METHOD_EXEC_PROP_DELIM.length();
            int valueEnd = line.length() - 1;
            if (valueEnd < valueStart) {
                continue;
            }
            String value = line.substring(valueStart, valueEnd);
            if (value.isEmpty()) {
                continue;
            }
            if (property.endsWith(".dns") || property.endsWith(".dns1") || property.endsWith(".dns2") ||
                    property.endsWith(".dns3") || property.endsWith(".dns4")) {
                InetAddress ip = InetAddress.getByName(value);
                if (ip == null) {
                    continue;
                }
                value = ip.getHostAddress();
                if (value == null || value.length() == 0) {
                    continue;
                }
                serversSet.add(value);
            }
        }
        return serversSet;
    }

    private static boolean linkPropertiesHasDefaultRoute(LinkProperties linkProperties) {
        for (RouteInfo route : linkProperties.getRoutes()) {
            if (route.isDefaultRoute()) {
                return true;
            }
        }
        return false;
    }
}
