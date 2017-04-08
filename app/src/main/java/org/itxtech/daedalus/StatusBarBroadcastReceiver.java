package org.itxtech.daedalus;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Button;

import java.lang.reflect.Method;

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
public class StatusBarBroadcastReceiver extends BroadcastReceiver {
    static String STATUS_BAR_BTN_DEACTIVATE_CLICK_ACTION = "org.itxtech.daedalus.StatusBarBroadcastReceiver.STATUS_BAR_BTN_DEACTIVATE_CLICK_ACTION";
    static String STATUS_BAR_BTN_SETTINGS_CLICK_ACTION = "org.itxtech.daedalus.StatusBarBroadcastReceiver.STATUS_BAR_BTN_SETTINGS_CLICK_ACTION";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(STATUS_BAR_BTN_DEACTIVATE_CLICK_ACTION)) {
            Intent serviceIntent = new Intent(context, DaedalusVpnService.class);
            context.startService(serviceIntent.setAction(DaedalusVpnService.ACTION_DEACTIVATE));
            context.stopService(serviceIntent);
            if (MainActivity.getInstance() != null) {
                ((Button) MainActivity.getInstance().findViewById(R.id.button_activate)).setText(R.string.button_text_activate);
            }

        }
        if (intent.getAction().equals(STATUS_BAR_BTN_SETTINGS_CLICK_ACTION)) {
            Intent settingsIntent = new Intent(context, SettingsActivity.class);
            settingsIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            context.startActivity(settingsIntent);
            try {
                Object statusBarManager = context.getSystemService("statusbar");
                Method collapse = statusBarManager.getClass().getMethod("collapsePanels");
                collapse.invoke(statusBarManager);
            } catch (Exception e) {
                Log.d("DVpn", e.toString());
            }
        }
    }
}
