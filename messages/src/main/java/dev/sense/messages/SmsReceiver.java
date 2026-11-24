package dev.sense.messages;

import android.content.*;
import android.provider.Telephony;
import android.telephony.*;

/** Only the platform, holding BROADCAST_SMS, can deliver this receiver. */
public final class SmsReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(!Telephony.Sms.Intents.SMS_DELIVER_ACTION.equals(intent.getAction())||!MessageStore.isDefault(context))return;
        SmsMessage[] parts=Telephony.Sms.Intents.getMessagesFromIntent(intent);if(parts==null||parts.length==0)return;
        PendingResult pending=goAsync();Context app=context.getApplicationContext();
        MessageStore.EVENTS.execute(()->{try{
            String address=parts[0].getOriginatingAddress();if(address==null||address.isEmpty())address="Unknown";
            StringBuilder body=new StringBuilder();for(SmsMessage part:parts)if(part.getMessageBody()!=null)body.append(part.getMessageBody());
            int sub=intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,intent.getIntExtra("subscription",SubscriptionManager.INVALID_SUBSCRIPTION_ID));
            if(MessageStore.incoming(app,address,body.toString(),parts[0].getTimestampMillis(),sub)!=null)MessageNotifications.received(app,address,body.toString());
        }catch(RuntimeException error){MessageNotifications.show(app,"receive-error","Message storage unavailable","Open Messages to check SMS access.",new Intent(app,MessagesActivity.class));}finally{pending.finish();}});
    }
}
