package org.itxtech.daedalus;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.preference.PreferenceManager;
import android.util.Log;

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
public class BootBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        PreferenceManager.setDefaultValues(context, R.xml.perf_settings, false);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (prefs.getBoolean("settings_boot", false)) {

            Intent vIntent = VpnService.prepare(context);
            if (vIntent != null) {
                context.startActivity(vIntent);
            }

            DaedalusVpnService.primaryServer = DnsServers.getDnsServerAddress(prefs.getString("primary_server", "0"));
            DaedalusVpnService.secondaryServer = DnsServers.getDnsServerAddress(prefs.getString("secondary_server", "1"));

            context.startService((new Intent(context, DaedalusVpnService.class)).setAction(DaedalusVpnService.ACTION_ACTIVATE));

            Log.d("DVpn", "Boot service");
        }
    }
}
