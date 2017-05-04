package org.itxtech.daedalus.util;

import android.content.Context;

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
public class DnsServer {
    public static int DNS_SERVER_DEFAULT_PORT = 53;


    private static int totalId = 0;

    private String id;
    private String address;
    private int port;
    private int description = 0;

    public DnsServer(String address, int description, int port) {
        this.id = String.valueOf(totalId++);
        this.address = address;
        this.description = description;
        this.port = port;
    }

    public DnsServer(String address, int description) {
        this(address, description, DNS_SERVER_DEFAULT_PORT);
    }

    public int getPort() {
        return port;
    }

    public String getId() {
        return id;
    }

    public String getAddress() {
        return address;
    }

    public String getStringDescription(Context context) {
        return context.getResources().getString(description);
    }
}
