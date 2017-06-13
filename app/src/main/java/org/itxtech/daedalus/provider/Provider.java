package org.itxtech.daedalus.provider;

import android.os.ParcelFileDescriptor;
import org.itxtech.daedalus.service.DaedalusVpnService;

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
public abstract class Provider {
    ParcelFileDescriptor descriptor;
    DaedalusVpnService service;
    boolean running = false;
    static long dnsQueryTimes = 0;

    Provider(ParcelFileDescriptor descriptor, DaedalusVpnService service) {
        this.descriptor = descriptor;
        this.service = service;
        dnsQueryTimes = 0;
    }

    public final long getDnsQueryTimes() {
        return dnsQueryTimes;
    }

    public abstract void process();

    public final void start() {
        running = true;
    }

    public final void shutdown() {
        running = false;
    }

    public abstract void stop();
}