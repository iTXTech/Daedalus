package org.itxtech.daedalus.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.util.Log;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.service.DaedalusVpnService;
import org.itxtech.daedalus.util.DnsServerHelper;

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
            Intent vIntent = VpnService.prepare(context);
            if (vIntent != null) {
                context.startActivity(vIntent);
            }

            DaedalusVpnService.primaryServer = DnsServerHelper.getAddressById(DnsServerHelper.getPrimary());
            DaedalusVpnService.secondaryServer = DnsServerHelper.getAddressById(DnsServerHelper.getSecondary());

            context.startService((new Intent(context, DaedalusVpnService.class)).setAction(DaedalusVpnService.ACTION_ACTIVATE));

            Log.d("DBootRecv", "Triggered boot receiver");
        }

        Daedalus.updateShortcut(context);
    }
}
