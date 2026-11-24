package dev.sense.messages;

import android.app.Activity;
import android.content.*;
import android.telephony.SmsMessage;

/** Explicit PendingIntents are the only entry point; no exported status broadcasts. */
public final class SendResultReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        String token=intent.getStringExtra("token");if(token==null)return;
        boolean delivery="DELIVERED".equals(intent.getAction());if(!delivery&&!"SENT".equals(intent.getAction()))return;
        boolean success=getResultCode()==Activity.RESULT_OK;
        if(delivery){byte[] pdu=intent.getByteArrayExtra("pdu");if(pdu==null)return;SmsMessage receipt=SmsMessage.createFromPdu(pdu,intent.getStringExtra("format"));if(receipt==null)return;int status=receipt.getStatus();if(status>=32&&status<64)return;success=status<32;}
        boolean ok=success;PendingResult pending=goAsync();Context app=context.getApplicationContext();
        MessageStore.EVENTS.execute(()->{try(SendLedger ledger=new SendLedger(app)){
            SendLedger.Result result=ledger.record(token,intent.getIntExtra("part",-1),delivery,ok);
            if(result!=null&&MessageStore.isDefault(app)&&SendLedger.matches(app,result)){MessageStore.update(app,result.id(),result.type(),result.status());if(result.type()==5)MessageNotifications.show(app,"failed:"+result.id(),"SMS not sent","Open Messages to review the failed message.",new Intent(app,MessagesActivity.class));}
        }catch(RuntimeException ignored){/* Ledger results are replayed when provider access returns. */}finally{pending.finish();}});
    }
}
