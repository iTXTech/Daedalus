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
import android.support.v4.app.NotificationCompat;
import android.system.OsConstants;
import android.util.Log;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.R;
import org.itxtech.daedalus.activity.MainActivity;
import org.itxtech.daedalus.provider.Provider;
import org.itxtech.daedalus.provider.TcpProvider;
import org.itxtech.daedalus.provider.UdpProvider;
import org.itxtech.daedalus.receiver.StatusBarBroadcastReceiver;
import org.itxtech.daedalus.util.Logger;
import org.itxtech.daedalus.util.RuleResolver;
import org.itxtech.daedalus.util.server.DNSServerHelper;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
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

    private static boolean activated = false;

    public static boolean isActivated() {
        return activated;
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            switch (intent.getAction()) {
                case ACTION_ACTIVATE:
                    activated = true;
                    if (Daedalus.getPrefs().getBoolean("settings_notification", true)) {

                        NotificationManager manager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);

                        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);

                        Intent nIntent = new Intent(this, MainActivity.class);
                        PendingIntent pIntent = PendingIntent.getActivity(this, 0, nIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                        builder.setWhen(0)
                                .setContentTitle(getResources().getString(R.string.notice_activated))
                                .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
                                .setSmallIcon(R.drawable.ic_security)
                                .setColor(getResources().getColor(R.color.colorPrimary)) //backward compatibility
                                .setAutoCancel(false)
                                .setOngoing(true)
                                .setTicker(getResources().getString(R.string.notice_activated))
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

                    Daedalus.initRuleResolver();
                    DNSServerHelper.buildPortCache();

                    if (this.mThread == null) {
                        this.mThread = new Thread(this, "DaedalusVpn");
                        this.running = true;
                        this.mThread.start();
                    }
                    Daedalus.updateShortcut(getApplicationContext());
                    if (MainActivity.getInstance() != null) {
                        MainActivity.getInstance().startActivity(new Intent(getApplicationContext(), MainActivity.class)
                                .putExtra(MainActivity.LAUNCH_ACTION, MainActivity.LAUNCH_ACTION_SERVICE_DONE));
                    }
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
        activated = false;
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
            RuleResolver.clear();
            DNSServerHelper.clearPortCache();
            Logger.info("Daedalus VPN service has stopped");
        }

        if (shouldRefresh && MainActivity.getInstance() != null) {
            MainActivity.getInstance().startActivity(new Intent(getApplicationContext(), MainActivity.class)
                    .putExtra(MainActivity.LAUNCH_ACTION, MainActivity.LAUNCH_ACTION_SERVICE_DONE));
        } else if (shouldRefresh) {
            Daedalus.updateShortcut(getApplicationContext());
        }
    }

    @Override
    public void onRevoke() {
        stopThread();
    }

    private InetAddress addDnsServer(Builder builder, String format, byte[] ipv6Template, InetAddress address) throws UnknownHostException {
        int size = dnsServers.size();
        size++;
        if (address instanceof Inet6Address && ipv6Template == null) {
            Log.i(TAG, "addDnsServer: Ignoring DNS server " + address);
        } else if (address instanceof Inet4Address) {
            String alias = String.format(format, size + 1);
            dnsServers.put(alias, address.getHostAddress());
            builder.addRoute(alias, 32);
            return InetAddress.getByName(alias);
        } else if (address instanceof Inet6Address) {
            ipv6Template[ipv6Template.length - 1] = (byte) (size + 1);
            InetAddress i6addr = Inet6Address.getByAddress(ipv6Template);
            dnsServers.put(i6addr.getHostAddress(), address.getHostAddress());
            return i6addr;
        }
        return null;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void run() {
        try {
            Builder builder = new Builder()
                    .setSession("Daedalus")
                    .setConfigureIntent(PendingIntent.getActivity(this, 0,
                            new Intent(this, MainActivity.class).putExtra(MainActivity.LAUNCH_FRAGMENT, MainActivity.FRAGMENT_SETTINGS),
                            PendingIntent.FLAG_ONE_SHOT));
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
            byte[] ipv6Template = new byte[]{32, 1, 13, (byte) (184 & 0xFF), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

            if (primaryServer.contains(":") || secondaryServer.contains(":")) {//IPv6
                try {
                    InetAddress addr = Inet6Address.getByAddress(ipv6Template);
                    Log.d(TAG, "configure: Adding IPv6 address" + addr);
                    builder.addAddress(addr, 120);
                } catch (Exception e) {
                    Logger.logException(e);

                    ipv6Template = null;
                }
            } else {
                ipv6Template = null;
            }

            InetAddress aliasPrimary;
            InetAddress aliasSecondary;
            if (advanced) {
                dnsServers = new HashMap<>();
                aliasPrimary = addDnsServer(builder, format, ipv6Template, InetAddress.getByName(primaryServer));
                aliasSecondary = addDnsServer(builder, format, ipv6Template, InetAddress.getByName(secondaryServer));
            } else {
                aliasPrimary = InetAddress.getByName(primaryServer);
                aliasSecondary = InetAddress.getByName(secondaryServer);
            }

            InetAddress primaryDNSServer = aliasPrimary;
            InetAddress secondaryDNSServer = aliasSecondary;
            Logger.info("Daedalus VPN service is listening on " + primaryServer + " as " + primaryDNSServer.getHostAddress());
            Logger.info("Daedalus VPN service is listening on " + secondaryServer + " as " + secondaryDNSServer.getHostAddress());
            builder.addDnsServer(primaryDNSServer).addDnsServer(secondaryDNSServer);

            if (advanced) {
                builder.setBlocking(true);
                builder.allowFamily(OsConstants.AF_INET);
                builder.allowFamily(OsConstants.AF_INET6);
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
                notification.setContentTitle(getResources().getString(R.string.notice_queries) + " " + String.valueOf(provider.getDnsQueryTimes()));
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
