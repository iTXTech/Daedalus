package org.itxtech.daedalus.service;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.R;
import org.itxtech.daedalus.activity.MainActivity;
import org.itxtech.daedalus.provider.Provider;
import org.itxtech.daedalus.provider.TcpProvider;
import org.itxtech.daedalus.provider.UdpProvider;
import org.itxtech.daedalus.receiver.StatusBarBroadcastReceiver;
import org.itxtech.daedalus.util.Logger;
import org.itxtech.daedalus.util.RulesResolver;
import org.itxtech.daedalus.util.server.DNSServerHelper;

import java.net.InetAddress;
import java.util.HashMap;

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
public class DaedalusVpnService extends VpnService implements Runnable {
    public static final String ACTION_ACTIVATE = "org.itxtech.daedalus.service.DaedalusVpnService.ACTION_ACTIVATE";
    public static final String ACTION_DEACTIVATE = "org.itxtech.daedalus.service.DaedalusVpnService.ACTION_DEACTIVATE";

    private static final int NOTIFICATION_ACTIVATED = 0;

    private static final String TAG = "DaedalusVpnService";

    public static String primaryServer;
    public static String secondaryServer;

    private NotificationCompat.Builder notification = null;

    private boolean running = false;
    private long lastUpdate = 0;
    private boolean statisticQuery;
    private Provider provider;
    private ParcelFileDescriptor descriptor;

    private Thread mThread = null;

    public HashMap<String, String> dnsServers;

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            switch (intent.getAction()) {
                case ACTION_ACTIVATE:
                    if (Daedalus.getPrefs().getBoolean("settings_notification", true)) {

                        NotificationManager manager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);

                        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);

                        Intent nIntent = new Intent(this, MainActivity.class);
                        PendingIntent pIntent = PendingIntent.getActivity(this, 0, nIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                        builder.setWhen(0)
                                .setContentTitle(getResources().getString(R.string.notification_activated))
                                .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
                                .setSmallIcon(R.drawable.ic_security)
                                .setColor(getResources().getColor(R.color.colorPrimary)) //backward compatibility
                                .setAutoCancel(false)
                                .setOngoing(true)
                                .setTicker(getResources().getString(R.string.notification_activated))
                                .setContentIntent(pIntent)
                                .addAction(R.drawable.ic_clear, getResources().getString(R.string.button_text_deactivate),
                                        PendingIntent.getBroadcast(this, 0,
                                                new Intent(StatusBarBroadcastReceiver.STATUS_BAR_BTN_DEACTIVATE_CLICK_ACTION), 0))
                                .addAction(R.drawable.ic_settings, getResources().getString(R.string.action_settings),
                                        PendingIntent.getBroadcast(this, 0,
                                                new Intent(StatusBarBroadcastReceiver.STATUS_BAR_BTN_SETTINGS_CLICK_ACTION), 0));

                        Notification notification = builder.build();

                        manager.notify(NOTIFICATION_ACTIVATED, notification);

                        this.notification = builder;
                    }

                    Daedalus.initHostsResolver();
                    DNSServerHelper.buildPortCache();

                    if (this.mThread == null) {
                        this.mThread = new Thread(this, "DaedalusVpn");
                        this.running = true;
                        this.mThread.start();
                    }
                    Daedalus.updateShortcut(this.getApplicationContext());
                    return START_STICKY;
                case ACTION_DEACTIVATE:
                    stopThread();
                    return START_NOT_STICKY;
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        stopThread();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void stopThread() {
        Log.d(TAG, "stopThread");
        boolean shouldRefresh = false;
        try {
            if (this.descriptor != null) {
                this.descriptor.close();
                this.descriptor = null;
            }
            if (mThread != null) {
                running = false;
                shouldRefresh = true;
                if (provider != null) {
                    provider.shutdown();
                    mThread.interrupt();
                    provider.stop();
                } else {
                    mThread.interrupt();
                }
                mThread = null;
            }
            if (notification != null) {
                NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManager.cancel(NOTIFICATION_ACTIVATED);
                notification = null;
            }
            dnsServers = null;
        } catch (Exception e) {
            Logger.logException(e);
        }
        stopSelf();

        if (shouldRefresh) {
            RulesResolver.clear();
            DNSServerHelper.clearPortCache();
            Logger.info("Daedalus VPN service has stopped");
        }

        if (shouldRefresh && MainActivity.getInstance() != null && Daedalus.getInstance().isAppOnForeground()) {
            MainActivity.getInstance().startActivity(new Intent(getApplicationContext(), MainActivity.class)
                    .putExtra(MainActivity.LAUNCH_ACTION, MainActivity.LAUNCH_ACTION_AFTER_DEACTIVATE));
        } else if (shouldRefresh) {
            Daedalus.updateShortcut(getApplicationContext());
        }
    }


    @Override
    public void onRevoke() {
        stopThread();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void run() {
        try {
            Builder builder = new Builder();
            String format = null;
            for (String prefix : new String[]{"10.0.0", "192.0.2", "198.51.100", "203.0.113", "192.168.50"}) {
                try {
                    builder.addAddress(prefix + ".1", 24);
                } catch (IllegalArgumentException e) {
                    continue;
                }

                format = prefix + ".%d";
                break;
            }

            boolean advanced = Daedalus.getPrefs().getBoolean("settings_advanced_switch", false);
            statisticQuery = Daedalus.getPrefs().getBoolean("settings_count_query_times", false);

            String aliasPrimary;
            String aliasSecondary;
            if (advanced) {
                dnsServers = new HashMap<>();
                aliasPrimary = String.format(format, 2);
                dnsServers.put(aliasPrimary, primaryServer);
                aliasSecondary = String.format(format, 3);
                dnsServers.put(aliasSecondary, secondaryServer);
            } else {
                aliasPrimary = primaryServer;
                aliasSecondary = secondaryServer;
            }

            InetAddress primaryDNSServer = InetAddress.getByName(aliasPrimary);
            InetAddress secondaryDNSServer = InetAddress.getByName(aliasSecondary);
            Logger.info("Daedalus VPN service is listening on " + primaryDNSServer.getHostAddress() + " and " + secondaryDNSServer.getHostAddress());
            builder.setSession("Daedalus")
                    .addDnsServer(primaryDNSServer)
                    .addDnsServer(secondaryDNSServer)
                    .setConfigureIntent(PendingIntent.getActivity(this, 0,
                            new Intent(this, MainActivity.class).putExtra(MainActivity.LAUNCH_FRAGMENT, MainActivity.FRAGMENT_SETTINGS),
                            PendingIntent.FLAG_ONE_SHOT));

            if (advanced) {
                builder.addRoute(primaryDNSServer, primaryDNSServer.getAddress().length * 8)
                        .addRoute(secondaryDNSServer, secondaryDNSServer.getAddress().length * 8)
                        .setBlocking(true);
            }

            descriptor = builder.establish();
            Logger.info("Daedalus VPN service is started");

            if (advanced) {
                if (Daedalus.getPrefs().getBoolean("settings_dns_over_tcp", false)) {
                    provider = new TcpProvider(descriptor, this);
                } else {
                    provider = new UdpProvider(descriptor, this);
                }
                provider.start();
                provider.process();
            } else {
                while (running) {
                    Thread.sleep(1000);
                }
            }
        } catch (InterruptedException ignored) {
        } catch (Exception e) {
            Logger.logException(e);
        } finally {
            Log.d(TAG, "quit");
            stopThread();
        }
    }

    public void providerLoopCallback() {
        if (statisticQuery) {
            updateUserInterface();
        }
    }

    private void updateUserInterface() {
        long time = System.currentTimeMillis();
        if (time - lastUpdate >= 1000) {
            lastUpdate = time;
            if (notification != null) {
                notification.setContentTitle(getResources().getString(R.string.notification_queries) + " " + String.valueOf(provider.getDnsQueryTimes()));
                NotificationManager manager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
                manager.notify(NOTIFICATION_ACTIVATED, notification.build());
            }
        }
    }


    public static class VpnNetworkException extends Exception {
        public VpnNetworkException(String s) {
            super(s);
        }

        public VpnNetworkException(String s, Throwable t) {
            super(s, t);
        }

    }

}
