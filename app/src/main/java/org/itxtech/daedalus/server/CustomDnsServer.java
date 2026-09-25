package org.itxtech.daedalus.server;

import org.itxtech.daedalus.Daedalus;

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
public class CustomDnsServer extends AbstractDnsServer {
    public static final int DEFAULT_PROXY_PORT = 1080;

    private String name;
    private String id;
    // Flag of configurations written before the proxy settings moved into the server.
    // Kept so that such servers can be migrated once, see Daedalus.migrateProxySettings().
    private boolean proxied;
    // SOCKS5 proxy this server is reached through, e.g. EasyTier's on this phone; null for
    // a direct connection
    private String proxyHost;
    private int proxyPort;
    private String proxyUsername;
    private String proxyPassword;
    // Certificate trusted for DoT connections to this server (self-signed or private CA), or null
    private String certificate;

    public CustomDnsServer(String name, String address, int port) {
        super(address, port);
        this.name = name;
        this.id = String.valueOf(Daedalus.configurations.getNextDnsId());
    }

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getProxyHost() {
        return proxyHost;
    }

    @Override
    public int getProxyPort() {
        return proxyPort > 0 ? proxyPort : DEFAULT_PROXY_PORT;
    }

    @Override
    public String getProxyUsername() {
        return proxyUsername;
    }

    @Override
    public String getProxyPassword() {
        return proxyPassword;
    }

    /**
     * Reaches the server through the given SOCKS5 proxy; an empty host means a direct
     * connection. Empty credentials mean the proxy needs no authentication.
     */
    public void setProxy(String host, int port, String username, String password) {
        proxyHost = host == null || host.trim().isEmpty() ? null : host.trim();
        proxyPort = port > 0 ? port : DEFAULT_PROXY_PORT;
        proxyUsername = username == null || username.isEmpty() ? null : username;
        proxyPassword = password == null || password.isEmpty() ? null : password;
        proxied = proxyHost != null;
    }

    public void clearProxy() {
        setProxy(null, 0, null, null);
    }

    /**
     * Whether an older version flagged the server as proxied without storing the proxy
     * itself; the settings of that time have to be copied in.
     */
    public boolean hasLegacyProxyFlag() {
        return proxied && proxyHost == null;
    }

    @Override
    public String getCertificate() {
        return certificate;
    }

    public void setCertificate(String certificate) {
        this.certificate = certificate;
    }
}
