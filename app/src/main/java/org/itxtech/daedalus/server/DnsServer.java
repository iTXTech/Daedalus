package org.itxtech.daedalus.server;

import android.content.Context;
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
public class DnsServer extends AbstractDnsServer {

    private static int totalId = 0;

    private String id;
    private int description;

    public DnsServer(String address, int description, int port) {
        super(address, port);
        this.id = String.valueOf(totalId++);
        this.description = description;
    }

    public DnsServer(String address, int description) {
        this(address, description, DNS_SERVER_DEFAULT_PORT);
    }

    public DnsServer(String address) {
        this(address, 0);
    }

    public String getId() {
        return id;
    }

    public String getStringDescription(Context context) {
        return context.getResources().getString(description);
    }

    @Override
    public String getName() {
        return getStringDescription(Daedalus.getInstance());
    }
}
