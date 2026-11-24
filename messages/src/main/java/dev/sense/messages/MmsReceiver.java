package dev.sense.messages;

import android.content.*;
import android.provider.Telephony;
import android.telephony.SubscriptionManager;

/** Protected carrier notification delivery. Media downloads require an explicit tap. */
public final class MmsReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(!Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION.equals(intent.getAction())||!MessageStore.isDefault(context))return;
        byte[] data=intent.getByteArrayExtra("data");if(data==null)return;int subscription=intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,intent.getIntExtra("subscription",SubscriptionManager.INVALID_SUBSCRIPTION_ID));PendingResult pending=goAsync();Context app=context.getApplicationContext();
        MessageStore.EVENTS.execute(()->{try{MmsTransport.notification(app,data,subscription);}catch(Exception e){MessageNotifications.show(app,"mms-error","MMS notification unavailable","Could not process this carrier notification. Check SIM and MMS settings.",new Intent(app,MessagesActivity.class));}finally{pending.finish();}});
    }
}
