package org.itxtech.daedalus.activity;

import android.app.FragmentManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import org.itxtech.daedalus.R;
import org.itxtech.daedalus.fragment.SettingsFragment;

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
public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        SettingsFragment settingsFragment = new SettingsFragment();
        FragmentManager manager = getFragmentManager();
        manager.beginTransaction().replace(R.id.activity_settings, settingsFragment).commit();
    }
}
