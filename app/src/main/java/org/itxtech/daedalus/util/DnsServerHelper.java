package org.itxtech.daedalus.util;

import android.content.Context;
import org.itxtech.daedalus.Daedalus;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Daedalus Project
 *
 * @author iTXTech
 * @link https://itxtech.org
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
public class DnsServerHelper {
    private static HashMap<String, Integer> portCache = null;

    public static void cleanPortCache() {
        portCache = null;
    }

    public static void buildPortCache() {
        portCache = new HashMap<>();
        for (DnsServer server : Daedalus.DNS_SERVERS) {
            portCache.put(server.getAddress(), server.getPort());
        }

        for (CustomDnsServer server : Daedalus.configurations.getCustomDnsServers()) {
            portCache.put(server.getAddress(), server.getPort());
        }

    }

    public static int getPortOrDefault(InetAddress address, int defaultPort) {
        String hostAddress = address.getHostAddress();

        if (portCache.containsKey(hostAddress)) {
            return portCache.get(hostAddress);
        }

        return defaultPort;
    }

    public static int getPosition(String id) {
        int intId = Integer.parseInt(id);
        if (intId < Daedalus.DNS_SERVERS.size()) {
            return intId;
        }

        for (int i = 0; i < Daedalus.configurations.getCustomDnsServers().size(); i++) {
            if (Daedalus.configurations.getCustomDnsServers().get(i).getId().equals(id)) {
                return i + Daedalus.DNS_SERVERS.size();
            }
        }
        return 0;
    }

    public static String getPrimary() {
        return String.valueOf(DnsServerHelper.checkServerId(Integer.parseInt(Daedalus.getPrefs().getString("primary_server", "0"))));
    }

    public static String getSecondary() {
        return String.valueOf(DnsServerHelper.checkServerId(Integer.parseInt(Daedalus.getPrefs().getString("secondary_server", "1"))));
    }

    private static int checkServerId(int id) {
        if (id < (Daedalus.DNS_SERVERS.size() - 1)) {
            return id;
        }
        for (CustomDnsServer server : Daedalus.configurations.getCustomDnsServers()) {
            if (server.getId().equals(String.valueOf(id))) {
                return id;
            }
        }
        return 0;
    }

    public static String getAddressById(String id) {
        for (DnsServer server : Daedalus.DNS_SERVERS) {
            if (server.getId().equals(id)) {
                return server.getAddress();
            }
        }
        for (CustomDnsServer customDnsServer : Daedalus.configurations.getCustomDnsServers()) {
            if (customDnsServer.getId().equals(id)) {
                return customDnsServer.getAddress();
            }
        }
        return Daedalus.DNS_SERVERS.get(0).getAddress();
    }

    public static String getAddressByDescription(Context context, String description) {
        for (DnsServer server : Daedalus.DNS_SERVERS) {
            if (server.getStringDescription(context).equals(description)) {
                return server.getAddress();
            }
        }
        for (CustomDnsServer customDnsServer : Daedalus.configurations.getCustomDnsServers()) {
            if (customDnsServer.getName().equals(description)) {
                return customDnsServer.getAddress();
            }
        }
        return Daedalus.DNS_SERVERS.get(0).getAddress();
    }

    public static String[] getIds() {
        ArrayList<String> servers = new ArrayList<>(Daedalus.DNS_SERVERS.size());
        for (DnsServer server : Daedalus.DNS_SERVERS) {
            servers.add(server.getId());
        }
        for (CustomDnsServer customDnsServer : Daedalus.configurations.getCustomDnsServers()) {
            servers.add(customDnsServer.getId());
        }
        String[] stringServers = new String[Daedalus.DNS_SERVERS.size()];
        return servers.toArray(stringServers);
    }

    public static String[] getNames(Context context) {
        ArrayList<String> servers = new ArrayList<>(Daedalus.DNS_SERVERS.size());
        for (DnsServer server : Daedalus.DNS_SERVERS) {
            servers.add(server.getStringDescription(context));
        }
        for (CustomDnsServer customDnsServer : Daedalus.configurations.getCustomDnsServers()) {
            servers.add(customDnsServer.getName());
        }
        String[] stringServers = new String[Daedalus.DNS_SERVERS.size()];
        return servers.toArray(stringServers);
    }

    public static String getDescription(String id, Context context) {
        for (DnsServer server : Daedalus.DNS_SERVERS) {
            if (server.getId().equals(id)) {
                return server.getStringDescription(context);
            }
        }
        for (CustomDnsServer customDnsServer : Daedalus.configurations.getCustomDnsServers()) {
            if (customDnsServer.getId().equals(id)) {
                return customDnsServer.getName();
            }
        }
        return Daedalus.DNS_SERVERS.get(0).getStringDescription(context);
    }
}
