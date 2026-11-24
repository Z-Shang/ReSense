package dev.sense.phone;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CallLog.Calls;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Seeds only temporary emulator call log rows; never places a call. */
public final class PhoneInstrumentation extends ContactsInstrumentation {
    private final ArrayList<Uri> fixtures=new ArrayList<>();
    private Activity activity;
    private View find(View v,String text) {
        if(!v.isShown())return null;
        if(text.equals(v.getContentDescription()))return v;
        if(v instanceof TextView t && text.contentEquals(t.getText()))return v;
        if(v instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){View hit=find(g.getChildAt(i),text);if(hit!=null)return hit;}
        return null;
    }
    private View find(String text){return find(activity.getWindow().getDecorView(),text);}
    private void ui(Runnable runnable){
        AtomicReference<Throwable> failure=new AtomicReference<>();runOnMainSync(()->{try{runnable.run();}catch(Throwable e){failure.set(e);}});
        if(failure.get()!=null)throw new AssertionError(failure.get());
    }
    private void click(String text){ui(()->{
        View tab=activity.getWindow().getDecorView().findViewWithTag(text);if(tab!=null&&tab.isShown()&&tab.isClickable()){tab.performClick();return;}
        View view=find(text);if(view==null)throw new AssertionError("Missing "+text);
        if(text.matches("[0-9*#]"))view=(View)view.getParent();
        if(view.isClickable()){view.performClick();return;}
        View child=view;
        while(child.getParent() instanceof View parent){
            if(parent instanceof ListView list){int position=list.getPositionForView(child);list.performItemClick(child,position,list.getItemIdAtPosition(position));return;}child=parent;
        }
        throw new AssertionError("Not clickable: "+text);
    });}
    private void await(BooleanSupplier condition,String message)throws Exception {
        long end=System.currentTimeMillis()+7000;
        while(System.currentTimeMillis()<end){AtomicReference<Boolean> ready=new AtomicReference<>(false);ui(()->ready.set(condition.getAsBoolean()));if(ready.get())return;Thread.sleep(50);}
        throw new AssertionError(message);
    }
    private void clickWindowText(String label)throws Exception{
        long end=android.os.SystemClock.uptimeMillis()+7000;
        while(android.os.SystemClock.uptimeMillis()<end){
            android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
            if(root!=null)for(var node:root.findAccessibilityNodeInfosByText(label))if(label.contentEquals(node.getText()==null?"":node.getText())&&node.isVisibleToUser()){
                android.graphics.Rect bounds=new android.graphics.Rect();node.getBoundsInScreen(bounds);long now=android.os.SystemClock.uptimeMillis();
                MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,bounds.exactCenterX(),bounds.exactCenterY(),0),up=MotionEvent.obtain(now,now+50,MotionEvent.ACTION_UP,bounds.exactCenterX(),bounds.exactCenterY(),0);
                getUiAutomation().injectInputEvent(down,true);getUiAutomation().injectInputEvent(up,true);down.recycle();up.recycle();waitForIdleSync();return;
            }
            Thread.sleep(60);
        }
        throw new AssertionError("Missing popup/dialog item: "+label);
    }
    private void filter(String label)throws Exception{click("Call history options");clickWindowText("View");clickWindowText(label);}
    private void row(String number,String name,int type,long date,long duration,int presentation){
        ContentValues values=new ContentValues();values.put(Calls.NUMBER,number);values.put(Calls.CACHED_NAME,name);values.put(Calls.TYPE,type);values.put(Calls.DATE,date);values.put(Calls.DURATION,duration);values.put(Calls.NUMBER_PRESENTATION,presentation);values.put(Calls.NEW,0);
        Uri uri=getTargetContext().getContentResolver().insert(Calls.CONTENT_URI,values);if(uri==null)throw new AssertionError("Fixture insert failed");fixtures.add(uri);
    }
    private boolean contactsSuite,defaultSuite;
    @Override public void onCreate(Bundle args){contactsSuite=args!=null && "contacts".equals(args.getString("suite"));defaultSuite=args!=null&&"default".equals(args.getString("suite"));super.onCreate(args);}
    @Override public void onStart(){
        if(defaultSuite){new DefaultDialerInstrumentation(this).run();return;}
        if(contactsSuite){super.onStart();return;}
        Bundle result=new Bundle();boolean originallyGranted=getTargetContext().checkSelfPermission(Manifest.permission.READ_CALL_LOG)==PackageManager.PERMISSION_GRANTED;
        ActivityMonitor monitor=null;android.content.SharedPreferences prefs=getTargetContext().getSharedPreferences("PhoneActivity",0);boolean hadFilter=prefs.contains("historyViewType");int oldFilter=prefs.getInt("historyViewType",0);prefs.edit().putInt("historyViewType",0).commit();
        try{
            activity=startActivitySync(new Intent(getTargetContext(),PhoneActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            click("Call History");if(!originallyGranted)await(()->find("Allow call log access")!=null || find("Open app permissions")!=null,"Permission explanation missing");
            getUiAutomation().grantRuntimePermission(getTargetContext().getPackageName(),Manifest.permission.READ_CALL_LOG);
            getUiAutomation().adoptShellPermissionIdentity(Manifest.permission.READ_CALL_LOG,Manifest.permission.WRITE_CALL_LOG);
            long now=System.currentTimeMillis();
            for(int i=0;i<102;i++)row("5550199000","Older fixture "+i,Calls.OUTGOING_TYPE,now-86400000L-i*60000L,65,Calls.PRESENTATION_ALLOWED);
            row("5550199001","HTC incoming fixture",Calls.INCOMING_TYPE,now,125,Calls.PRESENTATION_ALLOWED);
            row("5550199002","HTC missed fixture",Calls.MISSED_TYPE,now+1000,0,Calls.PRESENTATION_ALLOWED);
            row("5550199003","Must remain hidden",Calls.INCOMING_TYPE,now+2000,42,Calls.PRESENTATION_RESTRICTED);
            click("Phone");click("Call History");
            await(()->find("HTC incoming fixture")!=null && find("HTC missed fixture")!=null && find("Private number")!=null,"System provider rows missing");
            ui(()->{if(find("Must remain hidden")!=null || find("5550199003")!=null)throw new AssertionError("Restricted caller exposed");if(find("Incoming · 2:05")==null)throw new AssertionError("Duration missing");});
            filter("Missed calls");await(()->find("HTC missed fixture")!=null && find("HTC incoming fixture")==null,"Missed filter failed");
            filter("Incoming calls");await(()->find("HTC incoming fixture")!=null&&find("HTC missed fixture")==null,"Incoming filter failed");
            filter("Outgoing calls");await(()->find("Older fixture 0")!=null&&find("HTC incoming fixture")==null&&find("HTC missed fixture")==null,"Outgoing filter failed");
            click("Phone");click("Call History");await(()->find("Older fixture 0")!=null&&find("HTC missed fixture")==null,"Filter not preserved across tabs");
            filter("Missed calls");await(()->find("HTC missed fixture")!=null,"Missed filter did not restore");
            row("5550199004","HTC observer fixture",Calls.MISSED_TYPE,now+3000,0,Calls.PRESENTATION_ALLOWED);
            await(()->find("HTC observer fixture")!=null,"Live provider changes not refreshed");
            filter("All calls");await(()->find("HTC incoming fixture")!=null,"All filter failed");
            ui(()->{ListView list=findList(activity.getWindow().getDecorView());list.setSelection(list.getCount()-1);});
            await(()->find("Load older calls")!=null,"Paging footer missing");click("Load older calls");
            await(()->findList(activity.getWindow().getDecorView()).getAdapter().getCount()>106,"Older calls not loaded");
            filter("All calls");
            ui(()->findList(activity.getWindow().getDecorView()).setSelection(0));Thread.sleep(250);
            try(java.io.FileOutputStream out=getTargetContext().openFileOutput("call-history-test.png",0)){
                android.graphics.Bitmap shot=getUiAutomation().takeScreenshot();if(shot==null)throw new AssertionError("Screenshot unavailable");shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);shot.recycle();
            }
            click("Phone");click("1");click("2");click("3");
            IntentFilter filter=new IntentFilter(Intent.ACTION_DIAL);filter.addDataScheme("tel");monitor=new ActivityMonitor(filter,new ActivityResult(Activity.RESULT_CANCELED,null),true);addMonitor(monitor);
            if(getTargetContext().getSystemService(android.app.role.RoleManager.class).isRoleHeld(android.app.role.RoleManager.ROLE_DIALER))throw new AssertionError("Refusing handoff test while Sense is default Phone");
            click("Call");if(monitor.getHits()!=1)throw new AssertionError("Dialer handoff not intercepted");
            click("Call History");await(()->find("HTC incoming fixture")!=null,"History return failed");click("HTC incoming fixture");
            await(()->find("5550199001")!=null && find("Call")!=null,"History row did not fill keypad");
            result.putString("result","PASS: permission state, real provider rows, hidden caller privacy, duration, four HTC View filters, filter persistence, observer refresh, pagination, keypad selection and intercepted dialer handoff");
        }catch(Throwable e){result.putString("error",e.toString());}
        finally{
            if(monitor!=null)removeMonitor(monitor);
            if(activity!=null)ui(activity::finish);
            try{getUiAutomation().adoptShellPermissionIdentity(Manifest.permission.WRITE_CALL_LOG);for(Uri uri:fixtures)getTargetContext().getContentResolver().delete(Calls.CONTENT_URI,Calls._ID+"=?",new String[]{uri.getLastPathSegment()});}catch(Throwable e){result.putString("cleanupError",e.toString());}
            if(hadFilter)prefs.edit().putInt("historyViewType",oldFilter).commit();else prefs.edit().remove("historyViewType").commit();
            getUiAutomation().dropShellPermissionIdentity();
            result.putBoolean("callLogPermissionOriginallyGranted", originallyGranted);
            // Host restores this permission after instrumentation completes: revoking it here kills this process.
        }
        finish(result.containsKey("error")||result.containsKey("cleanupError")||result.containsKey("permissionRestoreError")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);
    }
    private ListView findList(View view){if(view instanceof ListView list)return list;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){ListView list=findList(group.getChildAt(i));if(list!=null)return list;}return null;}
}
