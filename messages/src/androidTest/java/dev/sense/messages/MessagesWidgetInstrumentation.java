package dev.sense.messages;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Binds disposable real widgets to a test host; preserves the user's existing widgets and messages. */
final class MessagesWidgetInstrumentation {
    private final Instrumentation test;
    MessagesWidgetInstrumentation(Instrumentation test){this.test=test;}
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private View find(View root,String value){
        if(root instanceof TextView text&&text.getText().toString().contains(value))return root;
        if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View result=find(group.getChildAt(i),value);if(result!=null)return result;}
        return null;
    }
    void run(){
        Bundle result=new Bundle();Context context=test.getTargetContext();MessagesActivity activity=null;
        android.net.Uri fixture=null;android.net.Uri mmsFixture=null;AppWidgetHost[] host={null};java.util.List<Integer> ids=new ArrayList<>();
        boolean bindAllowed=false;
        try{
            check(MessageStore.isDefault(context),"Widget test requires temporary SMS role");
            fixture=MessageStore.incoming(context,"5550182042","HTC widget test message",System.currentTimeMillis(),1);
            check(fixture!=null,"Could not create test SMS");
            dev.sense.messages.mms.pdu.RetrieveConf mms=new dev.sense.messages.mms.pdu.RetrieveConf();
            mms.setFrom(new dev.sense.messages.mms.pdu.EncodedStringValue("5550182043"));
            dev.sense.messages.mms.pdu.PduBody body=new dev.sense.messages.mms.pdu.PduBody();
            dev.sense.messages.mms.pdu.PduPart part=new dev.sense.messages.mms.pdu.PduPart();
            part.setContentType(MmsStore.ascii("text/plain"));part.setData(MmsStore.ascii("HTC widget MMS message"));body.addPart(part);mms.setBody(body);
            mmsFixture=MmsStore.persist(context,mms,1,false,java.util.UUID.randomUUID().toString());
            activity=(MessagesActivity)test.startActivitySync(new Intent(context,MessagesActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            test.runOnMainSync(()->{host[0]=new AppWidgetHost(context,20642);host[0].deleteHost();host[0].startListening();});

            AppWidgetManager manager=AppWidgetManager.getInstance(context);
            String[] styles={"list","summary","card"};Class<?>[] providers={MessagesWidgetProvider.class,MessagesSummaryWidgetProvider.class,MessagesCardWidgetProvider.class};
            for(int i=0;i<styles.length;i++){
                String style=styles[i];ComponentName component=new ComponentName(context,providers[i]);
                AppWidgetProviderInfo info=manager.getInstalledProviders().stream().filter(p->p.provider.equals(component)).findFirst().orElse(null);
                check(info!=null,"Missing picker provider "+style);check("Messages".equals(info.loadLabel(context.getPackageManager())),"Widget styles are not grouped as Messages");check(info.previewImage!=0,"Stock preview missing "+style);
                int id=host[0].allocateAppWidgetId();ids.add(id);
                Bundle options=new Bundle();options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,320);options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,320);options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,400);options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,400);
                test.getUiAutomation().adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET");bindAllowed=true;
                try{check(manager.bindAppWidgetIdIfAllowed(id,component,options),"Widget bind failed "+style);}
                finally{test.getUiAutomation().dropShellPermissionIdentity();bindAllowed=false;}
                AppWidgetHostView[] view={null};MessagesActivity opened=activity;
                test.runOnMainSync(()->{view[0]=host[0].createView(context,id,info);FrameLayout root=new FrameLayout(opened);root.setBackgroundColor(0xff415661);int densityWidth=Math.round(320*context.getResources().getDisplayMetrics().density),densityHeight=Math.round(400*context.getResources().getDisplayMetrics().density);root.addView(view[0],new FrameLayout.LayoutParams(densityWidth,densityHeight,Gravity.CENTER));opened.setContentView(root);});
                MessagesWidgetProvider.updateAll(context);
                boolean ready=false;
                for(int poll=0;poll<100&&!ready;poll++){
                    boolean[] visible={false};test.runOnMainSync(()->visible[0]=find(view[0],"HTC widget test message")!=null);ready=visible[0];if(!ready)SystemClock.sleep(80);
                }
                check(ready,"Widget collection did not render "+style);
                test.runOnMainSync(()->check(find(view[0],"HTC widget MMS message")!=null,"MMS row missing "+style));
                if(i==0){
                    ContentValues edited=new ContentValues();edited.put("body","HTC widget changed message");check(context.getContentResolver().update(fixture,edited,null,null)==1,"Test SMS edit was not written");
                    try(android.database.Cursor saved=context.getContentResolver().query(fixture,new String[]{"body"},null,null,null)){check(saved!=null&&saved.moveToFirst()&&saved.getString(0).equals("HTC widget changed message"),"Edited SMS was not readable");}
                    boolean changed=false;
                    for(int poll=0;poll<100&&!changed;poll++){boolean[] visible={false};test.runOnMainSync(()->visible[0]=find(view[0],"HTC widget changed message")!=null);changed=visible[0];if(!changed)SystemClock.sleep(60);}
                    if(!changed){
                        java.lang.reflect.Field field=MessagesWidgetProvider.class.getDeclaredField("observer");field.setAccessible(true);
                        throw new AssertionError("Widget did not observe provider changes; observer="+(field.get(null)!=null)+"; direct rows="+MessageStore.threads(context,"").stream().map(MessageStore.ThreadRow::body).toList());
                    }
                    edited.put("body","HTC widget test message");context.getContentResolver().update(fixture,edited,null,null);
                }
                test.runOnMainSync(()->{View compose=view[0].findViewById(R.id.widget_compose);check(compose!=null&&compose.getWidth()>0,"Compose control missing "+style);});
                try(var out=context.openFileOutput("messages-widget-"+style+".png",0)){Bitmap screen=test.getUiAutomation().takeScreenshot();screen.compress(Bitmap.CompressFormat.PNG,100,out);screen.recycle();}
                manager.updateAppWidget(id,MessagesWidgetProvider.render(context,id,style,java.util.List.of(),"Open Messages to enable SMS access"));
                test.waitForIdleSync();
                boolean empty=false;
                for(int poll=0;poll<60&&!empty;poll++){boolean[] visible={false};test.runOnMainSync(()->{View text=find(view[0],"Open Messages to enable SMS access");visible[0]=text!=null&&text.isShown();});empty=visible[0];if(!empty)SystemClock.sleep(50);}
                check(empty,"Permission/empty state missing "+style);
                host[0].deleteAppWidgetId(id);ids.remove(Integer.valueOf(id));
            }
            result.putString("result","PASS: all three stock widget previews discoverable; real widget-host binding, live SMS/MMS rows, provider change refresh, compose controls and empty/permission states; no carrier send");
        }catch(Throwable failure){result.putString("error",android.util.Log.getStackTraceString(failure));}
        finally{
            if(host[0]!=null){for(int id:ids)host[0].deleteAppWidgetId(id);test.runOnMainSync(host[0]::stopListening);}
            if(bindAllowed)test.getUiAutomation().dropShellPermissionIdentity();
            if(activity!=null){MessagesActivity closing=activity;test.runOnMainSync(closing::finish);}
            if(fixture!=null)context.getContentResolver().delete(fixture,null,null);
            if(mmsFixture!=null)context.getContentResolver().delete(mmsFixture,null,null);
        }
        test.finish(result.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);
    }
}
