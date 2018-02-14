package org.itxtech.daedalus;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;
import com.google.firebase.crash.FirebaseCrash;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.stream.JsonReader;
import org.itxtech.daedalus.activity.MainActivity;
import org.itxtech.daedalus.service.DaedalusVpnService;
import org.itxtech.daedalus.util.Configurations;
import org.itxtech.daedalus.util.Logger;
import org.itxtech.daedalus.util.Rule;
import org.itxtech.daedalus.util.RuleResolver;
import org.itxtech.daedalus.util.server.DNSServer;
import org.itxtech.daedalus.util.server.DNSServerHelper;

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
    static {
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                FirebaseCrash.report(e);
            }
        });
    }

    private static final String SHORTCUT_ID_ACTIVATE = "shortcut_activate";

    public static final List<DNSServer> DNS_SERVERS = new ArrayList<DNSServer>() {{
        add(new DNSServer("119.23.248.241", R.string.server_fundns_south_china));
        add(new DNSServer("101.132.183.99", R.string.server_pdomo_primary));
        add(new DNSServer("193.112.15.186", R.string.server_pdomo_secondary));
        add(new DNSServer("115.159.146.99", R.string.server_aixyz_east_china));
        add(new DNSServer("123.206.21.48", R.string.server_aixyz_south_china));
    }};

    public static final List<Rule> RULES = new ArrayList<Rule>() {{
        //Build-in Hosts rule providers
        add(new Rule("googlehosts/hosts", "googlehosts.hosts", Rule.TYPE_HOSTS,
                "https://raw.githubusercontent.com/googlehosts/hosts/master/hosts-files/hosts", false));
        add(new Rule("vokins/yhosts", "vokins.hosts", Rule.TYPE_HOSTS,
                "https://raw.githubusercontent.com/vokins/yhosts/master/hosts", false));
        add(new Rule("adaway", "adaway.hosts", Rule.TYPE_HOSTS,
                "https://adaway.org/hosts.txt", false));
        //Build-in DNSMasq rule providers
        add(new Rule("vokins/yhosts/union", "union.dnsmasq", Rule.TYPE_DNAMASQ,
                "https://raw.githubusercontent.com/vokins/yhosts/master/dnsmasq/union.conf", false));
    }};

    public static final String[] DEFAULT_TEST_DOMAINS = new String[]{
            "google.com",
            "twitter.com",
            "youtube.com",
            "facebook.com",
            "wikipedia.org"
    };

    public static Configurations configurations;

    public static String rulePath = null;
    public static String logPath = null;
    private static String configPath = null;

    private static Daedalus instance = null;
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
    }

    private void initDirectory(String dir) {
        File directory = new File(dir);
        if (!directory.isDirectory()) {
            Logger.warning(dir + " is not a directory. Delete result: " + String.valueOf(directory.delete()));
        }
        if (!directory.exists()) {
            Logger.debug(dir + " does not exist. Create result: " + String.valueOf(directory.mkdirs()));
        }
    }

    private void initData() {
        PreferenceManager.setDefaultValues(this, R.xml.perf_settings, false);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        if (getExternalFilesDir(null) != null) {
            rulePath = getExternalFilesDir(null).getPath() + "/rules/";
            logPath = getExternalFilesDir(null).getPath() + "/logs/";
            configPath = getExternalFilesDir(null).getPath() + "/config.json";

            initDirectory(rulePath);
            initDirectory(logPath);
        }

        if (configPath != null) {
            configurations = Configurations.load(new File(configPath));
        } else {
            configurations = new Configurations();
        }
    }

    public static <T> T parseJson(Class<T> beanClass, JsonReader reader) throws JsonParseException {
        GsonBuilder builder = new GsonBuilder();
        Gson gson = builder.create();
        return gson.fromJson(reader, beanClass);
    }

    public static void initRuleResolver() {
        if (Daedalus.getPrefs().getBoolean("settings_local_rules_resolution", false)) {
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
    }

    public static void setRulesChanged() {
        if (DaedalusVpnService.isActivated() &&
                getPrefs().getBoolean("settings_allow_dynamic_rule_reload", false)) {
            initRuleResolver();
        }
    }

    public static SharedPreferences getPrefs() {
        return getInstance().prefs;
    }

    public static boolean isDarkTheme() {
        return getInstance().prefs.getBoolean("settings_dark_theme", false);
    }

    @Override
    public void onTerminate() {
        Log.d("Daedalus", "onTerminate");
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
            activateService(instance);
            return true;
        }
    }

    public static boolean activateService(Context context) {
        Intent intent = VpnService.prepare(context);
        if (intent != null) {
            return false;
        } else {
            DaedalusVpnService.primaryServer = DNSServerHelper.getAddressById(DNSServerHelper.getPrimary());
            DaedalusVpnService.secondaryServer = DNSServerHelper.getAddressById(DNSServerHelper.getSecondary());
            context.startService(Daedalus.getServiceIntent(context).setAction(DaedalusVpnService.ACTION_ACTIVATE));
            return true;
        }
    }

    public static void deactivateService(Context context) {
        context.startService(getServiceIntent(context).setAction(DaedalusVpnService.ACTION_DEACTIVATE));
        context.stopService(getServiceIntent(context));
    }

    public static void updateShortcut(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            Log.d("Daedalus", "Updating shortcut");
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
        openUri("https://qr.alipay.com/a6x07022gffiehykicipv1a");
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
