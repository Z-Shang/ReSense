package dev.sense.messages;

import android.app.Service;
import android.content.*;
import android.os.IBinder;
import android.telephony.*;

/** System call-screen quick reply; callers must hold SEND_RESPOND_VIA_MESSAGE. */
public final class RespondService extends Service {
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null||!TelephonyManager.ACTION_RESPOND_VIA_MESSAGE.equals(intent.getAction())||intent.getData()==null){stopSelf(startId);return START_NOT_STICKY;}
        String address=intent.getData().getSchemeSpecificPart();String body=intent.getStringExtra(Intent.EXTRA_TEXT);
        MessageStore.EVENTS.execute(()->{try{if(body!=null&&!body.isBlank())SmsSender.send(this,address,body,SubscriptionManager.getDefaultSmsSubscriptionId());}
        catch(RuntimeException e){getSharedPreferences("drafts",MODE_PRIVATE).edit().putString("quick-address",address).putString("quick-body",body).apply();MessageNotifications.show(this,"quick-reply","Quick reply not sent","Open Messages to review and send your reply.",new Intent(this,MessagesActivity.class).setAction("dev.sense.messages.QUICK_DRAFT"));}
        finally{stopSelfResult(startId);}});return START_NOT_STICKY;
    }
}
