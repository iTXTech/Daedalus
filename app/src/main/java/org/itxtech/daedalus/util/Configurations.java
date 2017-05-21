package org.itxtech.daedalus.util;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import org.itxtech.daedalus.Daedalus;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
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
public class Configurations {
    private static final int CUSTOM_ID_START = 32;

    private static File file;

    private ArrayList<CustomDnsServer> customDnsServers;

    private ArrayList<Rule> hostsRules;
    private ArrayList<Rule> dnsmasqRules;

    private int totalDnsId;
    private int totalRuleId;

    private long activateCounter;

    int getNextDnsId() {
        if (totalDnsId < CUSTOM_ID_START) {
            totalDnsId = CUSTOM_ID_START;
        }
        return totalDnsId++;
    }

    int getNextRuleId() {
        if (totalRuleId < 0) {
            totalRuleId = 0;
        }
        return totalRuleId++;
    }

    public long getActivateCounter() {
        return activateCounter;
    }

    public void setActivateCounter(long activateCounter) {
        this.activateCounter = activateCounter;
    }

    public ArrayList<CustomDnsServer> getCustomDnsServers() {
        if (customDnsServers == null) {
            customDnsServers = new ArrayList<>();
        }
        return customDnsServers;
    }

    public ArrayList<Rule> getHostsRules() {
        if (hostsRules == null) {
            hostsRules = new ArrayList<>();
        }
        return hostsRules;
    }

    public ArrayList<Rule> getDnsmasqRules() {
        if (dnsmasqRules == null) {
            dnsmasqRules = new ArrayList<>();
        }
        return dnsmasqRules;
    }

    public ArrayList<Rule> getUsingRules() {
        if (hostsRules != null && hostsRules.size() > 0) {
            for (Rule rule : hostsRules) {
                if (rule.isUsing()) {
                    return hostsRules;
                }
            }
        }
        if (dnsmasqRules != null && dnsmasqRules.size() > 0) {
            for (Rule rule : dnsmasqRules) {
                if (rule.isUsing()) {
                    return dnsmasqRules;
                }
            }
        }
        return hostsRules;
    }

    public static Configurations load(File file) {
        Configurations.file = file;
        Configurations config = null;
        try {
            config = Daedalus.parseJson(Configurations.class, new JsonReader(new FileReader(file)));
        } catch (Exception ignored) {
        }

        if (config == null) {
            config = new Configurations();
        }

        return config;
    }

    public void save() {
        try {
            FileWriter writer = new FileWriter(file);
            new Gson().toJson(this, writer);
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
