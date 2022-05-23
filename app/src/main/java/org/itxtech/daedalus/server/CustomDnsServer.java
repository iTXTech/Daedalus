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
    private String name;
    private String id;

    public CustomDnsServer(String name, String address, int port) {
        super(address, port);
        this.name = name;
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

    public void setName(String name) {
        this.name = name;
    }
}
