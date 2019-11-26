package org.itxtech.daedalus.service;

import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import androidx.appcompat.app.AlertDialog;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.activity.MainActivity;
import org.itxtech.daedalus.server.AbstractDnsServer;
import org.itxtech.daedalus.server.DnsServerHelper;
import org.itxtech.daedalus.util.Logger;

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
public class ServiceHolder {
    public static final String ACTION_ACTIVATE = "ACTION_ACTIVATE";
    public static final String ACTION_DEACTIVATE = "ACTION_DEACTIVATE";

    private static boolean running = false;

    public static AbstractDnsServer primaryServer;
    public static AbstractDnsServer secondaryServer;

    public static void setRunning(boolean running) {
        ServiceHolder.running = running;
    }

    public static boolean isRunning() {
        return running;
    }

    public static boolean isServerMode() {
        return Daedalus.getPrefs().getBoolean("settings_server_mode", false);
    }

    public static Intent getServiceIntent(Context context) {
        return new Intent(context, isServerMode() ? DaedalusServerService.class : DaedalusVpnService.class);
    }

    public static boolean switchService() {
        if (running) {
            stopService(Daedalus.getInstance());
            return false;
        } else {
            prepareAndStartService(Daedalus.getInstance());
            return true;
        }
    }

    public static boolean prepareAndStartService(Context context) {
        Intent intent = VpnService.prepare(context);
        if (intent != null) {
            return false;
        } else {
            startService(context);
            return true;
        }
    }

    public static void startService(Context context) {
        startService(context, false);
    }

    public static void startService(Context context, boolean forceForeground) {
        primaryServer = DnsServerHelper.getServerById(DnsServerHelper.getPrimary()).clone();
        secondaryServer = DnsServerHelper.getServerById(DnsServerHelper.getSecondary()).clone();
        if ((Daedalus.getPrefs().getBoolean("settings_foreground", false) || forceForeground)
                && Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
            Logger.info("Starting foreground service");
            context.startForegroundService(getServiceIntent(context).setAction(ACTION_ACTIVATE));
        } else {
            Logger.info("Starting background service");
            context.startService(getServiceIntent(context).setAction(ACTION_ACTIVATE));
        }

        Daedalus.updateShortcut(Daedalus.getInstance());

        long activateCounter = Daedalus.configurations.getActivateCounter();
        if (activateCounter == -1) {
            return;
        }
        activateCounter++;
        Daedalus.configurations.setActivateCounter(activateCounter);
        if (MainActivity.getInstance() != null && activateCounter % 10 == 0) {
            new AlertDialog.Builder(MainActivity.getInstance())
                    .setTitle("觉得还不错？")
                    .setMessage("您的支持是我动力来源！\n请考虑为我买杯咖啡醒醒脑，甚至其他…… ;)")
                    .setPositiveButton("为我买杯咖啡", (dialog, which) -> {
                        Daedalus.donate();
                        new AlertDialog.Builder(MainActivity.getInstance())
                                .setMessage("感谢您的支持！;)\n我会再接再厉！")
                                .setPositiveButton("确认", null)
                                .show();
                    })
                    .setNeutralButton("不再显示", (dialog, which) -> Daedalus.configurations.setActivateCounter(-1))
                    .setNegativeButton("取消", null)
                    .show();
        }
    }

    public static void stopService(Context context) {
        context.startService(getServiceIntent(context).setAction(ACTION_DEACTIVATE));
        context.stopService(getServiceIntent(context));
    }
}
