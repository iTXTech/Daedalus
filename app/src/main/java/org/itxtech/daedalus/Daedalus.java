package org.itxtech.daedalus;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.net.Uri;
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
import org.itxtech.daedalus.util.RulesResolver;
import org.itxtech.daedalus.util.server.DNSServer;

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
        add(new DNSServer("115.159.220.214", R.string.server_puredns_east_china));
        add(new DNSServer("123.207.137.88", R.string.server_puredns_north_china));
        add(new DNSServer("115.159.146.99", R.string.server_aixyz_east_china));
        add(new DNSServer("123.206.21.48", R.string.server_aixyz_south_china));
        add(new DNSServer("119.29.105.234", R.string.server_cutedns_south_china));
    }};

    public static final List<Rule> RULES = new ArrayList<Rule>() {{
        //Build-in Hosts rule providers
        add(new Rule("racaljk/hosts", "racaljk.hosts", Rule.TYPE_HOSTS,
                "https://coding.net/u/scaffrey/p/hosts/git/raw/master/hosts", false));
        add(new Rule("fengixng/google-hosts", "fengixng.hosts", Rule.TYPE_HOSTS,
                "https://raw.githubusercontent.com/fengixng/google-hosts/master/hosts", false));
        add(new Rule("sy618/hosts", "sy618.hosts", Rule.TYPE_HOSTS,
                "https://raw.githubusercontent.com/sy618/hosts/master/ADFQ", false));
        add(new Rule("vokins/yhosts", "vokins.hosts", Rule.TYPE_HOSTS,
                "https://raw.githubusercontent.com/vokins/yhosts/master/hosts", false));
        add(new Rule("lengers/connector", "connector.hosts", Rule.TYPE_HOSTS,
                "https://git.oschina.net/lengers/connector/raw/master/hosts", false));
        //Build-in DNSMasq rule providers
        add(new Rule("sy618/hosts/dnsad", "dnsad.dnsmasq", Rule.TYPE_DNAMASQ,
                "https://raw.githubusercontent.com/sy618/hosts/master/dnsmasq/dnsad", false));
        add(new Rule("sy618/hosts/dnsfq", "dnsfq.dnsmasq", Rule.TYPE_DNAMASQ,
                "https://raw.githubusercontent.com/sy618/hosts/master/dnsmasq/dnsfq", false));
    }};

    public static final String[] DEFAULT_TEST_DOMAINS = new String[]{
            "google.com",
            "twitter.com",
            "youtube.com",
            "facebook.com",
            "wikipedia.org"
    };

    public static Configurations configurations;

    public static String rulesPath = null;
    private static String configPath = null;

    private static Daedalus instance = null;
    private SharedPreferences prefs;
    private Thread mResolver;

    @Override
    public void onCreate() {
        super.onCreate();

        instance = this;

        Logger.init();

        mResolver = new Thread(new RulesResolver());
        mResolver.start();

        if (getExternalFilesDir(null) != null) {
            rulesPath = getExternalFilesDir(null).getPath() + "/rules/";
            configPath = getExternalFilesDir(null).getPath() + "/config.json";

            File configDir = new File(rulesPath);
            if (!configDir.isDirectory()) {
                Logger.warning("Configuration directory is not a directory. Delete result: " + String.valueOf(configDir.delete()));
            }
            if (!configDir.exists()) {
                Logger.debug("Configuration directory does not exist. Create result: " + String.valueOf(configDir.mkdirs()));
            }
        }

        initData();
    }

    private void initData() {
        PreferenceManager.setDefaultValues(this, R.xml.perf_settings, false);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

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

    public static void initHostsResolver() {
        if (Daedalus.getPrefs().getBoolean("settings_local_rules_resolution", false)) {
            ArrayList<String> pendingLoad = new ArrayList<>();
            ArrayList<Rule> usingRules = configurations.getUsingRules();
            if (usingRules != null && usingRules.size() > 0) {
                for (Rule rule : usingRules) {
                    if (rule.isUsing()) {
                        pendingLoad.add(rulesPath + rule.getFileName());
                    }
                }
                if (pendingLoad.size() > 0) {
                    String[] arr = new String[pendingLoad.size()];
                    pendingLoad.toArray(arr);
                    switch (usingRules.get(0).getType()) {
                        case Rule.TYPE_HOSTS:
                            RulesResolver.startLoadHosts(arr);
                            break;
                        case Rule.TYPE_DNAMASQ:
                            RulesResolver.startLoadDnsmasq(arr);
                            break;
                    }
                }
            }
        }
    }

    public static void setRulesChanged() {
        if (instance.isServiceActivated() &&
                getPrefs().getBoolean("settings_allow_dynamic_rule_reload", false)) {
            initHostsResolver();
        }
    }

    public static SharedPreferences getPrefs() {
        return getInstance().prefs;
    }

    @Override
    public void onTerminate() {
        Log.d("Daedalus", "onTerminate");
        super.onTerminate();

        instance = null;
        prefs = null;
        RulesResolver.shutdown();
        mResolver.interrupt();
        RulesResolver.clear();
        mResolver = null;
        Logger.shutdown();
    }

    public Intent getServiceIntent() {
        return new Intent(this, DaedalusVpnService.class);
    }


    public boolean isAppOnForeground() {
        // Returns a list of application processes that are running on the
        // device

        ActivityManager activityManager = (ActivityManager) getApplicationContext().getSystemService(Context.ACTIVITY_SERVICE);
        String packageName = getApplicationContext().getPackageName();

        List<ActivityManager.RunningAppProcessInfo> appProcesses = activityManager
                .getRunningAppProcesses();
        if (appProcesses == null)
            return false;

        for (ActivityManager.RunningAppProcessInfo appProcess : appProcesses) {
            // The name of the process that this object is associated with.
            if (appProcess.processName.equals(packageName)
                    && appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                return true;
            }
        }
        return false;
    }

    public void deactivateService() {
        startService(getServiceIntent().setAction(DaedalusVpnService.ACTION_DEACTIVATE));
        stopService(getServiceIntent());
    }

    public boolean isServiceActivated() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (DaedalusVpnService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public static void updateShortcut(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            Log.d("Daedalus", "Updating shortcut");
            //shortcut!
            String notice = context.getString(R.string.button_text_activate);
            boolean activate = true;
            ActivityManager manager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
            for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (DaedalusVpnService.class.getName().equals(service.service.getClassName())) {
                    notice = context.getString(R.string.button_text_deactivate);
                    activate = false;
                }
            }
            ShortcutInfo info = new ShortcutInfo.Builder(context, Daedalus.SHORTCUT_ID_ACTIVATE)
                    .setLongLabel(notice)
                    .setShortLabel(notice)
                    .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
                    .setIntent(new Intent(context, MainActivity.class).setAction(Intent.ACTION_VIEW)
                            .putExtra(MainActivity.LAUNCH_ACTION, activate ? MainActivity.LAUNCH_ACTION_ACTIVATE : MainActivity.LAUNCH_ACTION_DEACTIVATE))
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
