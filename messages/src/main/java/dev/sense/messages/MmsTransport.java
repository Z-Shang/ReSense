package dev.sense.messages;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.*;
import dev.sense.messages.mms.pdu.*;
import java.io.*;
import java.util.*;

/** Carrier work uses the selected subscription and Android's MMS service, never direct HTTP. */
final class MmsTransport {
    interface Transport {void send(SmsManager manager,Context c,Uri pdu,String location,PendingIntent callback);void download(SmsManager manager,Context c,String location,Uri output,PendingIntent callback);}
    static final Transport CARRIER=new Transport(){public void send(SmsManager m,Context c,Uri p,String location,PendingIntent callback){m.sendMultimediaMessage(c,p,location,null,callback);}public void download(SmsManager m,Context c,String location,Uri p,PendingIntent callback){m.downloadMultimediaMessage(c,location,p,null,callback);}};
    static SmsManager manager(Context c,int subscription){if(!SubscriptionManager.isValidSubscriptionId(subscription))throw new IllegalArgumentException("Select an active SIM for MMS.");return c.getSystemService(SmsManager.class).createForSubscriptionId(subscription);}
    static byte[] compose(Context c,String number,String text,List<MmsFiles.Attachment> attachments,int subscription,String token)throws Exception{
        Bundle config=manager(c,subscription).getCarrierConfigValues();if(config.containsKey(SmsManager.MMS_CONFIG_MMS_ENABLED)&&!config.getBoolean(SmsManager.MMS_CONFIG_MMS_ENABLED))throw new IOException("MMS is disabled by this carrier.");
        int limit=Math.min(MmsFiles.MAX_BYTES,Math.max(16384,config.getInt(SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE,300*1024)));int width=Math.min(2048,Math.max(1,config.getInt(SmsManager.MMS_CONFIG_MAX_IMAGE_WIDTH,1280))),height=Math.min(2048,Math.max(1,config.getInt(SmsManager.MMS_CONFIG_MAX_IMAGE_HEIGHT,1280)));
        return compose(c,number,text,attachments,token,limit,width,height);
    }
    static byte[] compose(Context c,String number,String text,List<MmsFiles.Attachment> attachments,String token,int limit,int width,int height)throws Exception{
        if(attachments.isEmpty()||attachments.size()>10)throw new IOException("Add between one and ten attachments.");
        SendReq request=new SendReq();request.setTransactionId(MmsStore.ascii(token));request.setDate(System.currentTimeMillis()/1000);request.setTo(new EncodedStringValue[]{new EncodedStringValue(SmsSender.address(number))});request.setFrom(new EncodedStringValue("insert-address-token"));request.setContentType(MmsStore.ascii("application/vnd.wap.multipart.related"));request.setExpiry(7*24*60*60);request.setDeliveryReport(PduHeaders.VALUE_NO);request.setReadReport(PduHeaders.VALUE_NO);
        PduBody body=new PduBody();int budget=limit-text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length-8192;if(budget<1024)throw new IOException("Message text exceeds the carrier MMS limit.");StringBuilder smil=new StringBuilder("<smil><head><layout><root-layout width=\"320\" height=\"480\"/><region id=\"Image\" left=\"0\" top=\"0\" width=\"320\" height=\"400\"/><region id=\"Text\" left=\"0\" top=\"400\" width=\"320\" height=\"80\"/></layout></head><body>");
        if(!text.isEmpty())body.addPart(part("text/plain","text.txt",MmsStore.ascii(text)));
        for(int i=0;i<attachments.size();i++){var attachment=attachments.get(i);int share=budget/(attachments.size()-i);byte[] data=MmsFiles.bytes(c,attachment,share,width,height);budget-=data.length;String mime=attachment.mime();if(mime.startsWith("image/")&&!mime.equals("image/gif"))mime="image/jpeg";String extension=android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);if(extension==null)extension=mime.contains("vcard")?"vcf":"bin";String filename="attachment"+i+"."+extension;body.addPart(part(mime,filename,data));String tag=mime.startsWith("image/")?"img":mime.startsWith("video/")?"video":mime.startsWith("audio/")?"audio":"ref";smil.append("<par dur=\"5000ms\"><").append(tag).append(" src=\"").append(filename).append("\" region=\"Image\"/>");if(!text.isEmpty())smil.append("<text src=\"text.txt\" region=\"Text\"/>");smil.append("</par>");}
        smil.append("</body></smil>");body.addPart(0,part("application/smil","smil.xml",MmsStore.ascii(smil.toString())));request.setBody(body);byte[] pdu=new PduComposer(c,request).make();if(pdu==null||pdu.length>limit)throw new IOException("MMS exceeds this carrier's message size limit.");return pdu;
    }
    static PduPart part(String mime,String name,byte[] data){PduPart p=new PduPart();p.setContentType(MmsStore.ascii(mime));p.setContentLocation(MmsStore.ascii(name));p.setContentId(MmsStore.ascii(name));p.setName(MmsStore.ascii(name));if(mime.startsWith("text/")||mime.equals("application/smil"))p.setCharset(CharacterSets.UTF_8);p.setData(data);return p;}
    static String send(Context c,String number,String text,List<MmsFiles.Attachment> attachments,int subscription,Transport transport)throws Exception{
        if(!MessageStore.isDefault(c)||!MessageStore.permission(c,android.Manifest.permission.SEND_SMS))throw new IOException("Make Sense Messages the default SMS app and allow SMS access first.");
        String token=UUID.randomUUID().toString();byte[] encoded=compose(c,number,text,attachments,subscription,token);return submit(c,number,encoded,subscription,token,transport);
    }
    static String submit(Context c,String number,byte[] encoded,int subscription,String token,Transport transport)throws Exception{
        if(!MessageStore.isDefault(c)||!MessageStore.permission(c,android.Manifest.permission.SEND_SMS))throw new IOException("SMS role and access required");GenericPdu parsed=MmsCodec.parse(encoded);if(!(parsed instanceof SendReq request))throw new IOException("Could not encode MMS");Uri row=MmsStore.persist(c,request,subscription,true,token);
        try(MmsTransfers db=new MmsTransfers(c)){
            var job=new MmsTransfers.Job(token,false,row.toString(),number,"",subscription,0,System.currentTimeMillis());db.add(job);
            try{java.nio.file.Files.write(MmsTransferProvider.file(c,token).toPath(),encoded);transport.send(manager(c,subscription),c,MmsTransferProvider.uri(c,token),null,callback(c,token));}
            catch(Exception failure){db.state(token,2);MmsStore.state(c,job,false);throw failure;}
        }return token;
    }
    static PendingIntent callback(Context c,String token){Intent i=new Intent(c,MmsResultReceiver.class).setData(Uri.parse("sense-mms://result/"+token)).putExtra("token",token);return PendingIntent.getBroadcast(c,0,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_MUTABLE);}
    static void notification(Context c,byte[] data,int subscription)throws Exception{notification(c,data,subscription,CARRIER);}
    static void notification(Context c,byte[] data,int subscription,Transport transport)throws Exception{
        if(data.length>1024*1024)throw new IOException("Oversized MMS notification");GenericPdu parsed=MmsCodec.parse(data);if(!(parsed instanceof NotificationInd notification))return;
        String location=MmsStore.string(notification.getContentLocation());if(manager(c,subscription).getCarrierConfigValues().getBoolean(SmsManager.MMS_CONFIG_APPEND_TRANSACTION_ID,false)&&location.endsWith("="))location+=Uri.encode(MmsStore.string(notification.getTransactionId()));Uri url=Uri.parse(location);if(!("http".equals(url.getScheme())||"https".equals(url.getScheme()))||url.getHost()==null)throw new IOException("Invalid carrier MMS location");
        String number=MmsStore.address(notification.getFrom());if(number.isBlank())number="Unknown sender";String token=UUID.randomUUID().toString();
        try(MmsTransfers db=new MmsTransfers(c)){
            if(db.notification(location,subscription)!=null)return;
            ContentValues values=new ContentValues();values.put("thread_id",Telephony.Threads.getOrCreateThreadId(c,number));values.put("date",System.currentTimeMillis()/1000);values.put("msg_box",1);values.put("read",0);values.put("seen",0);values.put("m_type",130);values.put("ct_l",location);values.put("tr_id",token);values.put("sub_id",subscription);
            Uri row=c.getContentResolver().insert(Telephony.Mms.CONTENT_URI,values);if(row==null)throw new IOException("Cannot save MMS notification");MmsStore.addAddress(c,ContentUris.parseId(row),number,137);
            var job=new MmsTransfers.Job(token,true,row.toString(),number,location,subscription,4,System.currentTimeMillis());try{db.add(job);}catch(RuntimeException duplicate){c.getContentResolver().delete(row,null,null);throw duplicate;}
            MessageNotifications.show(c,"thread:"+Telephony.Threads.getOrCreateThreadId(c,number),"New MMS",number+" · Tap the message to download",new Intent(c,MessagesActivity.class).putExtra("thread",Telephony.Threads.getOrCreateThreadId(c,number)).putExtra("address",number));
            try{protocol(c,new NotifyRespInd(PduHeaders.CURRENT_MMS_VERSION,notification.getTransactionId(),PduHeaders.STATUS_DEFERRED),subscription,location,transport);}catch(Exception ignored){/* Media remains available for manual download. */}
        }
    }
    static void protocol(Context c,GenericPdu pdu,int subscription,String location,Transport transport)throws Exception{
        String token=UUID.randomUUID().toString();byte[] encoded=new PduComposer(c,pdu).make();if(encoded==null)throw new IOException("Cannot encode MMS acknowledgement");
        try(MmsTransfers db=new MmsTransfers(c)){db.add(new MmsTransfers.Job(token,false,"","",location,subscription,0,System.currentTimeMillis()));java.nio.file.Files.write(MmsTransferProvider.file(c,token).toPath(),encoded);Bundle config=manager(c,subscription).getCarrierConfigValues();String target=config.getBoolean(SmsManager.MMS_CONFIG_NOTIFY_WAP_MMSC_ENABLED,false)?location:null;try{transport.send(manager(c,subscription),c,MmsTransferProvider.uri(c,token),target,callback(c,token));}catch(Exception failure){db.state(token,2);MmsTransferProvider.file(c,token).delete();throw failure;}}
    }
    static void download(Context c,long id,Transport transport)throws Exception{
        if(!MessageStore.isDefault(c))throw new IOException("Make Sense Messages the default SMS app first.");try(MmsTransfers db=new MmsTransfers(c)){var job=db.forRow("content://mms/"+id);if(job==null||!job.download())throw new IOException("Download information unavailable");if(job.state()==0)throw new IOException("MMS download already in progress");if(!MmsStore.matches(c,job))throw new IOException("MMS notification changed");if(job.state()!=4){String oldToken=job.token();job=db.renew(job);ContentValues identity=new ContentValues();identity.put("tr_id",job.token());c.getContentResolver().update(Uri.parse(job.row()),identity,null,null);MmsTransferProvider.file(c,oldToken).delete();c.revokeUriPermission(MmsTransferProvider.uri(c,oldToken),Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);}db.state(job.token(),0);try{transport.download(manager(c,job.subscription()),c,job.location(),MmsTransferProvider.uri(c,job.token()),callback(c,job.token()));}catch(Exception e){db.state(job.token(),2);throw e;}}
    }
}
