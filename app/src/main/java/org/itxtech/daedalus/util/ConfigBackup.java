package org.itxtech.daedalus.util;

import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.server.AbstractDnsServer;
import org.itxtech.daedalus.server.CustomDnsServer;
import org.itxtech.daedalus.server.DnsServer;
import org.itxtech.daedalus.server.DnsServerHelper;
import org.itxtech.daedalus.service.DaedalusVpnService;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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

/**
 * Export and import of everything the user can configure, as one JSON document:
 * custom servers (with their certificates as PEM text), the default primary/secondary
 * servers, network rules, hosts/dnsmasq rules, the app filter list and all settings.
 * A document with only some of the sections, e.g. just "servers", is imported as well.
 * <p>
 * Importing merges and never deletes: items that already exist (same address and port,
 * same rule file, same network condition) are updated, others are added, settings are
 * overwritten. Servers are referenced by stable names rather than by their local IDs:
 * {@code builtin:<address>} for a built-in server and {@code custom:<address>:<port>}
 * for a custom one, so that a document works on another device.
 */
public class ConfigBackup {
    private static final int VERSION = 2;
    private static final String REF_BUILTIN = "builtin:";
    private static final String REF_CUSTOM = "custom:";
    private static final long MAX_RULE_FILE_SIZE = 4L * 1024 * 1024;

    private static class Document {
        int version = VERSION;
        List<ServerEntry> servers;
        DefaultServers defaultServers;
        List<NetworkRuleEntry> networkRules;
        List<RuleEntry> rules;
        List<String> apps;
        JsonObject preferences;
    }

    private static class ServerEntry {
        String name;
        String address;
        int port;
        // Plain-DNS transport forced by the user: "udp" or "tcp"; absent means automatic
        String transport;
        // Kept for documents of versions with a single global proxy
        boolean proxied;
        String proxyHost;
        int proxyPort;
        String proxyUsername;
        String proxyPassword;
        String certificate;
    }

    private static class DefaultServers {
        String primary;
        String secondary;
    }

    private static class NetworkRuleEntry {
        int type;
        String subnets;
        String primary;
        String secondary;
    }

    private static class RuleEntry {
        String name;
        String fileName;
        int type;
        String downloadUrl;
        boolean using;
        // File content, only for rules that cannot be downloaded again
        String content;
    }

    public static class Result {
        public int serversAdded;
        public int serversUpdated;
        public int networkRulesAdded;
        public int networkRulesUpdated;
        public int rulesAdded;
        public int rulesUpdated;
        public int apps;
        public int preferences;
        public final List<String> warnings = new ArrayList<>();
    }

    public static String export() throws IOException {
        Document document = new Document();
        document.servers = new ArrayList<>();
        for (CustomDnsServer server : Daedalus.configurations.getCustomDNSServers()) {
            ServerEntry entry = new ServerEntry();
            entry.name = server.getName();
            entry.address = server.getAddress();
            entry.port = server.getPort();
            entry.transport = transportName(server.getTransport());
            entry.proxied = server.isProxied();
            if (server.isProxied()) {
                entry.proxyHost = server.getProxyHost();
                entry.proxyPort = server.getProxyPort();
                entry.proxyUsername = server.getProxyUsername();
                entry.proxyPassword = server.getProxyPassword();
            }
            if (TlsCertificates.exists(server.getCertificate())) {
                entry.certificate = TlsCertificates.readPem(server.getCertificate());
            }
            document.servers.add(entry);
        }

        document.defaultServers = new DefaultServers();
        document.defaultServers.primary = ref(DnsServerHelper.getServerById(DnsServerHelper.getPrimary()));
        document.defaultServers.secondary = ref(DnsServerHelper.getServerById(DnsServerHelper.getSecondary()));

        document.networkRules = new ArrayList<>();
        for (NetworkRule rule : Daedalus.configurations.getNetworkRules()) {
            NetworkRuleEntry entry = new NetworkRuleEntry();
            entry.type = rule.getType();
            entry.subnets = rule.getSubnets();
            entry.primary = ref(DnsServerHelper.getServerById(rule.getPrimary()));
            entry.secondary = ref(DnsServerHelper.getServerById(rule.getSecondary()));
            document.networkRules.add(entry);
        }

        document.rules = new ArrayList<>();
        List<Rule> rules = new ArrayList<>(Daedalus.configurations.getHostsRules());
        rules.addAll(Daedalus.configurations.getDnsmasqRules());
        for (Rule rule : rules) {
            RuleEntry entry = new RuleEntry();
            entry.name = rule.getName();
            entry.fileName = rule.getFileName();
            entry.type = rule.getType();
            entry.downloadUrl = rule.getDownloadUrl();
            entry.using = rule.isUsing();
            entry.content = readRuleFile(rule);
            document.rules.add(entry);
        }

        document.apps = new ArrayList<>(Daedalus.configurations.getAppObjects());

        document.preferences = new JsonObject();
        Gson gson = new Gson();
        for (Map.Entry<String, ?> entry : Daedalus.getPrefs().getAll().entrySet()) {
            if (isExcludedPreference(entry.getKey()) || entry.getValue() == null) {
                continue;
            }
            document.preferences.add(entry.getKey(), gson.toJsonTree(entry.getValue()));
        }
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(document);
    }

    /**
     * Preferences that are not settings of their own: the default servers are exported as
     * references, keys starting with "_" belong to the preference framework, and the
     * global SOCKS5 settings of older versions now live in the servers.
     */
    private static boolean isExcludedPreference(String key) {
        return key.startsWith("_") || key.equals("primary_server") || key.equals("secondary_server")
                || key.startsWith("settings_socks5_") || key.startsWith("service_");
    }

    /**
     * Content of a rule file that cannot be downloaded again (imported from a local file),
     * so that the backup is self-contained. Downloadable rules are synced on the Rules page.
     */
    private static String readRuleFile(Rule rule) {
        String url = rule.getDownloadUrl();
        if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
            return null;
        }
        if (Daedalus.rulePath == null || rule.getFileName() == null) {
            return null;
        }
        File file = new File(Daedalus.rulePath + rule.getFileName());
        if (!file.isFile() || file.length() > MAX_RULE_FILE_SIZE) {
            return null;
        }
        try (InputStream in = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int read = 0;
            while (read < data.length) {
                int count = in.read(data, read, data.length - read);
                if (count < 0) {
                    break;
                }
                read += count;
            }
            return new String(data, 0, read, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Logger.logException(e);
            return null;
        }
    }

    public static Result importJson(String json) throws IOException, GeneralSecurityException {
        Document document;
        try {
            document = new Gson().fromJson(json, Document.class);
        } catch (JsonParseException e) {
            throw new IOException("Not a Daedalus configuration: " + e.getMessage());
        }
        if (document == null || (document.servers == null && document.preferences == null
                && document.networkRules == null && document.rules == null && document.apps == null)) {
            throw new IOException("Not a Daedalus configuration");
        }

        Result result = new Result();
        if (document.servers != null) {
            importServers(document.servers, document.preferences, result);
        }
        if (document.rules != null) {
            importRules(document.rules, result);
        }
        if (document.networkRules != null) {
            importNetworkRules(document.networkRules, result);
        }
        if (document.apps != null) {
            importApps(document.apps, result);
        }
        if (document.preferences != null) {
            importPreferences(document.preferences, result);
        }
        if (document.defaultServers != null) {
            importDefaultServers(document.defaultServers, result);
        }
        Daedalus.configurations.save();
        Daedalus.setRulesChanged();
        DaedalusVpnService.notifyConfigurationChanged();
        return result;
    }

    private static void importServers(List<ServerEntry> entries, JsonObject preferences, Result result)
            throws IOException, GeneralSecurityException {
        ArrayList<CustomDnsServer> servers = Daedalus.configurations.getCustomDNSServers();
        for (ServerEntry entry : entries) {
            if (entry == null || entry.address == null || entry.address.trim().isEmpty()) {
                continue;
            }
            String address = entry.address.trim();
            int port = entry.port > 0 ? entry.port : AbstractDnsServer.DNS_SERVER_DEFAULT_PORT;
            String name = entry.name == null || entry.name.trim().isEmpty() ? address : entry.name.trim();
            String certificate = null;
            if (entry.certificate != null && !entry.certificate.trim().isEmpty()) {
                certificate = TlsCertificates.importPem(entry.certificate);
            }

            String proxyHost = entry.proxyHost == null ? "" : entry.proxyHost.trim();
            int proxyPort = entry.proxyPort;
            String proxyUsername = entry.proxyUsername;
            String proxyPassword = entry.proxyPassword;
            if (proxyHost.isEmpty() && entry.proxied) {
                // Document of a version with one global proxy: take it from its settings section
                proxyHost = stringOf(preferences, "settings_socks5_host", SocksProxy.DEFAULT_HOST);
                try {
                    proxyPort = Integer.parseInt(stringOf(preferences, "settings_socks5_port", "").trim());
                } catch (NumberFormatException e) {
                    proxyPort = SocksProxy.DEFAULT_PORT;
                }
                proxyUsername = stringOf(preferences, "settings_socks5_username", null);
                proxyPassword = stringOf(preferences, "settings_socks5_password", null);
            }

            CustomDnsServer existing = findServer(servers, address, port);
            if (existing == null) {
                CustomDnsServer server = new CustomDnsServer(name, address, port);
                server.setTransport(transportOf(entry.transport));
                server.setProxy(proxyHost, proxyPort, proxyUsername, proxyPassword);
                server.setCertificate(certificate);
                servers.add(server);
                result.serversAdded++;
            } else {
                String previous = existing.getCertificate();
                existing.setName(name);
                if (entry.transport != null) {
                    existing.setTransport(transportOf(entry.transport));
                }
                existing.setProxy(proxyHost, proxyPort, proxyUsername, proxyPassword);
                existing.setCertificate(certificate);
                if (previous != null && !previous.equals(certificate)) {
                    TlsCertificates.deleteIfUnused(previous);
                }
                result.serversUpdated++;
            }
        }
    }

    private static String stringOf(JsonObject object, String key, String fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            String value = object.get(key).getAsString();
            return value.trim().isEmpty() ? fallback : value;
        } catch (Exception e) {
            return fallback;
        }
    }

    /**
     * Name of the forced plain-DNS transport for the document, or null for the automatic
     * choice (a null field is left out of the JSON).
     */
    private static String transportName(int transport) {
        switch (transport) {
            case AbstractDnsServer.TRANSPORT_UDP:
                return "udp";
            case AbstractDnsServer.TRANSPORT_TCP:
                return "tcp";
            default:
                return null;
        }
    }

    private static int transportOf(String name) {
        if ("udp".equalsIgnoreCase(name)) {
            return AbstractDnsServer.TRANSPORT_UDP;
        }
        if ("tcp".equalsIgnoreCase(name)) {
            return AbstractDnsServer.TRANSPORT_TCP;
        }
        return AbstractDnsServer.TRANSPORT_AUTO;
    }

    private static CustomDnsServer findServer(List<CustomDnsServer> servers, String address, int port) {
        for (CustomDnsServer server : servers) {
            if (server.getAddress().equalsIgnoreCase(address) && server.getPort() == port) {
                return server;
            }
        }
        return null;
    }

    private static void importRules(List<RuleEntry> entries, Result result) {
        for (RuleEntry entry : entries) {
            if (entry == null || entry.fileName == null || entry.fileName.trim().isEmpty()) {
                continue;
            }
            String fileName = entry.fileName.trim();
            int type = entry.type == Rule.TYPE_DNAMASQ ? Rule.TYPE_DNAMASQ : Rule.TYPE_HOSTS;
            String name = entry.name == null || entry.name.trim().isEmpty() ? fileName : entry.name.trim();
            String downloadUrl = entry.downloadUrl == null ? "" : entry.downloadUrl;

            Rule rule = findRule(type, fileName);
            if (rule == null) {
                rule = new Rule(name, fileName, type, downloadUrl);
                rule.addToConfig();
                result.rulesAdded++;
            } else {
                rule.setName(name);
                rule.setDownloadUrl(downloadUrl);
                result.rulesUpdated++;
            }
            if (entry.using) {
                rule.setUsing(true);
            }
            if (entry.content != null && !entry.content.isEmpty()) {
                if (Daedalus.rulePath == null) {
                    result.warnings.add("No rule directory for " + fileName);
                } else {
                    try (FileOutputStream out = new FileOutputStream(Daedalus.rulePath + fileName)) {
                        out.write(entry.content.getBytes(StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        Logger.logException(e);
                        result.warnings.add("Cannot write rule file " + fileName);
                    }
                }
            }
        }
    }

    private static Rule findRule(int type, String fileName) {
        List<Rule> rules = type == Rule.TYPE_DNAMASQ
                ? Daedalus.configurations.getDnsmasqRules() : Daedalus.configurations.getHostsRules();
        for (Rule rule : rules) {
            if (fileName.equals(rule.getFileName())) {
                return rule;
            }
        }
        return null;
    }

    private static void importNetworkRules(List<NetworkRuleEntry> entries, Result result) {
        ArrayList<NetworkRule> rules = Daedalus.configurations.getNetworkRules();
        for (NetworkRuleEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            int type = entry.type == NetworkRule.TYPE_MOBILE ? NetworkRule.TYPE_MOBILE : NetworkRule.TYPE_SUBNET;
            String subnets = entry.subnets == null ? "" : entry.subnets.trim();
            if (type == NetworkRule.TYPE_SUBNET && subnets.isEmpty()) {
                continue;
            }
            String primary = resolveRef(entry.primary);
            String secondary = resolveRef(entry.secondary);
            if (primary == null || secondary == null) {
                result.warnings.add("Network rule " + (type == NetworkRule.TYPE_MOBILE ? "mobile" : subnets)
                        + " refers to an unknown server");
                continue;
            }

            NetworkRule existing = null;
            for (NetworkRule rule : rules) {
                if (rule.getType() == type && (type == NetworkRule.TYPE_MOBILE || rule.getSubnets().trim().equals(subnets))) {
                    existing = rule;
                    break;
                }
            }
            if (existing == null) {
                existing = new NetworkRule();
                existing.setType(type);
                existing.setSubnets(subnets);
                rules.add(existing);
                result.networkRulesAdded++;
            } else {
                result.networkRulesUpdated++;
            }
            existing.setPrimary(primary);
            existing.setSecondary(secondary);
        }
    }

    private static void importApps(List<String> entries, Result result) {
        ArrayList<String> apps = Daedalus.configurations.getAppObjects();
        for (String app : entries) {
            if (app != null && !app.trim().isEmpty() && !apps.contains(app.trim())) {
                apps.add(app.trim());
                result.apps++;
            }
        }
    }

    /**
     * Writes the settings back with the type each key currently has, so that a hand-edited
     * document cannot turn a boolean setting into a string; unknown keys take the JSON type.
     */
    private static void importPreferences(JsonObject preferences, Result result) {
        SharedPreferences prefs = Daedalus.getPrefs();
        Map<String, ?> existing = prefs.getAll();
        SharedPreferences.Editor editor = prefs.edit();
        for (Map.Entry<String, JsonElement> entry : preferences.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            if (isExcludedPreference(key) || value == null || value.isJsonNull()) {
                continue;
            }
            try {
                Object current = existing.get(key);
                if (current instanceof Boolean) {
                    editor.putBoolean(key, value.getAsBoolean());
                } else if (current instanceof Integer) {
                    editor.putInt(key, value.getAsInt());
                } else if (current instanceof Long) {
                    editor.putLong(key, value.getAsLong());
                } else if (current instanceof Float) {
                    editor.putFloat(key, value.getAsFloat());
                } else if (current instanceof String) {
                    editor.putString(key, value.getAsString());
                } else if (current instanceof Set || value.isJsonArray()) {
                    Set<String> set = new HashSet<>();
                    for (JsonElement element : value.getAsJsonArray()) {
                        set.add(element.getAsString());
                    }
                    editor.putStringSet(key, set);
                } else if (value.isJsonPrimitive()) {
                    JsonPrimitive primitive = value.getAsJsonPrimitive();
                    if (primitive.isBoolean()) {
                        editor.putBoolean(key, primitive.getAsBoolean());
                    } else if (primitive.isNumber()) {
                        double number = primitive.getAsDouble();
                        if (number == Math.rint(number) && Math.abs(number) < Integer.MAX_VALUE) {
                            editor.putInt(key, (int) number);
                        } else {
                            editor.putFloat(key, (float) number);
                        }
                    } else {
                        editor.putString(key, primitive.getAsString());
                    }
                } else {
                    continue;
                }
                result.preferences++;
            } catch (Exception e) {
                result.warnings.add("Setting " + key + ": " + e.getMessage());
            }
        }
        editor.apply();
    }

    private static void importDefaultServers(DefaultServers servers, Result result) {
        SharedPreferences.Editor editor = Daedalus.getPrefs().edit();
        String primary = resolveRef(servers.primary);
        String secondary = resolveRef(servers.secondary);
        if (primary != null) {
            editor.putString("primary_server", primary);
        } else if (servers.primary != null) {
            result.warnings.add("Unknown primary server " + servers.primary);
        }
        if (secondary != null) {
            editor.putString("secondary_server", secondary);
        } else if (servers.secondary != null) {
            result.warnings.add("Unknown secondary server " + servers.secondary);
        }
        editor.apply();
    }

    /**
     * Stable reference of a server for documents shared between devices.
     */
    private static String ref(AbstractDnsServer server) {
        if (server instanceof CustomDnsServer) {
            return REF_CUSTOM + server.getAddress() + ":" + server.getPort();
        }
        return REF_BUILTIN + server.getAddress();
    }

    /**
     * The local ID of a referenced server, or null when it does not exist here.
     */
    private static String resolveRef(String ref) {
        if (ref == null || ref.trim().isEmpty()) {
            return null;
        }
        ref = ref.trim();
        if (ref.startsWith(REF_BUILTIN)) {
            String address = ref.substring(REF_BUILTIN.length());
            for (DnsServer server : Daedalus.DNS_SERVERS) {
                if (server.getAddress().equalsIgnoreCase(address)) {
                    return server.getId();
                }
            }
            return null;
        }
        if (ref.startsWith(REF_CUSTOM)) {
            String rest = ref.substring(REF_CUSTOM.length());
            int separator = rest.lastIndexOf(':');
            if (separator <= 0) {
                return null;
            }
            try {
                CustomDnsServer server = findServer(Daedalus.configurations.getCustomDNSServers(),
                        rest.substring(0, separator), Integer.parseInt(rest.substring(separator + 1)));
                return server == null ? null : server.getId();
            } catch (NumberFormatException e) {
                return null;
            }
        }
        // A raw ID, e.g. from a document edited by hand
        AbstractDnsServer server = DnsServerHelper.getServerById(ref);
        return ref.equals(server.getId()) ? ref : null;
    }

    /**
     * Downloads a configuration document over a direct connection.
     */
    public static String fetch(String url) throws IOException {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code());
            }
            return response.body().string();
        }
    }
}
