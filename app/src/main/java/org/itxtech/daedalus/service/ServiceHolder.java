package org.itxtech.daedalus.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationCompat;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.R;
import org.itxtech.daedalus.activity.MainActivity;
import org.itxtech.daedalus.receiver.StatusBarBroadcastReceiver;
import org.itxtech.daedalus.server.AbstractDnsServer;
import org.itxtech.daedalus.server.DnsServerHelper;
import org.itxtech.daedalus.util.Logger;

import java.net.InetSocketAddress;
import java.net.URI;

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
    public static final int NOTIFICATION_ACTIVATED = 2;
    public static final String CHANNEL_ID = "daedalus_channel";
    public static final String CHANNEL_NAME = "Daedalus";
    public static final String ACTION_ACTIVATE = "ACTION_ACTIVATE";
    public static final String ACTION_DEACTIVATE = "ACTION_DEACTIVATE";

    private static boolean running = false;
    private static NotificationCompat.Builder builder = null;

    public static AbstractDnsServer primaryServer;
    public static AbstractDnsServer secondaryServer;
    public static InetSocketAddress serverAddr;
    private static boolean foreground = false;

    public static boolean isForeground() {
        return foreground;
    }

    public static NotificationCompat.Builder getBuilder() {
        return builder;
    }

    public static void buildNotification(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new NotificationCompat.Builder(context, CHANNEL_ID);
        } else {
            builder = new NotificationCompat.Builder(context);
        }

        Intent deactivateIntent = new Intent(StatusBarBroadcastReceiver.STATUS_BAR_BTN_DEACTIVATE_CLICK_ACTION);
        deactivateIntent.setClass(context, StatusBarBroadcastReceiver.class);
        Intent settingsIntent = new Intent(StatusBarBroadcastReceiver.STATUS_BAR_BTN_SETTINGS_CLICK_ACTION);
        settingsIntent.setClass(context, StatusBarBroadcastReceiver.class);
        PendingIntent pIntent = PendingIntent.getActivity(context, 0,
                new Intent(context, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setWhen(0)
                .setContentTitle(context.getString(R.string.notice_activated))
                .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
                .setSmallIcon(R.drawable.ic_security)
                .setColor(context.getResources().getColor(R.color.colorPrimary))
                .setAutoCancel(false)
                .setOngoing(true)
                .setTicker(context.getString(R.string.notice_activated))
                .setContentIntent(pIntent)
                .addAction(R.drawable.ic_clear, context.getString(R.string.button_text_deactivate),
                        PendingIntent.getBroadcast(context, 0,
                                deactivateIntent, PendingIntent.FLAG_UPDATE_CURRENT))
                .addAction(R.drawable.ic_settings, context.getString(R.string.action_settings),
                        PendingIntent.getBroadcast(context, 0,
                                settingsIntent, PendingIntent.FLAG_UPDATE_CURRENT));
    }

    public static void setRunning(boolean running, Context context) {
        ServiceHolder.running = running;
        if (running) {
            if (Daedalus.getPrefs().getBoolean("settings_notification", true) && (!isServerMode() || !isForeground())) {
                NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
                    manager.createNotificationChannel(channel);
                }
                buildNotification(context);
                manager.notify(NOTIFICATION_ACTIVATED, builder.build());
            }

            Daedalus.initRuleResolver();
            Daedalus.updateShortcut(Daedalus.getInstance());
            if (MainActivity.getInstance() != null) {
                MainActivity.getInstance().startActivity(new Intent(Daedalus.getInstance(), MainActivity.class)
                        .putExtra(MainActivity.LAUNCH_ACTION, MainActivity.LAUNCH_ACTION_SERVICE_DONE));
            }
        } else {
            if (builder != null) {
                NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManager.cancel(NOTIFICATION_ACTIVATED);
                builder = null;
            }
        }
    }

    public static void updateNotification(Context context, long times) {
        if (builder != null) {
            builder.setContentTitle(context.getString(R.string.notice_queries) + " " + times);
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            manager.notify(NOTIFICATION_ACTIVATED, builder.build());
        }
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
        serverAddr = new InetSocketAddress("127.0.0.1", 5353);
        try {
            URI uri = new URI("my://" + Daedalus.getPrefs().getString("settings_server_addr", "127.0.0.1:5353"));
            String host = uri.getHost();
            int port = uri.getPort();
            if (uri.getHost() != null && uri.getPort() != -1) {
                serverAddr = new InetSocketAddress(host, port);
            }
        } catch (Exception e) {
            Logger.logException(e);
        }
        primaryServer = DnsServerHelper.getServerById(DnsServerHelper.getPrimary()).clone();
        secondaryServer = DnsServerHelper.getServerById(DnsServerHelper.getSecondary()).clone();
        if ((Daedalus.getPrefs().getBoolean("settings_foreground", false) || forceForeground)
                && Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
            ServiceHolder.foreground = true;
            Logger.info("Starting foreground service");
            context.startForegroundService(getServiceIntent(context).setAction(ACTION_ACTIVATE));
        } else {
            ServiceHolder.foreground = false;
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
