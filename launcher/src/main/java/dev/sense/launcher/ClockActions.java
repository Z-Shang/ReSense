package dev.sense.launcher;

import android.content.*;
import android.provider.AlarmClock;
import android.widget.Toast;

/** Open the user's clock handler without creating or changing an alarm. */
final class ClockActions {
    static void open(Context context){
        Intent intent=new Intent(AlarmClock.ACTION_SHOW_ALARMS);
        if(!(context instanceof android.app.Activity))intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try{context.startActivity(intent);}catch(ActivityNotFoundException|SecurityException unavailable){Toast.makeText(context,"No clock app available",Toast.LENGTH_SHORT).show();}
    }
    private ClockActions(){}
}
