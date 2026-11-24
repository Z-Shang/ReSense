package dev.sense.contacts;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.*;
import android.provider.ContactsContract.CommonDataKinds.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Real provider fixtures, isolated cleanup and intercepted external actions; no calls or messages. */
public final class PeopleInstrumentation extends Instrumentation {
    private ContactsActivity activity;
    private final ArrayList<Uri> rawContacts=new ArrayList<>(),groups=new ArrayList<>();
    private final ArrayList<ActivityMonitor> monitors=new ArrayList<>();
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private void ui(Runnable work){AtomicReference<Throwable> error=new AtomicReference<>();runOnMainSync(()->{try{work.run();}catch(Throwable e){error.set(e);}});if(error.get()!=null)throw new AssertionError(error.get());}
    private View find(View view,String value){if(!view.isShown())return null;if(view instanceof TextView t&&value.contentEquals(t.getText()))return view;if(value.equals(view.getContentDescription()))return view;if(view instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){View result=find(g.getChildAt(i),value);if(result!=null)return result;}return null;}
    private View find(String value){return find(activity.getWindow().getDecorView(),value);}
    private View clickable(View view,String value){if(!view.isShown())return null;if(view.isClickable()&&((view instanceof TextView t&&value.contentEquals(t.getText()))||value.equals(view.getContentDescription())))return view;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View hit=clickable(group.getChildAt(i),value);if(hit!=null)return hit;}return null;}
    private void click(String value){ui(()->{View view=clickable(activity.getWindow().getDecorView(),value);if(view==null)view=find(value);if(view==null)throw new AssertionError("Missing "+value);if(view.isClickable()){view.performClick();return;}View child=view;while(child.getParent() instanceof View parent){if(parent instanceof ListView list){int position=list.getPositionForView(child);list.performItemClick(child,position,list.getItemIdAtPosition(position));return;}if(parent.isClickable()){parent.performClick();return;}child=parent;}throw new AssertionError("Not clickable "+value);});}
    private void await(BooleanSupplier condition,String reason)throws Exception{long end=System.currentTimeMillis()+8000;while(System.currentTimeMillis()<end){AtomicReference<Boolean> done=new AtomicReference<>(false);ui(()->done.set(condition.getAsBoolean()));if(done.get())return;Thread.sleep(40);}throw new AssertionError(reason);}
    private EditText search(View view){if(view instanceof EditText edit)return edit;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){EditText e=search(group.getChildAt(i));if(e!=null)return e;}return null;}
    private void query(String value){ui(()->search(activity.getWindow().getDecorView()).setText(value));}
    private long contact(String name){ContentValues values=new ContentValues();values.putNull(RawContacts.ACCOUNT_TYPE);values.putNull(RawContacts.ACCOUNT_NAME);Uri uri=getTargetContext().getContentResolver().insert(RawContacts.CONTENT_URI,values);if(uri==null)throw new AssertionError("Insert failed");rawContacts.add(uri);long id=ContentUris.parseId(uri);data(id,StructuredName.CONTENT_ITEM_TYPE,name,null,null);return id;}
    private void data(long raw,String mime,String value,Integer type,String label){ContentValues values=new ContentValues();values.put(Data.RAW_CONTACT_ID,raw);values.put(Data.MIMETYPE,mime);values.put(Data.DATA1,value);if(type!=null)values.put(Data.DATA2,type);if(label!=null)values.put(Data.DATA3,label);getTargetContext().getContentResolver().insert(Data.CONTENT_URI,values);}
    private ActivityMonitor monitor(String action,String scheme,String mime)throws Exception{IntentFilter filter=new IntentFilter(action);if(scheme!=null)filter.addDataScheme(scheme);if(mime!=null)filter.addDataType(mime);ActivityMonitor monitor=new ActivityMonitor(filter,new ActivityResult(Activity.RESULT_CANCELED,null),true);addMonitor(monitor);monitors.add(monitor);return monitor;}
    private void screenshot(String filename)throws Exception{waitForIdleSync();Thread.sleep(200);try(var out=getTargetContext().openFileOutput(filename,0)){var bitmap=getUiAutomation().takeScreenshot();if(bitmap==null)throw new AssertionError("No screenshot");bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);bitmap.recycle();}}
    @Override public void onStart(){
        Bundle result=new Bundle();boolean read=getTargetContext().checkSelfPermission(Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED,write=getTargetContext().checkSelfPermission(Manifest.permission.WRITE_CONTACTS)==PackageManager.PERMISSION_GRANTED;
        try{
            activity=(ContactsActivity)startActivitySync(new Intent(getTargetContext(),ContactsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            if(!read)await(()->find("Allow contacts access")!=null||find("Open app permissions")!=null,"Permission explanation missing");
            getUiAutomation().grantRuntimePermission(getTargetContext().getPackageName(),Manifest.permission.READ_CONTACTS);getUiAutomation().grantRuntimePermission(getTargetContext().getPackageName(),Manifest.permission.WRITE_CONTACTS);
            getUiAutomation().adoptShellPermissionIdentity(Manifest.permission.READ_CONTACTS,Manifest.permission.WRITE_CONTACTS);
            long dana=contact("Dana HTC People"),elise=contact("Élise HTC People");
            data(dana,Phone.CONTENT_ITEM_TYPE,"5550177001",Phone.TYPE_MOBILE,null);data(dana,Phone.CONTENT_ITEM_TYPE,"5550177002",Phone.TYPE_WORK,null);data(dana,Email.CONTENT_ITEM_TYPE,"dana@example.test",Email.TYPE_WORK,null);data(dana,Note.CONTENT_ITEM_TYPE,"HTC native contact fixture",null,null);
            ContentValues values=new ContentValues();values.put(Groups.TITLE,"HTC Test Group");values.putNull(Groups.ACCOUNT_NAME);values.putNull(Groups.ACCOUNT_TYPE);values.put(Groups.GROUP_VISIBLE,1);Uri group=getTargetContext().getContentResolver().insert(Groups.CONTENT_URI,values);if(group==null)throw new AssertionError("Group insert failed");groups.add(group);data(dana,GroupMembership.CONTENT_ITEM_TYPE,group.getLastPathSegment(),null,null);
            ui(activity::finish);activity=(ContactsActivity)startActivitySync(new Intent(getTargetContext(),ContactsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            await(()->find("Dana HTC People")!=null&&find("Élise HTC People")!=null,"Aggregated contacts or phone-less contact missing");
            var snapshot=PeopleRepository.read(getTargetContext());var p=snapshot.people().stream().filter(person->person.name().equals("Dana HTC People")).findFirst().orElseThrow();if(p.fields().stream().filter(f->f.kind().equals("phone")).count()!=2)throw new AssertionError("Multiple numbers lost");
            query("elise");await(()->find("Élise HTC People")!=null&&find("Dana HTC People")==null,"Accent search failed");query("555 017 7002");await(()->find("Dana HTC People")!=null&&find("Élise HTC People")==null,"Secondary-number search failed");query("dana@example.test");await(()->find("Dana HTC People")!=null,"Email search failed");query("no such fixture person");await(()->find("No matches")!=null,"Empty state missing");query("");screenshot("people-list-test.png");
            click("Dana HTC People");await(()->find("5550177001")!=null&&find("5550177002")!=null&&find("dana@example.test")!=null,"Detail fields missing");screenshot("people-detail-test.png");
            ActivityMonitor sms=monitor(Intent.ACTION_SENDTO,"smsto",null);click("Message 5550177001");if(sms.getHits()!=1)throw new AssertionError("SMS intent missing");
            ActivityMonitor email=monitor(Intent.ACTION_SENDTO,"mailto",null);click("dana@example.test");if(email.getHits()!=1)throw new AssertionError("Email intent missing");
            ActivityMonitor edit=monitor(Intent.ACTION_EDIT,"content",Contacts.CONTENT_ITEM_TYPE);click("Edit");if(edit.getHits()!=1)throw new AssertionError("System edit handoff missing");
            click("Add to favorites");await(()->find("Remove from favorites")!=null,"Favorite provider update failed");click("Back to contacts");click("Favorites");await(()->find("Dana HTC People")!=null&&find("Élise HTC People")==null,"Favorites filter failed");
            click("Groups");await(()->find("HTC Test Group")!=null,"Provider group missing");click("HTC Test Group");await(()->find("Dana HTC People")!=null&&find("Élise HTC People")==null,"Group membership filter failed");click("Back to contacts");click("People");
            long live=contact("Robin HTC People");data(live,Phone.CONTENT_ITEM_TYPE,"5550177003",Phone.TYPE_HOME,null);await(()->find("Robin HTC People")!=null,"Live contacts refresh failed");
            ActivityMonitor add=monitor(Intent.ACTION_INSERT,null,RawContacts.CONTENT_TYPE);click("Add contact");if(add.getHits()!=1)throw new AssertionError("System add handoff missing");
            ui(activity::finish);activity=(ContactsActivity)startActivitySync(new Intent(Intent.ACTION_VIEW,p.uri()).setClass(getTargetContext(),ContactsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));await(()->find("5550177001")!=null,"Contact URI handling failed");
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);await(()->find("People")!=null&&find("Dana HTC People")!=null,"System Back failed to return to People");
            result.putString("result","PASS: real aggregated contacts, contacts without numbers, all-number/email/accent search, details, SMS/email/add/edit handoff, persistent favorites, groups/membership, observer refresh and contact URI handling");
        }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));}
        finally{
            for(ActivityMonitor monitor:monitors)removeMonitor(monitor);if(activity!=null)ui(activity::finish);
            try{getUiAutomation().adoptShellPermissionIdentity(Manifest.permission.WRITE_CONTACTS);for(Uri uri:rawContacts)getTargetContext().getContentResolver().delete(uri,null,null);for(Uri uri:groups)getTargetContext().getContentResolver().delete(uri,null,null);}catch(Throwable error){result.putString("cleanupError",error.toString());}getUiAutomation().dropShellPermissionIdentity();
            result.putBoolean("readPermissionOriginallyGranted",read);result.putBoolean("writePermissionOriginallyGranted",write);
        }
        finish(result.containsKey("error")||result.containsKey("cleanupError")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);
    }
}
