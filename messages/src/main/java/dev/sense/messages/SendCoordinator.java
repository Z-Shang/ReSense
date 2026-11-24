package dev.sense.messages;

import android.content.*;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Sending belongs to the process, not an Activity; composer recreation observes durable state. */
final class SendCoordinator {
    static final Set<String> ACTIVE=ConcurrentHashMap.newKeySet();
    static String key(String address){String clean=android.telephony.PhoneNumberUtils.stripSeparators(address);return clean==null?address:clean;}
    static void send(Context context,String number,String body,int subscription){
        send(context,number,body,subscription,SmsSender.RADIO);
    }
    static void send(Context context,String number,String body,int subscription,SmsSender.Transport transport){
        Context app=context.getApplicationContext();String key=key(number);SharedPreferences prefs=app.getSharedPreferences("drafts",Context.MODE_PRIVATE);
        if(!ACTIVE.add(key))return;
        prefs.edit().putString("sending:"+key,body).remove("error:"+key).apply();
        MessageStore.EVENTS.execute(()->{try{
            SmsSender.send(app,number,body,subscription,transport);
            prefs.edit().putString("submitted:"+key,body).remove("sending:"+key).remove("body:"+key).apply();
        }catch(RuntimeException e){prefs.edit().remove("sending:"+key).putString("error:"+key,"SMS could not be sent. Review the failed message before trying again.").apply();}
        finally{ACTIVE.remove(key);}});
    }
    static void sendMms(Context context,String number,String body,java.util.List<MmsFiles.Attachment> attachments,int subscription){
        Context app=context.getApplicationContext();String key=key(number);SharedPreferences prefs=app.getSharedPreferences("drafts",0);if(!ACTIVE.add(key))return;
        String snapshot=MmsFiles.encode(attachments);prefs.edit().putString("sending:"+key,body).remove("error:"+key).apply();
        MessageStore.EVENTS.execute(()->{try{
            MmsTransport.send(app,number,body,attachments,subscription,MmsTransport.CARRIER);
            prefs.edit().putString("mms-submitted:"+key,snapshot).putString("submitted:"+key,body).remove("sending:"+key).remove("body:"+key).remove("attachments:"+key).commit();
        }catch(Exception failure){prefs.edit().remove("sending:"+key).putString("error:"+key,failure.getMessage()==null?"MMS could not be sent":failure.getMessage()).apply();}finally{ACTIVE.remove(key);}});
    }

}
