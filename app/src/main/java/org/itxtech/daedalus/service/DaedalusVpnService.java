package org.itxtech.daedalus.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import org.itxtech.daedalus.R;
import org.itxtech.daedalus.activity.MainActivity;
import org.itxtech.daedalus.receiver.StatusBarBroadcastReceiver;

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
public class DaedalusVpnService extends VpnService implements Runnable {
    public static final String ACTION_ACTIVATE = "org.itxtech.daedalus.service.DaedalusVpnService.ACTION_ACTIVATE";
    public static final String ACTION_DEACTIVATE = "org.itxtech.daedalus.service.DaedalusVpnService.ACTION_DEACTIVATE";

    public static String primaryServer;
    public static String secondaryServer;

    private Thread mThread = null;
    private static int ip = 0;
    private ParcelFileDescriptor descriptor;
    private boolean running = false;

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            switch (intent.getAction()) {
                case ACTION_ACTIVATE:
                    if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("settings_notification", true)) {

                        NotificationManager manager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);

                        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);

                        Intent nIntent = new Intent(this, MainActivity.class);
                        PendingIntent pIntent = PendingIntent.getActivity(this, 0, nIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                        builder.setContentTitle(getResources().getString(R.string.notification_activated))
                                .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
                                .setSmallIcon(R.mipmap.ic_launcher)
                                .setAutoCancel(true)
                                .setOngoing(true)
                                .setContentIntent(pIntent)
                                .addAction(R.mipmap.ic_launcher, getResources().getString(R.string.button_text_deactivate),
                                        PendingIntent.getBroadcast(this, 0,
                                                new Intent(StatusBarBroadcastReceiver.STATUS_BAR_BTN_DEACTIVATE_CLICK_ACTION), 0))
                                .addAction(R.mipmap.ic_launcher, getResources().getString(R.string.action_settings),
                                        PendingIntent.getBroadcast(this, 0,
                                                new Intent(StatusBarBroadcastReceiver.STATUS_BAR_BTN_SETTINGS_CLICK_ACTION), 0));

                        Notification notification = builder.build();
                        notification.flags = Notification.FLAG_NO_CLEAR;

                        manager.notify(0, notification);
                    }

                    if (this.mThread == null) {
                        this.mThread = new Thread(this, "DaedalusVpn");
                        this.running = true;
                        this.mThread.start();
                    }
                    return START_STICKY;
                case ACTION_DEACTIVATE:
                    stopThread();

                    NotificationManager notificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
                    notificationManager.cancelAll();

                    return START_NOT_STICKY;
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        stopThread();
    }

    private void stopThread() {
        try {
            if (this.descriptor != null) {
                this.descriptor.close();
                this.descriptor = null;
            }
            if (this.mThread != null) {
                this.running = false;
                this.mThread = null;
            }
        } catch (Exception e) {
            Log.d("DVpn", e.toString());
        }
        stopSelf();
    }

    @Override
    public void onRevoke() {
        stopThread();
    }

    @Override
    public void run() {
        try {
            int i;
            Builder builder = new Builder();
            StringBuilder stringBuilder = new StringBuilder("10.0.0.");
            if (ip == 254) {
                ip = 0;
                i = 0;
            } else {
                i = ip;
                ip = i + 1;
            }
            String ipAdd = stringBuilder.append(i).toString();
            if (this.descriptor != null) {
                this.descriptor.close();
            }

            builder.addAddress(ipAdd, 24);
            Log.d("DVpn", "tun0 add " + ipAdd + " pServ " + primaryServer + " sServ " + secondaryServer);
            builder.addDnsServer(primaryServer).addDnsServer(secondaryServer);
            this.descriptor = builder.setSession("DVpn").establish();

            while (running) {
                Thread.sleep(1000);
            }
        } catch (Exception e) {
            Log.d("DVpn", e.toString());
        } finally {
            Log.d("DVpn", "quit");
            stopThread();
        }
    }
}
