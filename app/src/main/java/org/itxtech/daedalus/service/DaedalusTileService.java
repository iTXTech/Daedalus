package org.itxtech.daedalus.service;

import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.support.annotation.RequiresApi;

import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.activity.MainActivity;

/**
 * Quick Tile Service
 * Created by pcqpcq on 17/7/28.
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

        startActivityAndCollapse(intent);
    }

    @Override
    public void onTileAdded() {
        updateTile();
    }

    @Override
    public void onStartListening() {
        updateTile();
    }

    private void updateTile() {
        boolean activate = Daedalus.getInstance().isServiceActivated();

        Tile tile = getQsTile();
        tile.setState(activate ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }
}
