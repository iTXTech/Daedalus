package org.itxtech.daedalus;

import android.app.Application;
import org.itxtech.daedalus.util.DnsServer;

import java.util.ArrayList;
import java.util.List;

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
public class Daedalus extends Application {
    public static final List<DnsServer> DNS_SERVERS = new ArrayList<DnsServer>() {{
        add(new DnsServer("0", "113.107.249.56", R.string.server_north_china));
        add(new DnsServer("1", "120.27.103.230", R.string.server_east_china));
        add(new DnsServer("2", "123.206.61.167", R.string.server_south_china));
    }};

    private static Daedalus instance = null;

    @Override
    public void onCreate() {
        super.onCreate();

        instance = this;
    }

    public static Daedalus getInstance() {
        return instance;
    }
}
