package org.itxtech.daedalus.util;

import org.itxtech.daedalus.Daedalus;

import java.util.ArrayList;

/**
 * Daedalus Project
 *
 * @author iTXTech
 * @link https://itxtech.org
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
public class HostsProvider {
    private String name;
    private String downloadURL;

    public HostsProvider(String name, String downloadURL) {
        this.name = name;
        this.downloadURL = downloadURL;
    }

    public String getName() {
        return name;
    }

    public String getDownloadURL() {
        return downloadURL;
    }

    public static String[] getHostsProviderNames() {
        ArrayList<String> servers = new ArrayList<>(Daedalus.HOSTS_PROVIDERS.size());
        for (HostsProvider hostsProvider : Daedalus.HOSTS_PROVIDERS) {
            servers.add(hostsProvider.getName());
        }
        String[] stringServers = new String[Daedalus.HOSTS_PROVIDERS.size()];
        return servers.toArray(stringServers);
    }

    public static String getDownloadUrlByName(String name) {
        for (HostsProvider hostsProvider : Daedalus.HOSTS_PROVIDERS) {
            if (hostsProvider.getName().equals(name)) {
                return hostsProvider.getDownloadURL();
            }
        }
        return null;
    }
}
