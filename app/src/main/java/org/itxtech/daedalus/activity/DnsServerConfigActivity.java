package org.itxtech.daedalus.activity;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import org.itxtech.daedalus.R;
import org.itxtech.daedalus.fragment.DnsServerConfigFragment;

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
public class DnsServerConfigActivity extends AppCompatActivity {
    public static final String LAUNCH_ACTION_CUSTOM_DNS_SERVER_ID = "org.itxtech.daedalus.activity.DnsServerConfigActivity.LAUNCH_ACTION_CUSTOM_DNS_SERVER_ID";
    public static final int CUSTOM_DNS_SERVER_ID_NONE = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dns_server_config);

        DnsServerConfigFragment fragment = new DnsServerConfigFragment();

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar_config);
        Drawable drawable = ContextCompat.getDrawable(this, R.drawable.ic_clear);
        Drawable wrappedDrawable = DrawableCompat.wrap(drawable);
        DrawableCompat.setTint(wrappedDrawable, Color.WHITE);
        toolbar.setNavigationIcon(drawable);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });
        toolbar.setOnMenuItemClickListener(fragment);
        toolbar.inflateMenu(R.menu.custom_dns_server_menu);

        FragmentManager manager = getFragmentManager();
        fragment.setIntent(getIntent());
        FragmentTransaction fragmentTransaction = manager.beginTransaction();
        fragmentTransaction.replace(R.id.id_config, fragment);
        fragmentTransaction.commit();
    }
}
