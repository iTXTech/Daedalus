package org.itxtech.daedalus.util;

import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.server.AbstractDnsServer;
import org.itxtech.daedalus.server.CustomDnsServer;

import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.Socket;

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
 * SOCKS5 proxies of custom DNS servers. Each server carries its own proxy settings,
 * e.g. a DNS server inside an EasyTier subnet is reached through EasyTier's SOCKS5
 * server on this phone. SOCKS5 CONNECT only carries TCP, so plain UDP queries to such
 * a server are sent as DNS over TCP.
 */
public class SocksProxy {
    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = CustomDnsServer.DEFAULT_PROXY_PORT;

    // A server that is not (yet) in the configuration but whose proxy credentials the
    // authenticator must know, e.g. the one being tested on the server settings page
    private static volatile AbstractDnsServer extraServer = null;

    public static boolean isProxied(AbstractDnsServer server) {
        return server != null && server.isProxied();
    }

    /**
     * The proxy to reach the given server through, or null for a direct connection.
     */
    public static Proxy getProxy(AbstractDnsServer server) {
        if (!isProxied(server)) {
            return null;
        }
        int port = server.getProxyPort() > 0 ? server.getProxyPort() : DEFAULT_PORT;
        return new Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved(server.getProxyHost().trim(), port));
    }

    /**
     * An unconnected TCP socket for the given server, going through the proxy when the
     * server is configured to use one.
     */
    public static Socket createSocket(AbstractDnsServer server) {
        Proxy proxy = getProxy(server);
        return proxy == null ? new Socket() : new Socket(proxy);
    }

    public static void setExtraServer(AbstractDnsServer server) {
        extraServer = server;
    }

    /**
     * Answers SOCKS5 username/password requests of java.net sockets (and therefore of
     * OkHttp) with the credentials of the server configured for that proxy. Call once at
     * application start.
     */
    public static void installAuthenticator() {
        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                if (!"SOCKS5".equalsIgnoreCase(getRequestingProtocol())) {
                    return null;
                }
                String host = getRequestingHost();
                int port = getRequestingPort();
                PasswordAuthentication extra = credentials(extraServer, host, port);
                if (extra != null) {
                    return extra;
                }
                for (CustomDnsServer server : Daedalus.configurations.getCustomDNSServers()) {
                    PasswordAuthentication authentication = credentials(server, host, port);
                    if (authentication != null) {
                        return authentication;
                    }
                }
                return null;
            }
        });
    }

    private static PasswordAuthentication credentials(AbstractDnsServer server, String host, int port) {
        if (!isProxied(server) || server.getProxyUsername() == null || server.getProxyUsername().isEmpty()
                || !server.getProxyHost().trim().equalsIgnoreCase(host) || server.getProxyPort() != port) {
            return null;
        }
        String password = server.getProxyPassword();
        return new PasswordAuthentication(server.getProxyUsername(), (password == null ? "" : password).toCharArray());
    }
}
