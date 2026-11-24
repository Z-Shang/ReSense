package dev.sense.messages;

import android.app.*;
import android.app.role.RoleManager;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.Telephony;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Uses provider fixtures and an injected transport. Never sends an SMS to a radio. */
public final class MessagesInstrumentation extends Instrumentation {
    private Bundle arguments;private MessagesActivity activity;private final List<Long> ids=new ArrayList<>();
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);this.arguments=arguments;start();}
    private void check(boolean condition,String why){if(!condition)throw new AssertionError(why);}
    private void main(Runnable action){runOnMainSync(action);waitForIdleSync();}
    private void await(BooleanSupplier condition,String what)throws Exception{long end=SystemClock.uptimeMillis()+12000;while(SystemClock.uptimeMillis()<end){if(condition.getAsBoolean())return;Thread.sleep(80);}throw new AssertionError("Timed out: "+what);}
    private View find(View view,String text){if(!view.isShown())return null;if(text.equals(view.getContentDescription())||view instanceof TextView t&&text.contentEquals(t.getText()))return view;if(view instanceof android.view.ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),text);if(found!=null)return found;}return null;}
    private View find(String value){return find(activity.getWindow().getDecorView(),value);}
    private void click(String text){main(()->{View v=find(text);check(v!=null,"Missing "+text);if(!v.performClick()){View node=v;while(node.getParent() instanceof View parent&&!(parent instanceof ListView))node=parent;if(node.getParent() instanceof ListView list)list.performItemClick(node,list.getPositionForView(node),list.getItemIdAtPosition(list.getPositionForView(node)));}});}
    private int type(long id){try(Cursor c=getTargetContext().getContentResolver().query(ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI,id),new String[]{"type"},null,null,null)){return c!=null&&c.moveToFirst()?c.getInt(0):-1;}}
    private void screenshot(String name)throws Exception{getTargetContext().getSystemService(NotificationManager.class).cancelAll();Thread.sleep(500);try(var output=getTargetContext().openFileOutput(name,0)){var bitmap=getUiAutomation().takeScreenshot();bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);bitmap.recycle();}}
    @Override public void onStart(){Bundle report=new Bundle();Context c=getTargetContext();String number="5550182026",prefix="SENSE_SMS_FIXTURE_"+System.currentTimeMillis();
        if("mms".equals(arguments.getString("suite"))){new MmsInstrumentation(this).run();return;}
        if("attachment-layout".equals(arguments.getString("suite"))){
            try{
                activity=(MessagesActivity)startActivitySync(new Intent(c,MessagesActivity.class).setAction(Intent.ACTION_SENDTO).setData(Uri.parse("smsto:")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                waitForIdleSync();Thread.sleep(400);
                main(()->{View attach=find("Add attachment"),send=find("Send SMS"),editor=find("Message text");check(attach!=null&&send!=null&&editor!=null,"Missing compose control");int[] a=new int[2],b=new int[2],e=new int[2];attach.getLocationOnScreen(a);send.getLocationOnScreen(b);editor.getLocationOnScreen(e);check(a[1]>=b[1]+send.getHeight(),"Attachment overlaps Send");check(e[0]+editor.getWidth()<=a[0],"Editor overlaps attachment column");check(attach.getWidth()>=48*c.getResources().getDisplayMetrics().density,"Attachment touch target too small");});
                report.putString("result","PASS: original attachment column, editor clearance and accessible target; no SMS sent");finish(Activity.RESULT_OK,report);
            }catch(Throwable failure){report.putString("error",android.util.Log.getStackTraceString(failure));finish(Activity.RESULT_CANCELED,report);}finally{if(activity!=null)main(activity::finish);}return;
        }
        if("true".equals(arguments.getString("cleanup-only"))){
            try{check(MessageStore.isDefault(c),"Cleanup requires SMS role");c.getContentResolver().delete(Telephony.Sms.CONTENT_URI,"(address = ? AND body LIKE ?) OR (address = ? AND body LIKE ?)",new String[]{number,"SENSE_SMS_FIXTURE_%","5550182027","SENSE_SMS_EMULATOR_%"});c.getSystemService(NotificationManager.class).cancelAll();report.putString("result","PASS: emulator SMS fixtures removed");finish(Activity.RESULT_OK,report);}catch(Throwable e){report.putString("error",e.toString());finish(Activity.RESULT_CANCELED,report);}return;
        }
        try{
            check(MessageStore.isDefault(c),"Test requires temporary SMS role");
            for(String permission:new String[]{android.Manifest.permission.READ_SMS,android.Manifest.permission.SEND_SMS,android.Manifest.permission.RECEIVE_SMS,android.Manifest.permission.POST_NOTIFICATIONS})getUiAutomation().grantRuntimePermission(c.getPackageName(),permission);
            String incoming=arguments.getString("incoming");
            if(incoming!=null){await(()->{try(Cursor cursor=c.getContentResolver().query(Telephony.Sms.CONTENT_URI,new String[]{"_id"},"body = ?",new String[]{incoming},null)){return cursor!=null&&cursor.moveToFirst();}},"platform SMS_DELIVER");try(Cursor cursor=c.getContentResolver().query(Telephony.Sms.CONTENT_URI,new String[]{"_id"},"body = ?",new String[]{incoming},null)){while(cursor!=null&&cursor.moveToNext())ids.add(cursor.getLong(0));}report.putString("incoming","PASS: emulator SMS delivered through protected receiver into system provider");}
            Uri received=MessageStore.incoming(c,number,prefix+" Hello from HTC Sense",System.currentTimeMillis(),1);check(received!=null,"Inbox insert");ids.add(ContentUris.parseId(received));
            long thread=Telephony.Threads.getOrCreateThreadId(c,number);
            var rows=MessageStore.threads(c,prefix);check(rows.size()==1&&rows.get(0).unread()>0,"Thread grouping and unread count");
            activity=(MessagesActivity)startActivitySync(new Intent(c,MessagesActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            await(()->{final boolean[] found={false};main(()->found[0]=find(number+" ("+rows.get(0).count()+")")!=null);return found[0];},"conversation list row");
            screenshot("messages-inbox.png");click(number+" ("+rows.get(0).count()+")");
            await(()->{final boolean[] found={false};main(()->found[0]=find(prefix+" Hello from HTC Sense")!=null);return found[0];},"received bubble");
            main(()->((EditText)find("Message text")).setText("Draft survives recreation"));
            main(()->{Bundle state=new Bundle();callActivityOnSaveInstanceState(activity,state);check("Draft survives recreation".equals(state.getString("body")),"Draft saved state");});
            click("Back to conversations");
            await(()->{final boolean[] back={false};main(()->back[0]=find("Message text")==null&&find(number+" ("+rows.get(0).count()+")")!=null);return back[0];},"Header Back fade settles to inbox");
            click(number+" ("+rows.get(0).count()+")");
            await(()->{final boolean[] restored={false};main(()->restored[0]=find("Draft survives recreation")!=null);return restored[0];},"Draft restored after opening conversation");
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            await(()->{final boolean[] back={false};main(()->back[0]=find("Message text")==null&&find(number+" ("+rows.get(0).count()+")")!=null);return back[0];},"System Back fade settles to inbox");
            click(number+" ("+rows.get(0).count()+")");
            await(()->{final boolean[] restored={false};main(()->restored[0]=find("Draft survives recreation")!=null);return restored[0];},"Draft retained across both animated Back routes");
            MessageStore.markRead(c,thread);check(MessageStore.threads(c,prefix).get(0).unread()==0,"Read state saved");
            ArrayList<PendingIntent> callbacks=new ArrayList<>();String multipart=prefix+" "+"Long SMS part. ".repeat(35);
            long sentThread=SmsSender.send(c,number,multipart,1,(manager,destination,parts,sent,delivered)->{check(number.equals(destination)&&parts.size()>1,"Multipart split");callbacks.addAll(sent);});
            check(sentThread==thread,"Outgoing thread reuse");long sentId;
            try(Cursor cursor=c.getContentResolver().query(Telephony.Sms.CONTENT_URI,new String[]{"_id"},"body = ?",new String[]{multipart},null)){check(cursor!=null&&cursor.moveToFirst(),"Outbox row");sentId=cursor.getLong(0);}ids.add(sentId);check(type(sentId)==4,"Outbox persisted before dispatch");
            for(PendingIntent callback:callbacks)callback.send(Activity.RESULT_OK);await(()->type(sentId)==2,"Multipart sent callbacks");
            String failedBody=prefix+" Failure "+"text ".repeat(90);callbacks.clear();SmsSender.send(c,number,failedBody,1,(manager,destination,parts,sent,delivered)->callbacks.addAll(sent));long failedId;
            try(Cursor cursor=c.getContentResolver().query(Telephony.Sms.CONTENT_URI,new String[]{"_id"},"body = ?",new String[]{failedBody},null)){check(cursor!=null&&cursor.moveToFirst(),"Failed fixture saved");failedId=cursor.getLong(0);}ids.add(failedId);
            callbacks.get(0).send(android.telephony.SmsManager.RESULT_ERROR_GENERIC_FAILURE);for(PendingIntent callback:callbacks)callback.send(Activity.RESULT_OK);await(()->type(failedId)==5,"Failure survives late successful callbacks");
            try(SendLedger ledger=new SendLedger(c)){String token=UUID.randomUUID().toString();ledger.register(token,sentId,2);check(ledger.record(token,0,false,true).type()==4,"Wait for all sent parts");ledger.record(token,1,false,true);check(ledger.record(token,0,true,true).status()==32,"Wait for all delivery reports");check(ledger.record(token,1,true,true).status()==0,"All delivered");}
            boolean invalid=false;try{SmsSender.address("123;456");}catch(IllegalArgumentException expected){invalid=true;}check(invalid,"Reject multiple recipients instead of accidental group SMS");
            // Real SMS sending is replaced by injected transport above; never click the UI Send action.
            activity.finish();activity=(MessagesActivity)startActivitySync(new Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:"+number)).setPackage(c.getPackageName()).putExtra("sms_body","Prefilled SMS draft").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await(()->{final boolean[] found={false};main(()->found[0]=find("Prefilled SMS draft")!=null);return found[0];},"SENDTO prefill");screenshot("messages-conversation.png");
            java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
            String rotating=prefix+" Rotation submission";
            main(()->((EditText)find("Message text")).setText(rotating));
            SendCoordinator.send(c,number,rotating,1,(manager,destination,parts,sent,delivered)->{entered.countDown();try{check(release.await(8,java.util.concurrent.TimeUnit.SECONDS),"Release test transport");}catch(InterruptedException e){throw new RuntimeException(e);}for(PendingIntent callback:sent)try{callback.send(Activity.RESULT_OK);}catch(PendingIntent.CanceledException e){throw new RuntimeException(e);}});
            check(entered.await(5,java.util.concurrent.TimeUnit.SECONDS),"Coordinator entered fake transport");
            ActivityMonitor recreated=addMonitor(MessagesActivity.class.getName(),null,false);
            try{
                main(()->activity.recreate());Activity next=waitForMonitorWithTimeout(recreated,5000);check(next!=null,"Recreated composer");activity=(MessagesActivity)next;
                main(()->check(!find("Message text").isEnabled(),"Recreated composer must lock an in-flight submission"));
            }finally{release.countDown();removeMonitor(recreated);}
            await(()->{final boolean[] clear={false};main(()->clear[0]=((EditText)find("Message text")).getText().length()==0&&find("Message text").isEnabled());return clear[0];},"Submitted draft cleared across recreation");
            try(Cursor cursor=c.getContentResolver().query(Telephony.Sms.CONTENT_URI,new String[]{"_id"},"body = ?",new String[]{rotating},null)){while(cursor!=null&&cursor.moveToNext())ids.add(cursor.getLong(0));}
            // A short sent/received exchange makes the recovered blue and gray textures visible.
            Uri shortReply=MessageStore.incoming(c,number,prefix+" Welcome to Sense Messages.",System.currentTimeMillis(),1);ids.add(ContentUris.parseId(shortReply));
            String shortBody=prefix+" Thanks!";SmsSender.send(c,number,shortBody,1,(manager,destination,parts,sent,delivered)->{for(PendingIntent callback:sent)try{callback.send(Activity.RESULT_OK);}catch(PendingIntent.CanceledException e){throw new RuntimeException(e);}});
            try(Cursor cursor=c.getContentResolver().query(Telephony.Sms.CONTENT_URI,new String[]{"_id"},"body = ?",new String[]{shortBody},null)){while(cursor!=null&&cursor.moveToNext())ids.add(cursor.getLong(0));}
            await(()->{final boolean[] found={false};main(()->found[0]=find(shortBody)!=null);return found[0];},"Short exchange visible");screenshot("messages-conversation.png");
            report.putString("result","PASS: protected incoming SMS, provider conversations, unread/read state, HTC bubble UI, draft state, animated header/system Back navigation, rotation during submission, SENDTO, multipart intercepted transport, sent/failure/delivery aggregation. No real outgoing SMS.");
        }catch(Throwable error){report.putString("error",android.util.Log.getStackTraceString(error));try{screenshot("messages-failure.png");}catch(Exception ignored){}}
        finally{
            main(()->{if(activity!=null)activity.finish();});
            for(long id:ids)try{c.getContentResolver().delete(ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI,id),null,null);}catch(RuntimeException ignored){}
            c.getContentResolver().delete(Telephony.Sms.CONTENT_URI,"address = ? AND body LIKE ?",new String[]{number,prefix+"%"});
            c.getSystemService(NotificationManager.class).cancelAll();
            c.getSharedPreferences("drafts",Context.MODE_PRIVATE).edit().remove("body:"+number).apply();
        }
        finish(report.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,report);
    }
}
