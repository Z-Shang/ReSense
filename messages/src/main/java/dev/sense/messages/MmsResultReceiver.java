package dev.sense.messages;

import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.telephony.SmsManager;
import java.io.*;
import dev.sense.messages.mms.pdu.*;

/** Private, token-bound carrier callbacks; duplicate callbacks cannot send or import again. */
public final class MmsResultReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){String token=intent.getStringExtra("token");if(token==null)return;int result=getResultCode();byte[] response=intent.getByteArrayExtra(SmsManager.EXTRA_MMS_DATA);PendingResult pending=goAsync();Context app=context.getApplicationContext();MessageStore.EVENTS.execute(()->{try{complete(app,token,result,response);}finally{pending.finish();}});}
    static void complete(Context c,String token,int result,byte[] response){complete(c,token,result,response,MmsTransport.CARRIER);}
    static void complete(Context c,String token,int result,byte[] response,MmsTransport.Transport transport){
        try(MmsTransfers db=new MmsTransfers(c)){
            var job=db.get(token);if(job==null||(job.state()!=0&&job.state()!=3))return;
            if(response!=null&&response.length>1024*1024)result=Activity.RESULT_CANCELED;db.result(token,result,response);if(!MessageStore.isDefault(c))return;
            if(job.row().isEmpty()){db.state(token,result==Activity.RESULT_OK?1:2);try{MmsTransferProvider.file(c,token).delete();}catch(IOException ignored){}return;}
            if(!MmsStore.matches(c,job)){db.state(token,3);return;}
            boolean success=result==Activity.RESULT_OK;String error="MMS failed (carrier result "+result+"). Check mobile data, SIM and carrier MMS settings.";
            try{
                if(job.download()&&success){File file=MmsTransferProvider.file(c,token);if(file.length()==0||file.length()>MmsFiles.MAX_BYTES)throw new IOException("Invalid MMS download size");GenericPdu parsed=MmsCodec.parse(java.nio.file.Files.readAllBytes(file.toPath()));if(!(parsed instanceof RetrieveConf message)||message.getRetrieveStatus()>128)throw new IOException("Invalid downloaded MMS");Uri saved=MmsStore.received(c,token);if(saved==null)saved=MmsStore.persist(c,message,job.subscription(),false,token);c.getContentResolver().delete(Uri.parse(job.row()),null,null);MessageNotifications.show(c,"thread:"+android.provider.Telephony.Threads.getOrCreateThreadId(c,job.number()),"New multimedia message",job.number(),new Intent(c,MessagesActivity.class).putExtra("thread",android.provider.Telephony.Threads.getOrCreateThreadId(c,job.number())).putExtra("address",job.number()));if(message.getTransactionId()!=null)try{MmsTransport.protocol(c,new AcknowledgeInd(PduHeaders.CURRENT_MMS_VERSION,message.getTransactionId()),job.subscription(),job.location(),transport);}catch(Exception ignored){} }
                else if(!job.download()){
                    if(success&&response!=null){if(response.length>1024*1024)throw new IOException("Invalid MMS response");GenericPdu parsed=MmsCodec.parse(response);if(!(parsed instanceof SendConf confirmation)||confirmation.getResponseStatus()!=128||!token.equals(MmsStore.string(confirmation.getTransactionId())))throw new IOException("Carrier rejected the MMS or returned a mismatched response");}
                    MmsStore.state(c,job,success);
                }
            }catch(Exception failure){success=false;error=failure.getMessage()==null?"MMS could not be processed":failure.getMessage();if(!job.download())MmsStore.state(c,job,false);}
            db.state(token,success?1:2);try{MmsTransferProvider.file(c,token).delete();c.revokeUriPermission(MmsTransferProvider.uri(c,token),Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);}catch(IOException ignored){}
            if(!success)MessageNotifications.show(c,"mms-failed:"+token,job.download()?"MMS not downloaded":"MMS not sent",error,new Intent(c,MessagesActivity.class).putExtra("address",job.number()).putExtra("thread",android.provider.Telephony.Threads.getOrCreateThreadId(c,job.number())));
        }catch(RuntimeException ignored){/* Durable transfer remains for review if role/provider access was lost. */}
    }
}
