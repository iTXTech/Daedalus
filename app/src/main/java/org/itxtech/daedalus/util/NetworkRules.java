package org.itxtech.daedalus.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.R;
import org.itxtech.daedalus.server.AbstractDnsServer;
import org.itxtech.daedalus.server.DnsServerHelper;

import java.net.InetAddress;
import java.net.UnknownHostException;
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

/**
 * Evaluates the network rules against the network the VPN traffic actually leaves through.
 */
public class NetworkRules {
    /**
     * Servers chosen for the current network, with a description for the log.
     */
    public static class Selection {
        public final AbstractDnsServer primary;
        public final AbstractDnsServer secondary;
        public final String description;

        Selection(AbstractDnsServer primary, AbstractDnsServer secondary, String description) {
            this.primary = primary;
            this.secondary = secondary;
            this.description = description;
        }
    }

    /**
     * The network the VPN traffic leaves through: the best network with internet access
     * that is not a VPN. Like the system's default network choice, a validated network
     * (internet access confirmed) wins over an unvalidated one, then Wi-Fi and Ethernet
     * win over mobile data. Null when there is none.
     */
    public static Network getUnderlyingNetwork(ConnectivityManager manager) {
        Network best = null;
        int bestScore = -1;
        for (Network network : manager.getAllNetworks()) {
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
            if (capabilities == null
                    || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                    || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                continue;
            }
            int score = 0;
            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                score += 8;
            }
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                score += 4;
            } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                score += 2;
            }
            if (score > bestScore) {
                bestScore = score;
                best = network;
            }
        }
        return best;
    }

    /**
     * The servers to use on the given network: the first matching rule, or the
     * primary/secondary servers of the settings when no rule matches.
     */
    public static Selection select(ConnectivityManager manager, Network network) {
        NetworkCapabilities capabilities = network == null ? null : manager.getNetworkCapabilities(network);
        LinkProperties link = network == null ? null : manager.getLinkProperties(network);
        List<NetworkRule> rules = Daedalus.configurations.getNetworkRules();

        NetworkRule matched = null;
        for (NetworkRule rule : rules) {
            if (rule.getType() == NetworkRule.TYPE_SUBNET && link != null && matchesSubnet(rule, link)) {
                matched = rule;
                break;
            }
        }
        if (matched == null && capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            for (NetworkRule rule : rules) {
                if (rule.getType() == NetworkRule.TYPE_MOBILE) {
                    matched = rule;
                    break;
                }
            }
        }

        if (matched != null) {
            return new Selection(DnsServerHelper.getServerById(matched.getPrimary()),
                    DnsServerHelper.getServerById(matched.getSecondary()),
                    describe(matched, Daedalus.getInstance()));
        }
        return new Selection(DnsServerHelper.getServerById(DnsServerHelper.getPrimary()),
                DnsServerHelper.getServerById(DnsServerHelper.getSecondary()),
                Daedalus.getInstance().getString(R.string.query_log_default_servers));
    }

    /**
     * The DNS servers of the underlying network itself, for "Use system DNS as upstream".
     * Falls back to {@link #select} when the network has none.
     */
    public static Selection systemDns(ConnectivityManager manager, Network network) {
        LinkProperties link = network == null ? null : manager.getLinkProperties(network);
        List<InetAddress> servers = link == null ? null : link.getDnsServers();
        if (servers == null || servers.isEmpty()) {
            return select(manager, network);
        }
        AbstractDnsServer primary = plainServer(servers.get(0));
        AbstractDnsServer secondary = plainServer(servers.get(servers.size() > 1 ? 1 : 0));
        return new Selection(primary, secondary, Daedalus.getInstance().getString(R.string.query_log_system_dns));
    }

    private static AbstractDnsServer plainServer(InetAddress address) {
        AbstractDnsServer server = new AbstractDnsServer(address.getHostAddress(), AbstractDnsServer.DNS_SERVER_DEFAULT_PORT);
        server.setHostAddress(address.getHostAddress());
        return server;
    }

    private static boolean matchesSubnet(NetworkRule rule, LinkProperties link) {
        for (String cidr : rule.getSubnetList()) {
            for (LinkAddress linkAddress : link.getLinkAddresses()) {
                if (inSubnet(linkAddress.getAddress(), cidr)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether the address lies in the CIDR ("192.168.31.0/24", "fd00::/8"; a bare address
     * means a single host). Invalid CIDRs never match.
     */
    public static boolean inSubnet(InetAddress address, String cidr) {
        String[] parts = cidr.trim().split("/");
        String base = parts[0].trim();
        if (!isLiteral(base)) {
            return false;
        }
        byte[] network;
        try {
            network = InetAddress.getByName(base).getAddress();
        } catch (UnknownHostException e) {
            return false;
        }
        byte[] candidate = address.getAddress();
        if (candidate.length != network.length) {
            return false;
        }
        int prefix = network.length * 8;
        if (parts.length > 1) {
            try {
                prefix = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException e) {
                return false;
            }
            if (prefix < 0 || prefix > network.length * 8) {
                return false;
            }
        }
        for (int i = 0; i < prefix; i++) {
            int mask = 0x80 >> (i % 8);
            if ((candidate[i / 8] & mask) != (network[i / 8] & mask)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether the text is an IP literal, so that {@link InetAddress#getByName} does not
     * perform a DNS lookup.
     */
    private static boolean isLiteral(String text) {
        return text.matches("\\d{1,3}(\\.\\d{1,3}){3}") || (text.contains(":") && text.matches("[0-9a-fA-F:.]+"));
    }

    /**
     * Human readable condition of a rule, for lists and the log.
     */
    public static String describe(NetworkRule rule, Context context) {
        if (rule.getType() == NetworkRule.TYPE_MOBILE) {
            return context.getString(R.string.network_rule_type_mobile);
        }
        return context.getString(R.string.network_rule_type_subnet) + " " + rule.getSubnets();
    }
}
