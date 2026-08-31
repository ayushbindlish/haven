package org.havenapp.main.tile;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.annotation.RequiresApi;

import org.havenapp.main.MonitorActivity;
import org.havenapp.main.R;
import org.havenapp.main.service.MonitorService;

/** Quick Settings tile: shows armed/disarmed, taps to toggle. */
@RequiresApi(Build.VERSION_CODES.N)
public class HavenTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        boolean armed = isArmed();
        if (armed) {
            stopService(new Intent(this, MonitorService.class));
            updateTile();
        } else {
            Intent i = new Intent(this, MonitorActivity.class)
                    .putExtra("auto_resume", true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 0, i,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            } else {
                startActivityAndCollapse(i);
            }
        }
    }

    private boolean isArmed() {
        return MonitorService.getInstance() != null && MonitorService.getInstance().isRunning();
    }

    private void updateTile() {
        Tile t = getQsTile();
        if (t == null) return;
        boolean armed = isArmed();
        t.setState(armed ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setLabel(getString(armed ? R.string.tile_armed : R.string.tile_disarmed));
        try {
            t.setIcon(Icon.createWithResource(this, R.drawable.ic_stat_haven));
        } catch (Exception ignored) {
        }
        t.updateTile();
    }
}
