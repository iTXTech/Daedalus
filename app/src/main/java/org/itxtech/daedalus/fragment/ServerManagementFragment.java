package org.itxtech.daedalus.fragment;

import android.content.Context;
import android.os.Bundle;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.server.DnsServer;

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

/**
 * Settings > Server Management: every built-in DNS server with an on/off switch.
 * Servers switched off are hidden from the primary/secondary server lists and
 * from the DNS test. The state is stored under {@link DnsServer#getEnabledKey()}.
 */
public class ServerManagementFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getPreferenceManager().getContext();
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);
        for (DnsServer server : Daedalus.DNS_SERVERS) {
            SwitchPreference preference = new SwitchPreference(context);
            preference.setKey(server.getEnabledKey());
            preference.setTitle(server.getStringDescription(context));
            preference.setSummary(server.getRealName());
            preference.setDefaultValue(server.isEnabledByDefault());
            preference.setIconSpaceReserved(false);
            screen.addPreference(preference);
        }
        setPreferenceScreen(screen);
    }
}
