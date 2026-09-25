package org.itxtech.daedalus.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.system.OsConstants;
import android.util.Log;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationCompat;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.R;
import org.itxtech.daedalus.activity.MainActivity;
import org.itxtech.daedalus.provider.DnsTransport;
import org.itxtech.daedalus.provider.UnifiedProvider;
import org.itxtech.daedalus.receiver.StatusBarBroadcastReceiver;
import org.itxtech.daedalus.server.AbstractDnsServer;
import org.itxtech.daedalus.server.DnsServer;
import org.itxtech.daedalus.server.DnsServerHelper;
import org.itxtech.daedalus.util.Logger;
import org.itxtech.daedalus.util.NetworkRules;
import org.itxtech.daedalus.util.RuleResolver;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

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
    /**
     * Set when the service is started with startForegroundService(): it then has to call
     * startForeground() right away, or the system kills the app after a few seconds.
     */
    public static final String EXTRA_FOREGROUND = "org.itxtech.daedalus.service.DaedalusVpnService.EXTRA_FOREGROUND";
    /**
     * Whether the running service is a foreground service; read back when the system
     * restarts the service after killing the process.
     */
    private static final String PREF_FOREGROUND = "service_foreground";

    /**
     * Notification id of the activated state. It must not be 0: {@code startForeground(0, n)}
     * reaches the system as {@code id == 0}, which is the same call {@code stopForeground()}
     * makes, so it stops the foreground state instead of starting it and never answers a
     * pending startForegroundService() request.
     */
    private static final int NOTIFICATION_ACTIVATED = 1;

    private static final String TAG = "DaedalusVpnService";
    private static final String CHANNEL_ID = "daedalus_channel_1";
    private static final String CHANNEL_NAME = "daedalus_channel";
    private static final String HTTPS = "https://";

    // Last octet of the two VPN DNS addresses, e.g. 10.0.0.2 and 10.0.0.3
    private static final int ALIAS_PRIMARY = 2;
    private static final int ALIAS_SECONDARY = 3;

    /**
     * Primary/secondary servers of the settings, set by Daedalus.activateService(). They are
     * used when no network rule matches.
     */
    public static AbstractDnsServer primaryServer;
    public static AbstractDnsServer secondaryServer;
    private static InetAddress aliasPrimary;
    private static InetAddress aliasSecondary;

    private NotificationCompat.Builder notification = null;
    private boolean foreground = false;
    private boolean running = false;
    private long lastUpdate = 0;
    private boolean statisticQuery;
    private UnifiedProvider provider;
    private ParcelFileDescriptor descriptor;
    private Thread mThread = null;
    /**
     * The VPN DNS addresses handled by the provider, advanced mode only. Both are
     * equivalent: every query tries the primary server first.
     */
    public HashSet<String> dnsAliases;
    private ConnectivityManager.NetworkCallback networkCallback = null;
    private ExecutorService selector = null;
    private final AtomicInteger selectionGeneration = new AtomicInteger();
    private volatile String currentSelection = null;
    private static boolean activated = false;
    private static volatile DaedalusVpnService instance = null;

    public static boolean isActivated() {
        return activated;
    }

    /**
     * Re-evaluates the network rules with the current servers, rules and settings, e.g.
     * after they were edited or imported while the VPN is running.
     */
    public static void notifyConfigurationChanged() {
        DaedalusVpnService service = instance;
        if (service != null && service.running) {
            service.currentSelection = null;
            service.scheduleApplyServers();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    private static int getPendingIntent(int flag) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE | flag : flag;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            // START_STICKY restart after the system killed the process: nothing is re-delivered
            // and the statics of the previous process are gone. Re-establish the VPN with the
            // servers of the settings; a service that ran in the foreground goes back there
            // right away, otherwise the system kills the app once more.
            Logger.warning("Service restarted by the system after the process was killed, re-activating the VPN");
            return activate(true, Daedalus.getPrefs().getBoolean(PREF_FOREGROUND, false));
        }
        String action = intent.getAction();
        if (ACTION_ACTIVATE.equals(action)) {
            return activate(false, intent.getBooleanExtra(EXTRA_FOREGROUND, false));
        }
        if (ACTION_DEACTIVATE.equals(action)) {
            stopThread();
        }
        return START_NOT_STICKY;
    }

    private int activate(boolean restart, boolean foregroundRequested) {
        if (mThread != null) {
            // Already running, e.g. the activate intent was delivered twice. A start that came
            // through startForegroundService() must still be answered with startForeground():
            // the request stays pending otherwise and the system kills the app a few seconds
            // later with an ANR.
            if (foregroundRequested) {
                promoteToForeground();
            }
            return START_STICKY;
        }
        activated = true;
        if (primaryServer == null || secondaryServer == null) {
            primaryServer = (AbstractDnsServer) DnsServerHelper.getServerById(DnsServerHelper.getPrimary()).clone();
            secondaryServer = (AbstractDnsServer) DnsServerHelper.getServerById(DnsServerHelper.getSecondary()).clone();
        }

        boolean showNotification = Daedalus.getPrefs().getBoolean("settings_notification", true);
        if (foregroundRequested) {
            promoteToForeground();
        }
        if (foreground) {
            this.notification.setContentTitle(getResources().getString(R.string.notice_activated));
        } else if (showNotification) {
            NotificationCompat.Builder builder = buildNotification();
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            manager.notify(NOTIFICATION_ACTIVATED, builder.build());
            this.notification = builder;
        }
        Daedalus.getPrefs().edit().putBoolean(PREF_FOREGROUND, foreground).apply();

        Daedalus.initRuleResolver();
        startThread();
        Daedalus.updateShortcut(getApplicationContext());
        if (!restart && MainActivity.getInstance() != null) {
            MainActivity.getInstance().startActivity(new Intent(getApplicationContext(), MainActivity.class)
                    .putExtra(MainActivity.LAUNCH_ACTION, MainActivity.LAUNCH_ACTION_SERVICE_DONE));
        }
        return START_STICKY;
    }

    /**
     * Answers a startForegroundService() request. Every such start must reach startForeground(),
     * or the system kills the app a few seconds later; the request is per start, so a service
     * that is already in the foreground has to answer it as well.
     */
    private void promoteToForeground() {
        if (foreground) {
            return;
        }
        NotificationCompat.Builder builder = buildNotification();
        try {
            // A foreground service always shows its notification
            startForeground(NOTIFICATION_ACTIVATED, builder.build());
            foreground = true;
            this.notification = builder;
        } catch (Exception e) {
            // Android 12+ refuses startForeground() from the background unless the system
            // itself asked for the service. The pending request cannot be answered then, and
            // waiting it out means being killed, so stop cleanly instead.
            Logger.warning("Cannot run in the foreground, stopping the service: " + e);
            stopThread();
        }
    }

    private NotificationCompat.Builder buildNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
            manager.createNotificationChannel(channel);
            builder = new NotificationCompat.Builder(this, CHANNEL_ID);
        } else {
            builder = new NotificationCompat.Builder(this);
        }

        Intent deactivateIntent = new Intent(StatusBarBroadcastReceiver.STATUS_BAR_BTN_DEACTIVATE_CLICK_ACTION);
        deactivateIntent.setClass(this, StatusBarBroadcastReceiver.class);
        Intent settingsIntent = new Intent(StatusBarBroadcastReceiver.STATUS_BAR_BTN_SETTINGS_CLICK_ACTION);
        settingsIntent.setClass(this, StatusBarBroadcastReceiver.class);
        PendingIntent pIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), getPendingIntent(PendingIntent.FLAG_UPDATE_CURRENT));
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
                                deactivateIntent, getPendingIntent(PendingIntent.FLAG_UPDATE_CURRENT)))
                .addAction(R.drawable.ic_settings, getResources().getString(R.string.action_settings),
                        PendingIntent.getBroadcast(this, 0,
                                settingsIntent, getPendingIntent(PendingIntent.FLAG_UPDATE_CURRENT)));
        return builder;
    }

    private void startThread() {
        if (this.mThread == null) {
            this.mThread = new Thread(this, "DaedalusVpn");
            this.running = true;
            this.mThread.start();
        }
    }

    @Override
    public void onDestroy() {
        stopThread();
        if (instance == this) {
            instance = null;
        }
    }

    private void stopThread() {
        Log.d(TAG, "stopThread");
        activated = false;
        Daedalus.getPrefs().edit().putBoolean(PREF_FOREGROUND, false).apply();
        boolean shouldRefresh = false;
        unregisterNetworkCallback();
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
            if (foreground) {
                stopForeground(true);
                foreground = false;
            }
            if (notification != null) {
                NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManager.cancel(NOTIFICATION_ACTIVATED);
                notification = null;
            }
            dnsAliases = null;
        } catch (Exception e) {
            Logger.logException(e);
        }
        stopSelf();

        if (shouldRefresh) {
            RuleResolver.clear();
            DnsServerHelper.clearCache();
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
        Logger.warning("VPN permission revoked: another VPN app took over, or the VPN was turned off in the system settings");
        stopThread();
    }

    /**
     * Without the advanced mode the system resolver talks plain DNS to the server's IP
     * itself: port, DoT, DoH, SOCKS5 proxy, certificates and network rules are not used.
     * A DoH address is reduced to its host name so that the service can still start.
     */
    private static String getPlainDnsHost(AbstractDnsServer server) {
        String host = server.getAddress();
        if (server.isHttpsServer()) {
            String parsed = Uri.parse(HTTPS + server.getAddress()).getHost();
            if (parsed != null) {
                host = parsed;
            }
            Logger.warning("Advanced mode is off: plain DNS to " + host + " is used instead of DoH " + server.getAddress());
        } else if (server.getPort() != DnsServer.DNS_SERVER_DEFAULT_PORT || server.isProxied()) {
            Logger.warning("Advanced mode is off: port, DoT and SOCKS5 settings of " + server.getRealName() + " are ignored");
        }
        return host;
    }

    private InetAddress addAlias(Builder builder, String format, int index) throws UnknownHostException {
        String alias = String.format(format, index);
        dnsAliases.add(alias);
        builder.addRoute(alias, 32);
        return InetAddress.getByName(alias);
    }

    @Override
    public void run() {
        try {
            Builder builder = new Builder()
                    .setSession("Daedalus")
                    .setConfigureIntent(PendingIntent.getActivity(this, 0,
                            new Intent(this, MainActivity.class).putExtra(MainActivity.LAUNCH_FRAGMENT, MainActivity.FRAGMENT_SETTINGS),
                            getPendingIntent(PendingIntent.FLAG_ONE_SHOT)));

            if (Daedalus.getPrefs().getBoolean("settings_app_filter_switch", false)) {
                ArrayList<String> apps = Daedalus.configurations.getAppObjects();
                if (apps.size() > 0) {
                    boolean mode = Daedalus.getPrefs().getBoolean("settings_app_filter_mode_switch", false);
                    for (String app : apps) {
                        try {
                            if (mode) {
                                builder.addDisallowedApplication(app);
                            } else {
                                builder.addAllowedApplication(app);
                            }
                            Logger.debug("Added app to list: " + app);
                        } catch (PackageManager.NameNotFoundException e) {
                            Logger.error("Package Not Found:" + app);
                        }
                    }
                }
            }

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

            try {
                InetAddress addr = Inet6Address.getByAddress(ipv6Template);
                Log.d(TAG, "configure: Adding IPv6 address" + addr);
                builder.addAddress(addr, 120);
            } catch (Exception e) {
                Logger.logException(e);
            }

            if (advanced) {
                // Two fixed DNS addresses so that the system resolver has a second one to retry
                // with; both lead to the same primary/secondary chain, which the network rules
                // may swap while the VPN is running
                dnsAliases = new HashSet<>();
                aliasPrimary = addAlias(builder, format, ALIAS_PRIMARY);
                aliasSecondary = addAlias(builder, format, ALIAS_SECONDARY);
            } else {
                aliasPrimary = InetAddress.getByName(getPlainDnsHost(primaryServer));
                aliasSecondary = InetAddress.getByName(getPlainDnsHost(secondaryServer));
            }

            Logger.info("Daedalus VPN service is listening on " + aliasPrimary.getHostAddress() + " and " + aliasSecondary.getHostAddress());
            builder.addDnsServer(aliasPrimary).addDnsServer(aliasSecondary);

            if (advanced) {
                builder.setBlocking(true);
                builder.allowFamily(OsConstants.AF_INET);
                builder.allowFamily(OsConstants.AF_INET6);
            }

            descriptor = builder.establish();
            Logger.info("Daedalus VPN service is started");

            if (advanced) {
                provider = new UnifiedProvider(descriptor, this);
                provider.start();
                applyServers();
                registerNetworkCallback();
                provider.process();
            } else {
                while (running) {
                    Thread.sleep(1000);
                }
            }
        } catch (InterruptedException ignored) {
        } catch (Exception e) {
            Logger.logException(e);
            MainActivity activity = MainActivity.getInstance();
            if (activity != null) {
                activity.runOnUiThread(() ->
                        new AlertDialog.Builder(activity)
                                .setTitle(R.string.error_occurred)
                                .setMessage(Logger.getExceptionMessage(e))
                                .setPositiveButton(android.R.string.ok, (d, id) -> {
                                })
                                .show());
            }
        } finally {
            stopThread();
        }
    }

    /**
     * Chooses the upstream servers for the current network (network rules, or the system
     * DNS when "Use system DNS as upstream" is on) and hands them to the provider.
     */
    private synchronized void applyServers() {
        UnifiedProvider current = provider;
        if (current == null || !running) {
            return;
        }
        ConnectivityManager manager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        Network network = NetworkRules.getUnderlyingNetwork(manager);
        NetworkRules.Selection selection = Daedalus.getPrefs().getBoolean("settings_use_system_dns", false)
                ? NetworkRules.systemDns(manager, network)
                : NetworkRules.select(manager, network);

        String description = describe(selection.primary) + ", " + describe(selection.secondary) + " (" + selection.description + ")";
        // The network is part of the state: the same servers on another network need new
        // socket bindings and fresh connections
        String state = description + " @ " + (network == null ? "no network" : network.toString());
        if (state.equals(currentSelection)) {
            return;
        }
        AbstractDnsServer primary = prepare(selection.primary, network);
        AbstractDnsServer secondary = prepare(selection.secondary, network);
        current.setServers(primary, secondary, selection.description, network);
        currentSelection = state;
        Logger.info("Upstream DNS: " + description + (network == null ? ", no network" : " via network " + network));
    }

    private static String describe(AbstractDnsServer server) {
        if (server == null) {
            return "none";
        }
        return server.getRealName() + (server.isProxied() ? " via SOCKS5" : "");
    }

    /**
     * Clones the server and resolves its host name on the underlying network, so that the
     * lookup does not go through the VPN itself. Proxied servers are resolved by the proxy.
     */
    private static AbstractDnsServer prepare(AbstractDnsServer server, Network network) {
        if (server == null) {
            return null;
        }
        AbstractDnsServer copy = (AbstractDnsServer) server.clone();
        if (copy.isProxied()) {
            return copy;
        }
        String host = copy.isHttpsServer() ? Uri.parse(HTTPS + copy.getAddress()).getHost() : copy.getAddress();
        if (host == null) {
            return copy;
        }
        if (network == null && !DnsTransport.isLiteral(host)) {
            // A lookup through the system resolver would go into the VPN and stall; the
            // server stays unresolved until a network shows up and the rules are re-applied
            Logger.warning("No network to resolve DNS server " + host + " on");
            return copy;
        }
        try {
            InetAddress[] addresses = network != null ? network.getAllByName(host) : InetAddress.getAllByName(host);
            if (copy.isHttpsServer()) {
                DnsServerHelper.domainCache.put(host, Arrays.asList(addresses));
            } else {
                copy.setHostAddress(addresses[0].getHostAddress());
            }
        } catch (UnknownHostException e) {
            Logger.warning("Cannot resolve DNS server " + host + ": " + e.getMessage());
        }
        return copy;
    }

    private void registerNetworkCallback() {
        ConnectivityManager manager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        selector = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "DnsServerSelection"));
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                scheduleApplyServers();
            }

            @Override
            public void onLost(Network network) {
                scheduleApplyServers();
            }

            @Override
            public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
                scheduleApplyServers();
            }

            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                // Fires when a network becomes validated (and on signal changes, which the
                // cheap unchanged-state check in applyServers absorbs)
                scheduleApplyServers();
            }
        };
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build();
        manager.registerNetworkCallback(request, networkCallback);
    }

    /**
     * Re-evaluates the network rules shortly after a network change; several changes in a
     * row (Wi-Fi joining, addresses arriving) collapse into one evaluation.
     */
    private void scheduleApplyServers() {
        ExecutorService executor = selector;
        if (executor == null) {
            return;
        }
        final int generation = selectionGeneration.incrementAndGet();
        try {
            executor.execute(() -> {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    return;
                }
                if (generation != selectionGeneration.get()) {
                    return;
                }
                try {
                    applyServers();
                } catch (Exception e) {
                    Logger.logException(e);
                }
            });
        } catch (Exception ignored) {
            // executor already shut down
        }
    }

    private void unregisterNetworkCallback() {
        if (networkCallback != null) {
            try {
                ConnectivityManager manager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                manager.unregisterNetworkCallback(networkCallback);
            } catch (Exception e) {
                Logger.logException(e);
            }
            networkCallback = null;
        }
        if (selector != null) {
            selector.shutdownNow();
            selector = null;
        }
        currentSelection = null;
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
                notification.setContentTitle(getResources().getString(R.string.notice_queries) + " " + provider.getDnsQueryTimes());
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
