package org.itxtech.daedalus.service;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.system.OsConstants;
import android.util.Log;
import androidx.appcompat.app.AlertDialog;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.R;
import org.itxtech.daedalus.activity.MainActivity;
import org.itxtech.daedalus.provider.Provider;
import org.itxtech.daedalus.provider.ProviderPicker;
import org.itxtech.daedalus.server.AbstractDnsServer;
import org.itxtech.daedalus.server.DnsServer;
import org.itxtech.daedalus.server.DnsServerHelper;
import org.itxtech.daedalus.util.DnsServersDetector;
import org.itxtech.daedalus.util.Logger;
import org.itxtech.daedalus.util.RuleResolver;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
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

    private static final String TAG = "DaedalusVpnService";

    private static InetAddress aliasPrimary;
    private static InetAddress aliasSecondary;

    private long lastUpdate = 0;
    private boolean statisticQuery;
    private Provider provider;
    private ParcelFileDescriptor descriptor;
    private Thread mThread = null;
    public HashMap<String, AbstractDnsServer> dnsServers;
    private static BroadcastReceiver receiver;

    @Override
    public void onCreate() {
        super.onCreate();
        if (Daedalus.getPrefs().getBoolean("settings_use_system_dns", false)) {
            registerReceiver(receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    updateUpstreamServers(context);
                }
            }, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        }
    }

    private static void updateUpstreamServers(Context context) {
        String[] servers = DnsServersDetector.getServers(context);
        if (servers != null) {
            if (servers.length >= 2 && (aliasPrimary == null || !aliasPrimary.getHostAddress().equals(servers[0])) &&
                    (aliasSecondary == null || !aliasSecondary.getHostAddress().equals(servers[0])) &&
                    (aliasPrimary == null || !aliasPrimary.getHostAddress().equals(servers[1])) &&
                    (aliasSecondary == null || !aliasSecondary.getHostAddress().equals(servers[1]))) {
                ServiceHolder.primaryServer.setAddress(servers[0]);
                ServiceHolder.primaryServer.setPort(DnsServer.DNS_SERVER_DEFAULT_PORT);
                ServiceHolder.secondaryServer.setAddress(servers[1]);
                ServiceHolder.secondaryServer.setPort(DnsServer.DNS_SERVER_DEFAULT_PORT);
            } else if ((aliasPrimary == null || !aliasPrimary.getHostAddress().equals(servers[0])) &&
                    (aliasSecondary == null || !aliasSecondary.getHostAddress().equals(servers[0]))) {
                ServiceHolder.primaryServer.setAddress(servers[0]);
                ServiceHolder.primaryServer.setPort(DnsServer.DNS_SERVER_DEFAULT_PORT);
                ServiceHolder.secondaryServer.setAddress(servers[0]);
                ServiceHolder.secondaryServer.setPort(DnsServer.DNS_SERVER_DEFAULT_PORT);
            } else {
                StringBuilder buf = new StringBuilder();
                for (String server : servers) {
                    buf.append(server).append(" ");
                }
                Logger.error("Invalid upstream DNS " + buf);
            }
            Logger.info("Upstream DNS updated: " + ServiceHolder.primaryServer.getAddress() + " " + ServiceHolder.secondaryServer.getAddress());
        } else {
            Logger.error("Cannot obtain upstream DNS server!");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            switch (intent.getAction()) {
                case ServiceHolder.ACTION_ACTIVATE:
                    ServiceHolder.setRunning(true, this);
                    startThread();
                    return START_STICKY;
                case ServiceHolder.ACTION_DEACTIVATE:
                    stopThread();
                    return START_NOT_STICKY;
            }
        }
        return START_NOT_STICKY;
    }

    private void startThread() {
        if (this.mThread == null) {
            this.mThread = new Thread(this, "DaedalusVpn");
            this.mThread.start();
        }
    }

    @Override
    public void onDestroy() {
        stopThread();
        if (receiver != null) {
            unregisterReceiver(receiver);
            receiver = null;
        }
    }

    private void stopThread() {
        Log.d(TAG, "stopThread");
        ServiceHolder.setRunning(false, this);
        boolean shouldRefresh = false;
        try {
            if (this.descriptor != null) {
                this.descriptor.close();
                this.descriptor = null;
            }
            if (mThread != null) {
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
            dnsServers = null;
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
        stopThread();
    }

    private InetAddress addDnsServer(Builder builder, String format, byte[] ipv6Template, AbstractDnsServer addr)
            throws UnknownHostException {
        int size = dnsServers.size();
        size++;
        if (addr.getAddress().contains("/")) {//https uri
            String alias = String.format(format, size + 1);
            dnsServers.put(alias, addr);
            builder.addRoute(alias, 32);
            return InetAddress.getByName(alias);
        }
        InetAddress address = InetAddress.getByName(addr.getAddress());
        if (address instanceof Inet6Address && ipv6Template == null) {
            Log.i(TAG, "addDnsServer: Ignoring DNS server " + address);
        } else if (address instanceof Inet4Address) {
            String alias = String.format(format, size + 1);
            addr.setHostAddress(address.getHostAddress());
            dnsServers.put(alias, addr);
            builder.addRoute(alias, 32);
            return InetAddress.getByName(alias);
        } else if (address instanceof Inet6Address) {
            ipv6Template[ipv6Template.length - 1] = (byte) (size + 1);
            InetAddress i6addr = Inet6Address.getByAddress(ipv6Template);
            addr.setHostAddress(address.getHostAddress());
            dnsServers.put(i6addr.getHostAddress(), addr);
            return i6addr;
        }
        return null;
    }

    @Override
    public void run() {
        try {
            DnsServerHelper.buildCache();
            Builder builder = new Builder()
                    .setSession("Daedalus")
                    .setConfigureIntent(PendingIntent.getActivity(this, 0,
                            new Intent(this, MainActivity.class).putExtra(MainActivity.LAUNCH_FRAGMENT, MainActivity.FRAGMENT_SETTINGS),
                            PendingIntent.FLAG_ONE_SHOT));

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

                ipv6Template = null;
            }

            if (advanced) {
                dnsServers = new HashMap<>();
                aliasPrimary = addDnsServer(builder, format, ipv6Template, ServiceHolder.primaryServer);
                aliasSecondary = addDnsServer(builder, format, ipv6Template, ServiceHolder.secondaryServer);
            } else {
                aliasPrimary = InetAddress.getByName(ServiceHolder.primaryServer.getAddress());
                aliasSecondary = InetAddress.getByName(ServiceHolder.secondaryServer.getAddress());
            }

            Logger.info("Daedalus VPN service is listening on " + ServiceHolder.primaryServer.getAddress() + " as " + aliasPrimary.getHostAddress());
            Logger.info("Daedalus VPN service is listening on " + ServiceHolder.secondaryServer.getAddress() + " as " + aliasSecondary.getHostAddress());
            builder.addDnsServer(aliasPrimary).addDnsServer(aliasSecondary);

            if (advanced) {
                builder.setBlocking(true);
                builder.allowFamily(OsConstants.AF_INET);
                builder.allowFamily(OsConstants.AF_INET6);
            }

            descriptor = builder.establish();
            Logger.info("Daedalus VPN service is started");

            if (advanced) {
                provider = ProviderPicker.getProvider(descriptor, this);
                provider.start();
                provider.process();
            } else {
                while (ServiceHolder.isRunning()) {
                    Thread.sleep(1000);
                }
            }
        } catch (InterruptedException ignored) {
        } catch (Exception e) {
            MainActivity.getInstance().runOnUiThread(() ->
                    new AlertDialog.Builder(MainActivity.getInstance())
                            .setTitle(R.string.error_occurred)
                            .setMessage(Logger.getExceptionMessage(e))
                            .setPositiveButton(android.R.string.ok, (d, id) -> {
                            })
                            .show());
            Logger.logException(e);
        } finally {
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
            ServiceHolder.updateNotification(this, provider.getDnsQueryTimes());
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
