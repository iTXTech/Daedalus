package org.itxtech.daedalus.receiver;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.service.DaedalusVpnService;
import org.itxtech.daedalus.util.DnsServer;
import org.itxtech.daedalus.util.HostsResolver;

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
        if (Daedalus.getPrefs().getBoolean("settings_boot", false)) {

            if (Daedalus.getPrefs().getBoolean("settings_local_host_resolve", false)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    int permission = ActivityCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE);
                    if (permission != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                }
                HostsResolver.startLoad(context.getExternalFilesDir(null).getPath() + "/hosts");
            }

            Intent vIntent = VpnService.prepare(context);
            if (vIntent != null) {
                context.startActivity(vIntent);
            }

            DaedalusVpnService.primaryServer = DnsServer.getDnsServerAddressById(Daedalus.getPrefs().getString("primary_server", "0"));
            DaedalusVpnService.secondaryServer = DnsServer.getDnsServerAddressById(Daedalus.getPrefs().getString("secondary_server", "1"));

            context.startService((new Intent(context, DaedalusVpnService.class)).setAction(DaedalusVpnService.ACTION_ACTIVATE));

            Log.d("DBootRecv", "Triggered boot receiver");
        }

        Daedalus.updateShortcut(context);
    }
}
