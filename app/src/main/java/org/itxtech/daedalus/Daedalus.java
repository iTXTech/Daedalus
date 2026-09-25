package org.itxtech.daedalus;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.content.res.Configuration;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import androidx.preference.PreferenceManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.stream.JsonReader;
import org.itxtech.daedalus.activity.MainActivity;
import org.itxtech.daedalus.server.AbstractDnsServer;
import org.itxtech.daedalus.server.CustomDnsServer;
import org.itxtech.daedalus.server.DnsServer;
import org.itxtech.daedalus.server.DnsServerHelper;
import org.itxtech.daedalus.service.DaedalusVpnService;
import org.itxtech.daedalus.util.Configurations;
import org.itxtech.daedalus.util.Logger;
import org.itxtech.daedalus.util.Rule;
import org.itxtech.daedalus.util.RuleResolver;
import org.itxtech.daedalus.util.SocksProxy;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
public class Daedalus extends Application {

    private static final String SHORTCUT_ID_ACTIVATE = "shortcut_activate";

    public static final List<DnsServer> DNS_SERVERS = new ArrayList<DnsServer>() {{
        // Every built-in server can be switched off in Settings > Server Management
        add(new DnsServer("101.101.101.101", R.string.server_twnic_primary));
        add(new DnsServer("101.102.103.104", R.string.server_twnic_secondary));
        add(new DnsServer("rubyfish.cn/dns-query", R.string.server_rubyfish));
        add(new DnsServer("cloudflare-dns.com/dns-query", R.string.server_cloudflare));
        add(new DnsServer("dns.google/dns-query", R.string.server_google_ietf));
        add(new DnsServer("dns.google/resolve", R.string.server_google_json));
        add(new DnsServer("dns.alidns.com", R.string.server_alidns, AbstractDnsServer.DNS_SERVER_TLS_PORT));
        add(new DnsServer("dns.alidns.com/dns-query", R.string.server_alidns_doh));
        add(new DnsServer("i4cm5lqxfu.cloudflare-gateway.com/dns-query", R.string.server_cloudflare_gateway));
    }};

    public static final ArrayList<Rule> RULES = new ArrayList<Rule>() {{
        add(new Rule("googlehosts/hosts", "googlehosts.hosts", Rule.TYPE_HOSTS,
                "https://raw.githubusercontent.com/googlehosts/hosts/master/hosts-files/hosts", false));
        add(new Rule("VeleSila/yhosts", "vokins.hosts", Rule.TYPE_HOSTS,
                "https://raw.githubusercontent.com/VeleSila/yhosts/master/hosts.txt", false));
        add(new Rule("adaway", "adaway.hosts", Rule.TYPE_HOSTS,
                "https://adaway.org/hosts.txt", false));
        //Build-in DNSMasq rule providers
        add(new Rule("anti-AD", "antiad.dnsmasq", Rule.TYPE_DNAMASQ,
                "https://anti-ad.net/anti-ad-for-dnsmasq.conf", false));
        add(new Rule("vokins/yhosts/union", "union.dnsmasq", Rule.TYPE_DNAMASQ,
                "https://raw.githubusercontent.com/vokins/yhosts/master/dnsmasq/union.conf", false));
    }};

    public static final String[] DEFAULT_TEST_DOMAINS = {
            "google.com",
            "twitter.com",
            "youtube.com",
            "facebook.com",
            "wikipedia.org"
    };

    public static Configurations configurations;
    public static String rulePath;
    public static String logPath;

    private static Daedalus instance;
    private SharedPreferences prefs;
    private Thread mResolver;

    @Override
    public void onCreate() {
        super.onCreate();

        instance = this;
        Logger.init();
        mResolver = new Thread(new RuleResolver());
        mResolver.start();
        initData();
        Logger.installCrashHandler();
        SocksProxy.installAuthenticator();
    }

    private void initDirectory(String dir) {
        File directory = new File(dir);
        if (!directory.isDirectory()) {
            Logger.warning(dir + " is not a directory. Delete result: " + directory.delete());
        }
        if (!directory.exists()) {
            Logger.debug(dir + " does not exist. Create result: " + directory.mkdirs());
        }
    }

    private void initData() {
        PreferenceManager.setDefaultValues(this, R.xml.perf_settings, false);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // "dns_test_servers" used to default to the VPN alias addresses, which only answer
        // while the VPN is running. Installs that still carry that value get the new default once.
        if ("10.0.0.2,10.0.0.3".equals(prefs.getString("dns_test_servers", ""))) {
            prefs.edit().putString("dns_test_servers", getString(R.string.default_dns_test_servers)).apply();
        }
        // The dark theme used to be a switch; a switched-on one becomes the "dark" choice
        if (!prefs.contains("settings_theme") && prefs.getBoolean("settings_dark_theme", false)) {
            prefs.edit().putString("settings_theme", THEME_DARK).apply();
        }

        File legacyConfigFile = null;
        if (getExternalFilesDir(null) != null) {
            rulePath = getExternalFilesDir(null).getPath() + "/rules/";
            logPath = getExternalFilesDir(null).getPath() + "/logs/";
            legacyConfigFile = new File(getExternalFilesDir(null), "config.json");

            initDirectory(rulePath);
            initDirectory(logPath);
        }

        // Internal storage is always available, unlike the external files directory that
        // older versions used and that may be missing right after boot.
        configurations = Configurations.load(new File(getFilesDir(), "config.json"), legacyConfigFile);
        migrateProxySettings();
    }

    /**
     * The SOCKS5 proxy used to be configured once for all servers in the settings; it now
     * belongs to each custom server. Servers flagged as proxied by an older version get
     * the settings of that time copied in, then the old keys are dropped.
     */
    private void migrateProxySettings() {
        boolean changed = false;
        for (CustomDnsServer server : configurations.getCustomDNSServers()) {
            if (!server.hasLegacyProxyFlag()) {
                continue;
            }
            String host = prefs.getString("settings_socks5_host", SocksProxy.DEFAULT_HOST);
            if (host == null || host.trim().isEmpty()) {
                host = SocksProxy.DEFAULT_HOST;
            }
            int port = SocksProxy.DEFAULT_PORT;
            try {
                port = Integer.parseInt(prefs.getString("settings_socks5_port", String.valueOf(SocksProxy.DEFAULT_PORT)).trim());
            } catch (Exception ignored) {
            }
            server.setProxy(host, port, prefs.getString("settings_socks5_username", null),
                    prefs.getString("settings_socks5_password", null));
            changed = true;
        }
        if (changed) {
            configurations.save();
            Logger.info("Moved the SOCKS5 proxy settings into the proxied custom servers");
        }
        prefs.edit()
                .remove("settings_socks5_host").remove("settings_socks5_port")
                .remove("settings_socks5_username").remove("settings_socks5_password")
                .apply();
    }

    public static <T> T parseJson(Class<T> beanClass, JsonReader reader) throws JsonParseException {
        GsonBuilder builder = new GsonBuilder();
        Gson gson = builder.create();
        return gson.fromJson(reader, beanClass);
    }

    public static void initRuleResolver() {
        ArrayList<String> pendingLoad = new ArrayList<>();
        ArrayList<Rule> usingRules = configurations.getUsingRules();
        if (usingRules != null && usingRules.size() > 0) {
            for (Rule rule : usingRules) {
                if (rule.isUsing()) {
                    pendingLoad.add(rulePath + rule.getFileName());
                }
            }
            if (pendingLoad.size() > 0) {
                String[] arr = new String[pendingLoad.size()];
                pendingLoad.toArray(arr);
                switch (usingRules.get(0).getType()) {
                    case Rule.TYPE_HOSTS:
                        RuleResolver.startLoadHosts(arr);
                        break;
                    case Rule.TYPE_DNAMASQ:
                        RuleResolver.startLoadDnsmasq(arr);
                        break;
                }
            } else {
                RuleResolver.clear();
            }
        } else {
            RuleResolver.clear();
        }
    }

    public static void setRulesChanged() {
        if (DaedalusVpnService.isActivated()) {
            initRuleResolver();
        }
    }

    public static SharedPreferences getPrefs() {
        return getInstance().prefs;
    }

    public static final String THEME_AUTO = "auto";
    public static final String THEME_DARK = "dark";
    public static final String THEME_LIGHT = "light";

    /**
     * Whether the dark theme applies right now: the "settings_theme" choice, following
     * the system's night mode when set to auto.
     */
    public static boolean isDarkTheme() {
        return isDarkTheme(getInstance().getResources().getConfiguration());
    }

    public static boolean isDarkTheme(Configuration configuration) {
        String theme = getInstance().prefs.getString("settings_theme", THEME_AUTO);
        if (THEME_DARK.equals(theme)) {
            return true;
        }
        if (THEME_LIGHT.equals(theme)) {
            return false;
        }
        return (configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    @Override
    public void onTerminate() {
        super.onTerminate();

        instance = null;
        prefs = null;
        RuleResolver.shutdown();
        mResolver.interrupt();
        RuleResolver.clear();
        mResolver = null;
        Logger.shutdown();
    }

    public static Intent getServiceIntent(Context context) {
        return new Intent(context, DaedalusVpnService.class);
    }

    public static boolean switchService() {
        if (DaedalusVpnService.isActivated()) {
            deactivateService(instance);
            return false;
        } else {
            prepareAndActivateService(instance);
            return true;
        }
    }

    public static boolean prepareAndActivateService(Context context) {
        Intent intent = VpnService.prepare(context);
        if (intent != null) {
            return false;
        } else {
            activateService(context);
            return true;
        }
    }

    public static void activateService(Context context) {
        activateService(context, false);
    }

    public static void activateService(Context context, boolean forceForeground) {
        DaedalusVpnService.primaryServer = (AbstractDnsServer) DnsServerHelper.getServerById(DnsServerHelper.getPrimary()).clone();
        DaedalusVpnService.secondaryServer = (AbstractDnsServer) DnsServerHelper.getServerById(DnsServerHelper.getSecondary()).clone();
        try {
            if ((getInstance().prefs.getBoolean("settings_foreground", false) || forceForeground)
                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Logger.info("Starting foreground service");
                // The service must then call startForeground() promptly, see DaedalusVpnService
                context.startForegroundService(Daedalus.getServiceIntent(context)
                        .setAction(DaedalusVpnService.ACTION_ACTIVATE)
                        .putExtra(DaedalusVpnService.EXTRA_FOREGROUND, true));
            } else {
                Logger.info("Starting background service");
                context.startService(Daedalus.getServiceIntent(context).setAction(DaedalusVpnService.ACTION_ACTIVATE));
            }
        } catch (Exception e) {
            // Android 8+ refuses to start a service while the app is in the background, e.g.
            // from a quick settings tile on some systems; the user has to start it from the app
            Logger.logException(e);
        }
    }

    public static void deactivateService(Context context) {
        context.startService(getServiceIntent(context).setAction(DaedalusVpnService.ACTION_DEACTIVATE));
        context.stopService(getServiceIntent(context));
    }

    public static void updateShortcut(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            Logger.info("Updating shortcut");
            boolean activate = DaedalusVpnService.isActivated();
            String notice = activate ? context.getString(R.string.button_text_deactivate) : context.getString(R.string.button_text_activate);
            ShortcutInfo info = new ShortcutInfo.Builder(context, Daedalus.SHORTCUT_ID_ACTIVATE)
                    .setLongLabel(notice)
                    .setShortLabel(notice)
                    .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
                    .setIntent(new Intent(context, MainActivity.class).setAction(Intent.ACTION_VIEW)
                            .putExtra(MainActivity.LAUNCH_ACTION, activate ? MainActivity.LAUNCH_ACTION_DEACTIVATE : MainActivity.LAUNCH_ACTION_ACTIVATE))
                    .build();
            ShortcutManager shortcutManager = (ShortcutManager) context.getSystemService(SHORTCUT_SERVICE);
            shortcutManager.addDynamicShortcuts(Collections.singletonList(info));
        }
    }

    public static void donate() {
        openUri("https://qr.alipay.com/FKX04751EZDP0SQ0BOT137");
    }

    public static void openUri(String uri) {
        try {
            instance.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Logger.logException(e);
        }
    }

    public static Daedalus getInstance() {
        return instance;
    }
}
