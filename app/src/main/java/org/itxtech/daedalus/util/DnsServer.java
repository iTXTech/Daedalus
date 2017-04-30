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
public class DnsServer {
    private static int totalId = 0;

    private String id;
    private String address;
    private int description = 0;

    public DnsServer(String address, int description) {
        this.id = String.valueOf(totalId++);
        this.address = address;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getAddress() {
        return address;
    }

    public int getDescription() {
        return description;
    }

    public String getStringDescription(Context context) {
        return context.getResources().getString(description);
    }

    public static String getDnsServerAddressById(String id) {
        for (DnsServer server : Daedalus.DNS_SERVERS) {
            if (server.getId().equals(id)) {
                return server.getAddress();
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
        return Daedalus.DNS_SERVERS.get(0).getAddress();
    }

    public static String[] getDnsServerIds() {
        ArrayList<String> servers = new ArrayList<>(Daedalus.DNS_SERVERS.size());
        for (DnsServer server : Daedalus.DNS_SERVERS) {
            servers.add(server.getId());
        }
        String[] stringServers = new String[Daedalus.DNS_SERVERS.size()];
        return servers.toArray(stringServers);
    }

    public static String[] getDnsServerNames(Context context) {
        ArrayList<String> servers = new ArrayList<>(Daedalus.DNS_SERVERS.size());
        for (DnsServer server : Daedalus.DNS_SERVERS) {
            servers.add(server.getStringDescription(context));
        }
        String[] stringServers = new String[Daedalus.DNS_SERVERS.size()];
        return servers.toArray(stringServers);
    }

    public static DnsServer getDnsServerById(String id) {
        for (DnsServer server : Daedalus.DNS_SERVERS) {
            if (server.getId().equals(id)) {
                return server;
            }
        }
        return Daedalus.DNS_SERVERS.get(0);

    }
}
