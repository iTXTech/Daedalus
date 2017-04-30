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
 * @author iTXTech
 * @link https://itxtech.org
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 */
public class Configurations {
    private static File file;

    private ArrayList<CustomDnsServer> customDnsServers;

    public ArrayList<CustomDnsServer> getCustomDnsServers() {
        if (customDnsServers == null) {
            customDnsServers = new ArrayList<>();
        }
        return customDnsServers;
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
