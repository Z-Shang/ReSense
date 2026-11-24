package dev.sense.lockscreen;

import android.app.PendingIntent;
import android.content.Intent;
import android.service.quicksettings.*;

/** User-triggered launch avoids background-activity and fullscreen-notification workarounds. */
public final class LockScreenTile extends TileService {
    @Override public void onStartListening(){Tile tile=getQsTile();if(tile!=null){tile.setState(Tile.STATE_INACTIVE);tile.setLabel("HTC lock screen");tile.setContentDescription("Open HTC ring lock screen");tile.updateTile();}}
    @Override public void onClick(){super.onClick();Intent open=new Intent(this,LockScreenActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivityAndCollapse(PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));}
}
