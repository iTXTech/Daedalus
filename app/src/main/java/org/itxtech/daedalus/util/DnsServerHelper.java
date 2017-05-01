package org.itxtech.daedalus.util;

import android.content.Context;
import org.itxtech.daedalus.Daedalus;

import java.util.ArrayList;

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
public class DnsServerHelper {
    public static String getPrimaryDnsServer() {
        return String.valueOf(DnsServerHelper.checkServerId(Integer.parseInt(Daedalus.getPrefs().getString("primary_server", "0"))));
    }

    public static String getSecondaryDnsServer() {
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

    public static String getDnsServerAddressById(String id) {
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

    public static String getDnsServerAddressByStringDescription(Context context, String description) {
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

    public static String[] getDnsServerIds() {
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

    public static String[] getDnsServerNames(Context context) {
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

    public static String getDnsServerDescription(String id, Context context) {
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
