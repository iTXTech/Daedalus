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

    private final String id;
    private final int description;
    private boolean enabledByDefault = true;

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

    @Override
    public String getId() {
        return id;
    }

    /**
     * Initial state of the on/off switch in Settings > Server Management. A server that
     * is off keeps its ID (so saved settings stay valid) but is hidden from every list.
     */
    public DnsServer setEnabledByDefault(boolean enabled) {
        this.enabledByDefault = enabled;
        return this;
    }

    public boolean isEnabledByDefault() {
        return enabledByDefault;
    }

    /**
     * Preference key of the on/off switch. Based on the address rather than the ID so
     * that reordering the built-in list does not move the switches around.
     */
    public String getEnabledKey() {
        return "server_enabled_" + address;
    }

    /**
     * Whether the server is shown in the server lists, as toggled in Settings > Server Management.
     */
    public boolean isEnabled() {
        return Daedalus.getPrefs().getBoolean(getEnabledKey(), enabledByDefault);
    }

    public String getStringDescription(Context context) {
        return context.getResources().getString(description);
    }

    @Override
    public String getName() {
        return getStringDescription(Daedalus.getInstance());
    }
}
