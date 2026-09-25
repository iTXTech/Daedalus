package org.itxtech.daedalus.server;

import androidx.annotation.NonNull;

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
public class AbstractDnsServer implements Cloneable {
    public static final int DNS_SERVER_DEFAULT_PORT = 53;
    public static final int DNS_SERVER_TLS_PORT = 853;

    protected String address;
    protected int port;
    protected String hostAddress;

    public AbstractDnsServer(String address, int port) {
        this.address = address;
        this.port = port;
    }

    public void setHostAddress(String hostAddress) {
        this.hostAddress = hostAddress;
    }

    public String getHostAddress() {
        return hostAddress;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    /**
     * Identifier stored in the preferences. Ad-hoc servers (for example the extra
     * servers of the DNS test) have none.
     */
    public String getId() {
        return null;
    }

    public String getName() {
        return "";
    }


    /**
     * Host of the SOCKS5 proxy the server is reached through, or null for a direct
     * connection. Each server carries its own proxy settings.
     */
    public String getProxyHost() {
        return null;
    }

    public int getProxyPort() {
        return 0;
    }

    public String getProxyUsername() {
        return null;
    }

    public String getProxyPassword() {
        return null;
    }

    public boolean isProxied() {
        String host = getProxyHost();
        return host != null && !host.trim().isEmpty();
    }
    @Override
    public String toString() {
        return getName();
    }

    public String getRealName() {
        return isHttpsServer() ? address : address + ":" + port;
    }

    public boolean isHttpsServer() {
        return address.contains("/");
    }

    @NonNull
    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (Exception ignored) {
        }
        return new AbstractDnsServer("", 0);
    }
}
