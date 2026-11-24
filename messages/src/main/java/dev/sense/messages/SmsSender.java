package dev.sense.messages;

import android.Manifest;
import android.app.PendingIntent;
import android.content.*;
import android.net.Uri;
import android.provider.Telephony;
import android.telephony.*;
import java.util.*;

/** Dispatches only after an explicit user Send action (or protected system quick reply). */
final class SmsSender {
    interface Transport {void send(SmsManager manager,String address,ArrayList<String> parts,ArrayList<PendingIntent> sent,ArrayList<PendingIntent> delivered);}
    static final Transport RADIO=(manager,address,parts,sent,delivered)->manager.sendMultipartTextMessage(address,null,parts,sent,delivered);
    static String address(String raw){
        String number=PhoneNumberUtils.stripSeparators(raw.trim());
        if(number==null||!number.matches("\\+?[0-9]{1,25}"))throw new IllegalArgumentException("Enter one phone number, or pick a contact.");
        return number;
    }
    static List<SubscriptionInfo> subscriptions(Context c){
        if(!MessageStore.permission(c,Manifest.permission.READ_PHONE_STATE))return List.of();
        try{List<SubscriptionInfo> result=c.getSystemService(SubscriptionManager.class).getActiveSubscriptionInfoList();return result==null?List.of():result;}catch(SecurityException e){return List.of();}
    }
    static long send(Context c,String number,String body,int subscription){return send(c,number,body,subscription,RADIO);}
    static long send(Context c,String number,String body,int subscription,Transport transport){
        if(!MessageStore.isDefault(c))throw new IllegalStateException("Make Sense Messages your default SMS app first.");
        if(!MessageStore.permission(c,Manifest.permission.SEND_SMS)||!MessageStore.permission(c,Manifest.permission.READ_SMS))throw new IllegalStateException("Allow SMS access before sending.");
        number=address(number);if(body.isBlank())throw new IllegalArgumentException("Enter a message.");
        if(!SubscriptionManager.isValidSubscriptionId(subscription))throw new IllegalArgumentException("Select an active SIM for SMS.");
        SmsManager manager=c.getSystemService(SmsManager.class).createForSubscriptionId(subscription);ArrayList<String> parts=manager.divideMessage(body);
        String token=UUID.randomUUID().toString();long thread=Telephony.Threads.getOrCreateThreadId(c,number);
        ContentValues values=new ContentValues();values.put("address",number);values.put("body",body);values.put("date",System.currentTimeMillis());values.put("read",1);values.put("seen",1);values.put("type",4);values.put("status",32);values.put("sub_id",subscription);values.put("thread_id",thread);
        Uri uri=c.getContentResolver().insert(Telephony.Sms.Outbox.CONTENT_URI,values);if(uri==null)throw new IllegalStateException("Could not save the outgoing SMS.");long id=ContentUris.parseId(uri);
        try(SendLedger ledger=new SendLedger(c)){
            ledger.register(token,id,parts.size());ArrayList<PendingIntent> sent=new ArrayList<>(),delivered=new ArrayList<>();
            for(int i=0;i<parts.size();i++){sent.add(callback(c,token,i,false));delivered.add(callback(c,token,i,true));}
            transport.send(manager,number,parts,sent,delivered);
        }catch(RuntimeException e){try(SendLedger failed=new SendLedger(c)){failed.getWritableDatabase().execSQL("UPDATE parts SET sent = -1 WHERE token = ?",new Object[]{token});}MessageStore.update(c,id,5,64);throw e;}
        return thread;
    }
    private static PendingIntent callback(Context c,String token,int part,boolean delivery){
        Intent intent=new Intent(c,SendResultReceiver.class).setAction(delivery?"DELIVERED":"SENT").setData(Uri.parse("sense-sms://"+token+"/"+part+"/"+(delivery?"delivery":"sent"))).putExtra("token",token).putExtra("part",part);
        // Mutable only for the telephony stack's delivery PDU; the target is explicit and private.
        return PendingIntent.getBroadcast(c,0,intent,PendingIntent.FLAG_UPDATE_CURRENT|(delivery?PendingIntent.FLAG_MUTABLE:PendingIntent.FLAG_IMMUTABLE));
    }
}
