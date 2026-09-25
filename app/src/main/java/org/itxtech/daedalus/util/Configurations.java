package org.itxtech.daedalus.util;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.server.CustomDnsServer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
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

    private ArrayList<CustomDnsServer> customDNSServers;
    private ArrayList<NetworkRule> networkRules;
    private ArrayList<String> appObjects;

    private ArrayList<Rule> hostsRules;
    private ArrayList<Rule> dnsmasqRules;

    private int totalDnsId;
    private int totalRuleId;

    private long activateCounter;

    public int getNextDnsId() {
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

    public ArrayList<CustomDnsServer> getCustomDNSServers() {
        if (customDNSServers == null) {
            customDNSServers = new ArrayList<>();
        }
        return customDNSServers;
    }

    public ArrayList<NetworkRule> getNetworkRules() {
        if (networkRules == null) {
            networkRules = new ArrayList<>();
        }
        return networkRules;
    }

    public ArrayList<String> getAppObjects() {
        if (appObjects == null) {
            appObjects = new ArrayList<>();
        }
        return appObjects;
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

    public int getUsingRuleType() {
        if (hostsRules != null && hostsRules.size() > 0) {
            for (Rule rule : hostsRules) {
                if (rule.isUsing()) {
                    return Rule.TYPE_HOSTS;
                }
            }
        }
        if (dnsmasqRules != null && dnsmasqRules.size() > 0) {
            for (Rule rule : dnsmasqRules) {
                if (rule.isUsing()) {
                    return Rule.TYPE_DNAMASQ;
                }
            }
        }
        return Rule.TYPE_HOSTS;
    }

    /**
     * Loads the configuration from {@code file} (internal storage, always available).
     * A configuration written by older versions to {@code legacyFile} (the external
     * files directory, which is not always mounted when the process starts) is moved
     * to {@code file} on first run. A file that cannot be parsed is kept aside as
     * {@code config.json.corrupt} instead of being overwritten by the next save.
     */
    public static Configurations load(File file, File legacyFile) {
        Configurations.file = file;
        Configurations config = null;

        if (!file.exists() && legacyFile != null && legacyFile.exists()) {
            config = read(legacyFile);
            if (config != null) {
                config.save();
                if (legacyFile.renameTo(new File(legacyFile.getPath() + ".migrated"))) {
                    Logger.info("Migrated configuration from " + legacyFile + " to " + file);
                }
            }
        }

        if (config == null && file.exists()) {
            config = read(file);
            if (config == null) {
                File corrupt = new File(file.getPath() + ".corrupt");
                Logger.error("Configuration " + file + " cannot be read, keeping it as " + corrupt);
                if (!file.renameTo(corrupt)) {
                    Logger.error("Cannot move " + file + " to " + corrupt);
                }
            }
        }

        if (config == null) {
            Logger.info("Generating default configurations");
            config = new Configurations();
        }
        return config;
    }

    private static Configurations read(File file) {
        try (JsonReader reader = new JsonReader(new FileReader(file))) {
            Configurations config = Daedalus.parseJson(Configurations.class, reader);
            if (config == null) {
                throw new IOException("Empty configuration file");
            }
            Logger.info("Loaded configuration from " + file + " with "
                    + config.getCustomDNSServers().size() + " custom DNS servers");
            return config;
        } catch (Exception e) {
            Logger.logException(e);
            return null;
        }
    }

    public Configurations() {
        //TODO: Initial config. Eg. Build-in rules
    }

    /**
     * Writes the configuration to a temporary file and renames it over the real one,
     * so a process killed while saving never leaves a truncated configuration behind.
     */
    public synchronized void save() {
        if (file == null) {
            Logger.error("Configuration file is not set, nothing saved");
            return;
        }
        File temp = new File(file.getPath() + ".tmp");
        try {
            try (FileOutputStream out = new FileOutputStream(temp);
                 Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
                new Gson().toJson(this, writer);
                writer.flush();
                out.getFD().sync();
            }
            if (!temp.renameTo(file)) {
                throw new IOException("Cannot rename " + temp + " to " + file);
            }
        } catch (Exception e) {
            Logger.logException(e);
        }
    }
}
