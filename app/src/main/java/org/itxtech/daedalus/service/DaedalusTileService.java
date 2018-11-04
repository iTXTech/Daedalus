package org.itxtech.daedalus.service;

import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import androidx.annotation.RequiresApi;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.R;

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
        Tile tile = getQsTile();
        tile.setLabel(getString(R.string.quick_toggle));
        tile.setContentDescription(getString(R.string.app_name));
        tile.setState(Daedalus.switchService() ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }

    @Override
    public void onStartListening() {
        updateTile();
    }

    private void updateTile() {
        boolean activate = DaedalusVpnService.isActivated();
        Tile tile = getQsTile();
        tile.setLabel(getString(R.string.quick_toggle));
        tile.setContentDescription(getString(R.string.app_name));
        tile.setState(activate ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }
}
