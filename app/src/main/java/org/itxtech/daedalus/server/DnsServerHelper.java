package org.itxtech.daedalus.server;

import android.content.Context;
import android.net.Uri;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.provider.HttpsProvider;
import org.itxtech.daedalus.provider.ProviderPicker;
import org.itxtech.daedalus.service.DaedalusVpnService;
import org.itxtech.daedalus.util.Logger;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

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
public class DnsServerHelper {
    public static HashMap<String, List<InetAddress>> domainCache = new HashMap<>();

    public static void clearCache() {
        domainCache = new HashMap<>();
    }

    public static void buildCache() {
        domainCache = new HashMap<>();
        if (ProviderPicker.getDnsQueryMethod() >= ProviderPicker.DNS_QUERY_METHOD_HTTPS_IETF &&
                !Daedalus.getPrefs().getBoolean("settings_dont_build_cache", false)) {
            buildDomainCache(getServerById(getPrimary()).getAddress());
            buildDomainCache(getServerById(getSecondary()).getAddress());
        }
    }

    private static void buildDomainCache(String addr) {
        addr = HttpsProvider.HTTPS_SUFFIX + addr;
        String host = Uri.parse(addr).getHost();
        try {
            domainCache.put(host, Arrays.asList(InetAddress.getAllByName(host)));
        } catch (Exception e) {
            Logger.logException(e);
        }
    }

    public static int getPosition(String id) {
        int intId = Integer.parseInt(id);
        if (intId < Daedalus.DNS_SERVERS.size()) {
            return intId;
        }

        for (int i = 0; i < Daedalus.configurations.getCustomDNSServers().size(); i++) {
            if (Daedalus.configurations.getCustomDNSServers().get(i).getId().equals(id)) {
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
        if (id < Daedalus.DNS_SERVERS.size()) {
            return id;
        }
        for (CustomDnsServer server : Daedalus.configurations.getCustomDNSServers()) {
            if (server.getId().equals(String.valueOf(id))) {
                return id;
            }
        }
        return 0;
    }

    public static AbstractDnsServer getServerById(String id) {
        for (DnsServer server : Daedalus.DNS_SERVERS) {
            if (server.getId().equals(id)) {
                return server;
            }
        }
        for (CustomDnsServer customDNSServer : Daedalus.configurations.getCustomDNSServers()) {
            if (customDNSServer.getId().equals(id)) {
                return customDNSServer;
            }
        }
        return Daedalus.DNS_SERVERS.get(0);
    }

    public static String[] getIds() {
        ArrayList<String> servers = new ArrayList<>(Daedalus.DNS_SERVERS.size());
        for (DnsServer server : Daedalus.DNS_SERVERS) {
            servers.add(server.getId());
        }
        for (CustomDnsServer customDNSServer : Daedalus.configurations.getCustomDNSServers()) {
            servers.add(customDNSServer.getId());
        }
        String[] stringServers = new String[Daedalus.DNS_SERVERS.size()];
        return servers.toArray(stringServers);
    }

    public static String[] getNames(Context context) {
        ArrayList<String> servers = new ArrayList<>(Daedalus.DNS_SERVERS.size());
        for (DnsServer server : Daedalus.DNS_SERVERS) {
            servers.add(server.getStringDescription(context));
        }
        for (CustomDnsServer customDNSServer : Daedalus.configurations.getCustomDNSServers()) {
            servers.add(customDNSServer.getName());
        }
        String[] stringServers = new String[Daedalus.DNS_SERVERS.size()];
        return servers.toArray(stringServers);
    }

    public static ArrayList<AbstractDnsServer> getAllServers() {
        ArrayList<AbstractDnsServer> servers = new ArrayList<>(Daedalus.DNS_SERVERS.size());
        servers.addAll(Daedalus.DNS_SERVERS);
        servers.addAll(Daedalus.configurations.getCustomDNSServers());
        return servers;
    }

    public static String getDescription(String id, Context context) {
        for (DnsServer server : Daedalus.DNS_SERVERS) {
            if (server.getId().equals(id)) {
                return server.getStringDescription(context);
            }
        }
        for (CustomDnsServer customDNSServer : Daedalus.configurations.getCustomDNSServers()) {
            if (customDNSServer.getId().equals(id)) {
                return customDNSServer.getName();
            }
        }
        return Daedalus.DNS_SERVERS.get(0).getStringDescription(context);
    }

    public static boolean isInUsing(CustomDnsServer server) {
        return DaedalusVpnService.isActivated() && (server.getId().equals(getPrimary()) || server.getId().equals(getSecondary()));
    }
}
