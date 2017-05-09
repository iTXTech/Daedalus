package org.itxtech.daedalus.util;

import org.itxtech.daedalus.Daedalus;

import java.util.ArrayList;

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
public class RulesProvider {
    private String name;
    private String downloadURL;
    private String fileName;
    private String data;

    public RulesProvider(String name, String downloadURL) {
        this(name, downloadURL, "Unknown");
    }

    public RulesProvider(String name, String downloadURL, String fileName) {
        this.name = name;
        this.downloadURL = downloadURL;
        this.fileName = fileName;
    }

    public String getName() {
        return name;
    }

    public String getDownloadURL() {
        return downloadURL;
    }

    public String getFileName() {
        return fileName;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getData() {
        String temp = data;
        data = null;
        return temp;
    }

    public static String[] getHostsProviderNames() {
        ArrayList<String> servers = new ArrayList<>(Daedalus.HOSTS_PROVIDERS.size());
        for (RulesProvider provider : Daedalus.HOSTS_PROVIDERS) {
            servers.add(provider.getName());
        }
        String[] stringServers = new String[Daedalus.HOSTS_PROVIDERS.size()];
        return servers.toArray(stringServers);
    }

    public static String[] getDnsmasqProviderNames() {
        ArrayList<String> servers = new ArrayList<>(Daedalus.DNSMASQ_PROVIDERS.size());
        for (RulesProvider provider : Daedalus.DNSMASQ_PROVIDERS) {
            servers.add(provider.getName());
        }
        String[] stringServers = new String[Daedalus.DNSMASQ_PROVIDERS.size()];
        return servers.toArray(stringServers);
    }

    public static String getDownloadUrlByName(String name) {
        for (RulesProvider provider : Daedalus.HOSTS_PROVIDERS) {
            if (provider.getName().equals(name)) {
                return provider.getDownloadURL();
            }
        }
        return null;
    }

    public static RulesProvider getProviderByName(String name) {
        for (RulesProvider provider : Daedalus.DNSMASQ_PROVIDERS) {
            if (provider.getName().equals(name)) {
                return provider;
            }
        }
        return null;
    }
}
