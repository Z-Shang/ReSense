package dev.sense.phone;

import android.content.*;

/** Immutable notification actions address one call, never whichever call happens to be active. */
public final class CallActionReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){SenseInCallService service=SenseInCallService.current();if(service!=null&&SenseInCallService.END.equals(intent.getAction()))service.end(intent.getStringExtra(SenseInCallService.TOKEN));}
}
