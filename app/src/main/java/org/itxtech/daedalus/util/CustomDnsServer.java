package org.itxtech.daedalus.util;

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
public class CustomDnsServer {
    private String name;
    private String address;
    private int port;
    private String id;

    public CustomDnsServer(String name, String address, int port) {
        this.name = name;
        this.address = address;
        this.port = port;
        this.id = String.valueOf(Daedalus.configurations.getNextDnsId());
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public void setPort(int port) {
        this.port = port;
    }
}
