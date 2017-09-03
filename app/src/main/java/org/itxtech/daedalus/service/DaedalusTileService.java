package org.itxtech.daedalus.service;

import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.support.annotation.RequiresApi;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.R;
import org.itxtech.daedalus.activity.MainActivity;

/**
 * Daedalus Project
 *
 * @author pcqpcq & iTX Technologies
 * @link https://itxtech.org
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
@RequiresApi(api = Build.VERSION_CODES.N)
public class DaedalusTileService extends TileService {

    @Override
    public void onClick() {
        Daedalus appContext = Daedalus.getInstance();
        boolean activate = appContext.isServiceActivated();

        Intent intent = new Intent(appContext, MainActivity.class)
                .setAction(Intent.ACTION_VIEW)
                .putExtra(MainActivity.LAUNCH_ACTION, activate ? MainActivity.LAUNCH_ACTION_DEACTIVATE : MainActivity.LAUNCH_ACTION_ACTIVATE);

        startActivity(intent);
    }

    @Override
    public void onStartListening() {
        updateTile();
    }

    private void updateTile() {
        boolean activate = Daedalus.getInstance().isServiceActivated();

        Tile tile = getQsTile();
        tile.setLabel(getString(R.string.quick_toggle));
        tile.setContentDescription(getString(R.string.app_name));
        tile.setState(activate ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }
}
