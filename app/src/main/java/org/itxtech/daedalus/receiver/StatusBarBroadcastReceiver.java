package org.itxtech.daedalus.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Button;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.R;
import org.itxtech.daedalus.activity.MainActivity;
import org.itxtech.daedalus.activity.SettingsActivity;
import org.itxtech.daedalus.service.DaedalusVpnService;

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
    public static String STATUS_BAR_BTN_DEACTIVATE_CLICK_ACTION = "org.itxtech.daedalus.receiver.StatusBarBroadcastReceiver.STATUS_BAR_BTN_DEACTIVATE_CLICK_ACTION";
    public static String STATUS_BAR_BTN_SETTINGS_CLICK_ACTION = "org.itxtech.daedalus.receiver.StatusBarBroadcastReceiver.STATUS_BAR_BTN_SETTINGS_CLICK_ACTION";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(STATUS_BAR_BTN_DEACTIVATE_CLICK_ACTION)) {
            Intent serviceIntent = new Intent(context, DaedalusVpnService.class);
            context.startService(serviceIntent.setAction(DaedalusVpnService.ACTION_DEACTIVATE));
            context.stopService(serviceIntent);
            Daedalus.updateShortcut(context);
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
                Log.d("DStatusBarRecv", e.toString());
            }
        }
    }
}
