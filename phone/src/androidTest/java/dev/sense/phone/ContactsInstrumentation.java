package dev.sense.phone;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Exercises HTC contacts with temporary local contacts; never places a call. */
public class ContactsInstrumentation extends Instrumentation {
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
        String key=text.equals("People")?"Contacts":text.equals("Phone")?"Keypad":text;View tab=activity.getWindow().getDecorView().findViewWithTag(key);if(tab!=null&&tab.isShown()&&tab.isClickable()){tab.performClick();return;}
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
    private long contact(String name,String number) {
        var resolver=getTargetContext().getContentResolver();
        ContentValues raw=new ContentValues();raw.putNull(ContactsContract.RawContacts.ACCOUNT_NAME);raw.putNull(ContactsContract.RawContacts.ACCOUNT_TYPE);
        Uri uri=resolver.insert(ContactsContract.RawContacts.CONTENT_URI,raw);if(uri==null)throw new AssertionError("Raw contact insert failed");fixtures.add(uri);
        long id=ContentUris.parseId(uri);
        ContentValues data=new ContentValues();data.put(ContactsContract.Data.RAW_CONTACT_ID,id);data.put(ContactsContract.Data.MIMETYPE,ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE);data.put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,name);resolver.insert(ContactsContract.Data.CONTENT_URI,data);
        data=new ContentValues();data.put(ContactsContract.Data.RAW_CONTACT_ID,id);data.put(ContactsContract.Data.MIMETYPE,ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE);data.put(ContactsContract.CommonDataKinds.Phone.NUMBER,number);data.put(ContactsContract.CommonDataKinds.Phone.TYPE,ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE);resolver.insert(ContactsContract.Data.CONTENT_URI,data);
        return id;
    }
    private EditText search(View view){if(view instanceof EditText edit)return edit;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){EditText edit=search(group.getChildAt(i));if(edit!=null)return edit;}return null;}
    private void query(String value){ui(()->search(activity.getWindow().getDecorView()).setText(value));}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();boolean originallyGranted=getTargetContext().checkSelfPermission(Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED;
        try{
            activity=startActivitySync(new Intent(getTargetContext(),PhoneActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));click("People");
            if(!originallyGranted)await(()->find("Allow access to contacts")!=null || find("Open app permissions")!=null,"Permission explanation missing");
            getUiAutomation().grantRuntimePermission(getTargetContext().getPackageName(),Manifest.permission.READ_CONTACTS);
            getUiAutomation().adoptShellPermissionIdentity(Manifest.permission.READ_CONTACTS,Manifest.permission.WRITE_CONTACTS);
            long ada=contact("Ada HTC Contact","5550188001");contact("Éloise HTC Contact","5550188002");
            click("Phone");click("People");await(()->find("Ada HTC Contact")!=null && find("Éloise HTC Contact")!=null,"Provider contacts missing");
            ui(()->{if(find("Mobile · 5550188001")==null)throw new AssertionError("Phone type missing");if(find("A")==null)throw new AssertionError("Alphabet heading missing");});
            ui(()->{if(find("Add contact")==null)throw new AssertionError("HTC People Add action missing");if(find("Clear contact search")!=null)throw new AssertionError("Empty search must hide clear control");});
            IntentFilter insertFilter=new IntentFilter(ContactsContract.Intents.Insert.ACTION);insertFilter.addDataType(ContactsContract.RawContacts.CONTENT_TYPE);ActivityMonitor editor=new ActivityMonitor(insertFilter,new ActivityResult(Activity.RESULT_CANCELED,null),true);addMonitor(editor);click("Add contact");if(editor.getHits()!=1)throw new AssertionError("Contact editor handoff missing");removeMonitor(editor);
            query("eloise");await(()->find("Éloise HTC Contact")!=null&&find("Ada HTC Contact")==null,"Accent-insensitive search failed");
            ui(()->{if(find("Clear contact search")==null)throw new AssertionError("Typed search must show HTC clear control");});click("Clear contact search");await(()->find("Ada HTC Contact")!=null&&find("Éloise HTC Contact")!=null,"Clear search did not restore rows");
            query("555 018 8001");await(()->find("Ada HTC Contact")!=null&&find("Éloise HTC Contact")==null,"Formatted-number search failed");
            query("nobody matches this fixture");await(()->find("No matching contacts")!=null,"Empty search state missing");
            query("");contact("Beatrice HTC Contact","5550188003");await(()->find("Beatrice HTC Contact")!=null,"Live contacts update missing");
            Thread.sleep(250);
            try(java.io.FileOutputStream out=getTargetContext().openFileOutput("contacts-test.png",0)){
                android.graphics.Bitmap shot=getUiAutomation().takeScreenshot();if(shot==null)throw new AssertionError("Screenshot unavailable");shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);shot.recycle();
            }
            android.content.ContentValues groupValues=new android.content.ContentValues();groupValues.put(ContactsContract.Groups.TITLE,"HTC Dialer Group");groupValues.putNull(ContactsContract.Groups.ACCOUNT_NAME);groupValues.putNull(ContactsContract.Groups.ACCOUNT_TYPE);
            Uri group=getTargetContext().getContentResolver().insert(ContactsContract.Groups.CONTENT_URI,groupValues);if(group==null)throw new AssertionError("Group insert failed");fixtures.add(group);
            android.content.ContentValues membership=new android.content.ContentValues();membership.put(ContactsContract.Data.RAW_CONTACT_ID,ada);membership.put(ContactsContract.Data.MIMETYPE,ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE);membership.put(ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID,android.content.ContentUris.parseId(group));getTargetContext().getContentResolver().insert(ContactsContract.Data.CONTENT_URI,membership);
            android.content.ContentValues emptyValues=new android.content.ContentValues(groupValues);emptyValues.put(ContactsContract.Groups.TITLE,"HTC Empty Group");Uri emptyGroup=getTargetContext().getContentResolver().insert(ContactsContract.Groups.CONTENT_URI,emptyValues);if(emptyGroup==null)throw new AssertionError("Empty group insert failed");fixtures.add(emptyGroup);
            click("Groups");await(()->find("HTC Dialer Group (1)")!=null&&find("HTC Empty Group (0)")!=null,"Groups tab missing provider data/counts");
            Thread.sleep(250);try(java.io.FileOutputStream out=getTargetContext().openFileOutput("groups-test.png",0)){var shot=getUiAutomation().takeScreenshot();shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);shot.recycle();}
            click("HTC Empty Group (0)");await(()->find("No contacts with phone numbers")!=null&&find("HTC Empty Group")!=null,"Empty group layout/title missing");click("Back to groups");await(()->find("HTC Dialer Group (1)")!=null,"Empty group Back failed");
click("HTC Dialer Group (1)");await(()->find("Ada HTC Contact")!=null&&find("Éloise HTC Contact")==null,"Group membership not filtered");await(()->find("HTC Dialer Group")!=null,"Selected group title missing");
            Thread.sleep(250);try(java.io.FileOutputStream out=getTargetContext().openFileOutput("group-members-test.png",0)){var shot=getUiAutomation().takeScreenshot();shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);shot.recycle();}
            click("Back to groups");await(()->find("HTC Dialer Group (1)")!=null&&find("Back to groups")==null,"HTC group Back transition did not settle");
            click("HTC Dialer Group (1)");await(()->find("Ada HTC Contact")!=null,"Group did not reopen after animated Back");
            click("Ada HTC Contact");await(()->find("5550188001")!=null && find("Call")!=null,"Contact did not populate keypad");
            click("Hide keypad");ui(()->{if(find("1")!=null || find("Call")==null)throw new AssertionError("Keypad toggle hid the call action");});click("Show keypad");await(()->find("1")!=null,"Keypad did not reopen");
            Intent speechResult=new Intent().putStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS,new java.util.ArrayList<>(java.util.List.of("555 010 1234")));
            ActivityMonitor speech=new ActivityMonitor(new IntentFilter(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH),new ActivityResult(Activity.RESULT_OK,speechResult),true);addMonitor(speech);click("Voice dial");await(()->find("5550101234")!=null,"Voice input did not prefill the number");if(speech.getHits()!=1)throw new AssertionError("Voice input not intercepted");removeMonitor(speech);
            ui(()->{android.graphics.Rect bounds=new android.graphics.Rect();View call=find("Call");if(call==null||!call.getGlobalVisibleRect(bounds)||bounds.height()==0)throw new AssertionError("Call row is clipped");});
            result.putString("result","PASS: permission UI, real contacts provider, HTC People header/Add, conditional clear search, rows and sections, name/number search, empty state, live updates, HTC group labels/counts, empty group, named member header, group membership, keypad toggle, voice prefill and pinned call action");
        }catch(Throwable e){result.putString("error",e.toString());}
        finally{
            if(activity!=null)ui(activity::finish);
            try{getUiAutomation().adoptShellPermissionIdentity(Manifest.permission.WRITE_CONTACTS);for(Uri uri:fixtures)getTargetContext().getContentResolver().delete(uri,null,null);}catch(Throwable e){result.putString("cleanupError",e.toString());}
            getUiAutomation().dropShellPermissionIdentity();result.putBoolean("contactsPermissionOriginallyGranted",originallyGranted);
        }
        finish(result.containsKey("error")||result.containsKey("cleanupError")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);
    }
}
