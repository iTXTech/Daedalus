package org.itxtech.daedalus;

import android.Manifest;
import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import org.itxtech.daedalus.activity.MainActivity;
import org.itxtech.daedalus.service.DaedalusVpnService;
import org.itxtech.daedalus.util.DnsServer;
import org.itxtech.daedalus.util.HostsProvider;
import org.itxtech.daedalus.util.HostsResolver;

import java.util.ArrayList;
import java.util.Arrays;
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
    private static final String SHORTCUT_ID_ACTIVATE = "shortcut_activate";

    public static final List<DnsServer> DNS_SERVERS = new ArrayList<DnsServer>() {{
        /*add(new DnsServer("0", "113.107.249.56", R.string.server_cutedns_north_china));
        add(new DnsServer("1", "120.27.103.230", R.string.server_cutedns_east_china));
        add(new DnsServer("2", "123.206.61.167", R.string.server_cutedns_south_china));*/
        add(new DnsServer("0", "115.159.220.214", R.string.server_puredns_east_china));
        add(new DnsServer("1", "123.207.137.88", R.string.server_puredns_north_china));
        add(new DnsServer("2", "115.159.146.99", R.string.server_aixyz_east_china));
        add(new DnsServer("3", "123.206.21.48", R.string.server_aixyz_south_china));
    }};

    public static final List<HostsProvider> HOSTS_PROVIDERS = new ArrayList<HostsProvider>() {{
        add(new HostsProvider("racaljk", "https://coding.net/u/scaffrey/p/hosts/git/raw/master/hosts"));
    }};

    public static final String[] DEFAULT_TEST_DOMAINS = new String[]{
            "google.com",
            "twitter.com",
            "youtube.com",
            "facebook.com",
            "wikipedia.org"
    };

    private static Daedalus instance = null;
    private static SharedPreferences prefs;
    private static Thread mHostsResolver;

    @Override
    public void onCreate() {
        super.onCreate();

        initConfig();
        mHostsResolver = new Thread(new HostsResolver());
        mHostsResolver.start();

        instance = this;
    }

    private void initConfig() {
        PreferenceManager.setDefaultValues(this, R.xml.perf_settings, false);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
    }

    public static final int REQUEST_EXTERNAL_STORAGE = 1;
    public static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    public static void initHostsResolver() {
        if (Daedalus.getPrefs().getBoolean("settings_local_host_resolve", false)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (MainActivity.getInstance() != null) {
                    int permission = ActivityCompat.checkSelfPermission(Daedalus.getInstance(), Manifest.permission.WRITE_EXTERNAL_STORAGE);
                    if (permission != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(MainActivity.getInstance(), PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);
                    }
                } else {
                    return;
                }
            }
            HostsResolver.startLoad(instance.getExternalFilesDir(null).getPath() + "/hosts");
        }
    }
    public static SharedPreferences getPrefs() {
        return prefs;
    }

    @Override
    public void onTerminate() {
        super.onTerminate();

        instance = null;
        prefs = null;
        HostsResolver.shutdown();
        mHostsResolver.interrupt();
        HostsResolver.clean();
        mHostsResolver = null;
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
                    .setIntent(new Intent(context, MainActivity.class).setAction(Intent.ACTION_VIEW).putExtra(MainActivity.LAUNCH_ACTION, activate ? MainActivity.LAUNCH_ACTION_ACTIVATE : MainActivity.LAUNCH_ACTION_DEACTIVATE))
                    .build();

            ShortcutManager shortcutManager = (ShortcutManager) context.getSystemService(SHORTCUT_SERVICE);
            shortcutManager.addDynamicShortcuts(Arrays.asList(info));
        }
    }

    public static Daedalus getInstance() {
        return instance;
    }
}
