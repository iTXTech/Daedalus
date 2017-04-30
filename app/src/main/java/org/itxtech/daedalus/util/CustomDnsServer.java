package org.itxtech.daedalus.util;

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
public class CustomDnsServer {
    private String name;
    private String address;
    private int port;

    public CustomDnsServer(String name, String address, int port) {
        this.name = name;
        this.address = address;
        this.port = port;
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
