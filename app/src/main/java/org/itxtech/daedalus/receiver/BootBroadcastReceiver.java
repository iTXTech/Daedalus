package org.itxtech.daedalus.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.service.DaedalusVpnService;
import org.itxtech.daedalus.util.DnsServerHelper;
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
public class BootBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Daedalus.getPrefs().getBoolean("settings_boot", false)) {
            Intent vIntent = VpnService.prepare(context);
            if (vIntent != null) {
                vIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(vIntent);
            }

            DaedalusVpnService.primaryServer = DnsServerHelper.getAddressById(DnsServerHelper.getPrimary());
            DaedalusVpnService.secondaryServer = DnsServerHelper.getAddressById(DnsServerHelper.getSecondary());

            context.startService((new Intent(context, DaedalusVpnService.class)).setAction(DaedalusVpnService.ACTION_ACTIVATE));

            Logger.info("Triggered boot receiver");
        }

        Daedalus.updateShortcut(context);
    }
}
