package org.itxtech.daedalus.server;

import android.content.Context;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.service.DaedalusVpnService;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    /**
     * Addresses of DoH server host names, resolved by the VPN service on the underlying
     * network so that the lookup never goes through the VPN itself.
     */
    public static final Map<String, List<InetAddress>> domainCache = new ConcurrentHashMap<>();

    public static void clearCache() {
        domainCache.clear();
    }

    /**
     * Built-in servers that are not disabled, in list order.
     */
    public static List<DnsServer> getBuiltInServers() {
        ArrayList<DnsServer> servers = new ArrayList<>(Daedalus.DNS_SERVERS.size());
        for (DnsServer server : Daedalus.DNS_SERVERS) {
            if (server.isEnabled()) {
                servers.add(server);
            }
        }
        return servers;
    }

    /**
     * Custom servers first, the most recently added on top, followed by the enabled
     * built-in servers. This is the order used by every server list in the UI.
     */
    public static ArrayList<AbstractDnsServer> getAllServers() {
        ArrayList<CustomDnsServer> custom = Daedalus.configurations.getCustomDNSServers();
        ArrayList<AbstractDnsServer> servers = new ArrayList<>(custom.size() + Daedalus.DNS_SERVERS.size());
        for (int i = custom.size() - 1; i >= 0; i--) {
            servers.add(custom.get(i));
        }
        servers.addAll(getBuiltInServers());
        return servers;
    }

    public static int getPosition(String id) {
        ArrayList<AbstractDnsServer> servers = getAllServers();
        for (int i = 0; i < servers.size(); i++) {
            if (id.equals(servers.get(i).getId())) {
                return i;
            }
        }
        return 0;
    }

    public static String getPrimary() {
        return checkServerId(Daedalus.getPrefs().getString("primary_server", "0"), 0);
    }

    public static String getSecondary() {
        return checkServerId(Daedalus.getPrefs().getString("secondary_server", "1"), 1);
    }

    /**
     * Returns the given ID when it refers to an enabled built-in or a custom server.
     * Otherwise (never set, server disabled or custom server deleted) the ID of the
     * server at the given position of the list is returned, like the original defaults
     * "0" and "1" did.
     */
    private static String checkServerId(String id, int fallbackPosition) {
        if (findServerById(id) != null) {
            return id;
        }
        ArrayList<AbstractDnsServer> servers = getAllServers();
        if (servers.isEmpty()) {
            return Daedalus.DNS_SERVERS.get(0).getId();
        }
        return servers.get(Math.min(fallbackPosition, servers.size() - 1)).getId();
    }

    private static AbstractDnsServer findServerById(String id) {
        if (id == null) {
            return null;
        }
        for (DnsServer server : getBuiltInServers()) {
            if (server.getId().equals(id)) {
                return server;
            }
        }
        for (CustomDnsServer customDNSServer : Daedalus.configurations.getCustomDNSServers()) {
            if (customDNSServer.getId().equals(id)) {
                return customDNSServer;
            }
        }
        return null;
    }

    public static AbstractDnsServer getServerById(String id) {
        AbstractDnsServer server = findServerById(id);
        if (server != null) {
            return server;
        }
        ArrayList<AbstractDnsServer> servers = getAllServers();
        return servers.isEmpty() ? Daedalus.DNS_SERVERS.get(0) : servers.get(0);
    }

    public static String[] getIds() {
        ArrayList<AbstractDnsServer> servers = getAllServers();
        String[] ids = new String[servers.size()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = servers.get(i).getId();
        }
        return ids;
    }

    public static String[] getNames(Context context) {
        ArrayList<AbstractDnsServer> servers = getAllServers();
        String[] names = new String[servers.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = getName(servers.get(i), context);
        }
        return names;
    }

    public static String getDescription(String id, Context context) {
        return getName(getServerById(id), context);
    }

    private static String getName(AbstractDnsServer server, Context context) {
        if (server instanceof DnsServer) {
            return ((DnsServer) server).getStringDescription(context);
        }
        return server.getName();
    }

    public static boolean isInUsing(CustomDnsServer server) {
        return DaedalusVpnService.isActivated() && (server.getId().equals(getPrimary()) || server.getId().equals(getSecondary()));
    }
}
