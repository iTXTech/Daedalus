package org.itxtech.daedalus.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import org.itxtech.daedalus.R;

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
public class SettingsFragment extends ToolbarFragment {
    private int subPageTitle = R.string.action_settings;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getChildFragmentManager().addOnBackStackChangedListener(this::updateTitle);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        getChildFragmentManager().beginTransaction().replace(R.id.settings_content, new GlobalConfigFragment()).commit();
    }

    public void showServerManagement() {
        showSubPage(new ServerManagementFragment(), R.string.settings_server_management);
    }

    public void showNetworkRules() {
        showSubPage(new NetworkRulesFragment(), R.string.settings_network_rules);
    }

    /**
     * Opens a sub page of the settings. The back button returns to the settings list.
     */
    private void showSubPage(Fragment fragment, int titleRes) {
        subPageTitle = titleRes;
        getChildFragmentManager().beginTransaction()
                .replace(R.id.settings_content, fragment)
                .addToBackStack(null)
                .commit();
    }

    @Override
    public void checkStatus() {
        menu.findItem(R.id.nav_settings).setChecked(true);
        updateTitle();
    }

    private void updateTitle() {
        if (toolbar != null) {
            toolbar.setTitle(getChildFragmentManager().getBackStackEntryCount() > 0
                    ? subPageTitle : R.string.action_settings);
        }
    }
}
