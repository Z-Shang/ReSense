package dev.sense.phone;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** HTC People artwork over modern, read-only ContactsContract data. */
final class ContactsView extends LinearLayout {
    private record Contact(long id,String name,String number,String label,String photo) {}
    private final Activity activity;
    private final long groupId;
    private final Runnable requestPermission;
    private final Consumer<String> selectNumber;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor(), photos=Executors.newSingleThreadExecutor();
    private final ArrayList<Contact> contacts=new ArrayList<>();
    private final ArrayList<Object> rows=new ArrayList<>();
    private final PeopleAdapter adapter=new PeopleAdapter();
    private final LruCache<String,Bitmap> images=new LruCache<>(4*1024*1024){@Override protected int sizeOf(String key,Bitmap bitmap){return bitmap.getAllocationByteCount();}};
    private final HashSet<String> pendingPhotos=new HashSet<>();
    private EditText search;
    private TextView status;
    private boolean closed, observing;
    private int generation;
    private final Runnable refresh=this::load;
    private final ContentObserver observer=new ContentObserver(handler){@Override public void onChange(boolean selfChange){handler.removeCallbacks(refresh);handler.postDelayed(refresh,200);}};
    ContactsView(Activity activity,Runnable requestPermission,Consumer<String> selectNumber){
        this(activity,requestPermission,selectNumber,-1);
    }
    ContactsView(Activity activity,Runnable requestPermission,Consumer<String> selectNumber,long groupId){
        super(activity);this.activity=activity;this.groupId=groupId;this.requestPermission=requestPermission;this.selectNumber=selectNumber;setOrientation(VERTICAL);setBackgroundColor(0xfffafafa);
    }
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size,int color){TextView view=new TextView(activity);view.setText(value);view.setTextSize(size);view.setTextColor(color);return view;}
    @Override protected void onAttachedToWindow(){
        super.onAttachedToWindow();
        if(activity.checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED){permissionView();return;}
        buildList();
        try{activity.getContentResolver().registerContentObserver(ContactsContract.Contacts.CONTENT_URI,true,observer);observing=true;}catch(SecurityException ignored){}load();
    }
    @Override protected void onDetachedFromWindow(){
        closed=true;generation++;handler.removeCallbacksAndMessages(null);if(observing)activity.getContentResolver().unregisterContentObserver(observer);worker.shutdownNow();photos.shutdownNow();images.evictAll();super.onDetachedFromWindow();
    }
    private void permissionView(){
        addPeopleHeader();
        TextView heading=text("Contacts access",22,0xff222222);heading.setPadding(dp(20),dp(24),dp(20),dp(8));addView(heading);
        TextView explanation=text("Allow contacts access to find people and choose a phone number.",16,0xff555555);explanation.setPadding(dp(20),dp(8),dp(20),dp(16));addView(explanation);
        boolean blocked=activity.getPreferences(0).getBoolean("contactsPermissionRequested",false)&&!activity.shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS);
        TextView allow=text(blocked?"Open app permissions":"Allow access to contacts",16,0xffffffff);allow.setGravity(Gravity.CENTER);allow.setBackgroundResource(dev.sense.framework.R.drawable.sense_common_tab_button_center_rest);
        allow.setOnClickListener(v->{if(blocked)activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.fromParts("package",activity.getPackageName(),null)));else{activity.getPreferences(0).edit().putBoolean("contactsPermissionRequested",true).apply();requestPermission.run();}});addView(allow,new LayoutParams(-1,dp(52)));
    }
    private void addPeopleHeader(){
        if(groupId>=0)return;
        LinearLayout header=new LinearLayout(activity);header.setGravity(Gravity.CENTER_VERTICAL);header.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xff646964,0xff353b36,0xff242a25}));
        TextView title=text("People",22,0xffffffff);title.setAccessibilityHeading(true);title.setPadding(dp(14),0,dp(8),0);header.addView(title,new LayoutParams(0,-2,1));
        ImageButton add=new dev.sense.framework.SenseHeaderIconButton(activity,dev.sense.framework.R.drawable.sense_common_icon_glance_add_rest,dev.sense.framework.R.drawable.sense_common_icon_glance_add_on,"Add contact");
        add.setOnClickListener(v->{try{activity.startActivity(new Intent(ContactsContract.Intents.Insert.ACTION).setType(ContactsContract.RawContacts.CONTENT_TYPE));}catch(android.content.ActivityNotFoundException error){Toast.makeText(activity,"No contact editor is installed",Toast.LENGTH_SHORT).show();}});header.addView(add,new LayoutParams(dp(48),dp(48)));addView(header,new LayoutParams(-1,dp(56)));
    }
    private void buildList(){
        addPeopleHeader();
        dev.sense.framework.SenseSearchBox toolbar=new dev.sense.framework.SenseSearchBox(activity,"Search contacts","Clear contact search");search=toolbar.getTextField();addView(toolbar,new LayoutParams(-1,dp(56)));
        status=text("Loading contacts…",15,0xff555555);status.setPadding(dp(20),dp(16),dp(20),dp(16));status.setGravity(Gravity.CENTER);addView(status);
        ListView list=new ListView(activity);dev.sense.framework.SenseStyle.list(list);list.setAdapter(adapter);
        list.setOnItemClickListener((parent,view,position,id)->{if(rows.get(position) instanceof Contact c){activity.getSystemService(InputMethodManager.class).hideSoftInputFromWindow(search.getWindowToken(),0);selectNumber.accept(c.number());}});addView(list,new LayoutParams(-1,0,1));
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){filter();}public void afterTextChanged(Editable editable){}});
        setFocusableInTouchMode(true);requestFocus();
    }
    private void load(){
        if(closed)return;int token=++generation;
        worker.execute(()->{
            ArrayList<Contact> found=new ArrayList<>();String error=null;
            try {
                java.util.Set<Long> members=new java.util.HashSet<>();
                if(groupId>=0)try(var membership=activity.getContentResolver().query(ContactsContract.Data.CONTENT_URI,new String[]{ContactsContract.Data.CONTACT_ID},ContactsContract.Data.MIMETYPE+"=? AND "+ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID+"=?",new String[]{ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE,Long.toString(groupId)},null)){if(membership!=null)while(membership.moveToNext())members.add(membership.getLong(0));}
                try(Cursor cursor=activity.getContentResolver().query(Phone.CONTENT_URI,new String[]{Phone._ID,Phone.DISPLAY_NAME_PRIMARY,Phone.NUMBER,Phone.TYPE,Phone.LABEL,Phone.PHOTO_THUMBNAIL_URI,Phone.CONTACT_ID},null,null,Phone.SORT_KEY_PRIMARY+" ASC, "+Phone._ID+" ASC")){
                if(cursor==null)throw new IllegalStateException("Contacts provider unavailable");
                while(cursor.moveToNext()){
                    if(groupId>=0&&!members.contains(cursor.getLong(6)))continue;
                    String number=cursor.getString(2);if(number==null || number.isBlank())continue;
                    String name=cursor.getString(1);if(name==null || name.isBlank())name=number;
                    found.add(new Contact(cursor.getLong(0),name,number,Phone.getTypeLabel(getResources(),cursor.getInt(3),cursor.getString(4)).toString(),cursor.getString(5)));
                }
                }
            }catch(SecurityException e){error="Contacts access was denied. Check Contacts in app permissions.";}catch(RuntimeException e){error="Couldn’t load contacts. Tap to retry.";}
            String failure=error;handler.post(()->{if(closed||token!=generation)return;if(failure!=null){contacts.clear();rows.clear();adapter.notifyDataSetChanged();status.setText(failure);status.setVisibility(VISIBLE);status.setOnClickListener(v->load());return;}contacts.clear();contacts.addAll(found);status.setOnClickListener(null);filter();});
        });
    }
    private static String normalized(String value){return Normalizer.normalize(value,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT);}
    private void filter(){
        String query=normalized(search.getText().toString().trim()),numeric=query.replaceAll("[^0-9+]","");rows.clear();String previous=null;
        for(Contact c:contacts){
            boolean match=query.isEmpty()||normalized(c.name()).contains(query)||c.number().toLowerCase(Locale.ROOT).contains(query)||(!numeric.isEmpty()&&query.matches("[0-9+() .-]+")&&c.number().replaceAll("[^0-9+]","").contains(numeric));
            if(!match)continue;
            int initial=c.name().codePointAt(0);String section=Character.isLetter(initial)?new String(Character.toChars(Character.toUpperCase(initial))):"#";
            if(!section.equals(previous)){rows.add(section);previous=section;}rows.add(c);
        }
        adapter.notifyDataSetChanged();status.setText(query.isEmpty()?"No contacts with phone numbers":"No matching contacts");status.setVisibility(rows.isEmpty()?VISIBLE:GONE);
    }
    private void bindPhoto(ImageView image,String uri){
        image.setImageResource(dev.sense.framework.R.drawable.sense_people_default_photo);image.setTag(uri);
        if(uri==null||uri.isBlank())return;Bitmap cached=images.get(uri);if(cached!=null){image.setImageBitmap(cached);return;}
        if(!pendingPhotos.add(uri))return;
        photos.execute(()->{
            Bitmap bitmap=null;
            try{bitmap=ImageDecoder.decodeBitmap(ImageDecoder.createSource(activity.getContentResolver(),Uri.parse(uri)),(decoder,info,source)->{decoder.setTargetSize(dp(48),dp(48));decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);});}catch(IOException|RuntimeException ignored){}
            Bitmap result=bitmap;handler.post(()->{pendingPhotos.remove(uri);if(closed)return;if(result!=null){images.put(uri,result);adapter.notifyDataSetChanged();}});
        });
    }
    private final class PeopleAdapter extends BaseAdapter {
        public int getCount(){return rows.size();}public Object getItem(int position){return rows.get(position);}public long getItemId(int position){return rows.get(position) instanceof Contact c?c.id():-position-1;}
        public int getViewTypeCount(){return 2;}public int getItemViewType(int position){return rows.get(position) instanceof Contact?1:0;}public boolean areAllItemsEnabled(){return false;}public boolean isEnabled(int position){return rows.get(position) instanceof Contact;}
        public View getView(int position,View recycled,ViewGroup parent){
            if(rows.get(position) instanceof String letter){TextView header=recycled instanceof TextView t?t:text("",13,0xffffffff);header.setText(letter);header.setTypeface(null,Typeface.BOLD);header.setPadding(dp(14),dp(5),dp(14),dp(5));header.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xff676767,0xff414141}));return header;}
            Contact c=(Contact)rows.get(position);PeopleRow row=recycled instanceof PeopleRow r?r:new PeopleRow();row.name.setText(c.name());row.number.setText(c.label()+" · "+c.number());bindPhoto(row.photo,c.photo());row.setContentDescription(c.name()+", "+c.label()+", "+c.number());return row;
        }
    }
    private final class PeopleRow extends LinearLayout {
        final ImageView photo;final TextView name,number;
        PeopleRow(){super(activity);setGravity(Gravity.CENTER_VERTICAL);setMinimumHeight(dp(73));setPadding(dp(12),dp(8),dp(12),dp(8));StateListDrawable background=new StateListDrawable();background.addState(new int[]{android.R.attr.state_pressed},activity.getDrawable(dev.sense.framework.R.drawable.sense_list_selector_background_pressed));background.addState(new int[]{},new android.graphics.drawable.ColorDrawable(0x00000000));setBackground(background);photo=new ImageView(activity);photo.setScaleType(ImageView.ScaleType.CENTER_CROP);photo.setBackgroundResource(dev.sense.framework.R.drawable.sense_common_photo_frame);photo.setPadding(dp(2),dp(2),dp(2),dp(2));addView(photo,new LayoutParams(dp(48),dp(48)));
            LinearLayout labels=new LinearLayout(activity);labels.setOrientation(VERTICAL);labels.setPadding(dp(12),0,0,0);name=text("",18,0xff202020);name.setTypeface(null,Typeface.NORMAL);number=text("",14,0xff666666);for(TextView text:new TextView[]{name,number}){text.setSingleLine(true);text.setEllipsize(TextUtils.TruncateAt.END);labels.addView(text,new LayoutParams(-1,-2));}addView(labels,new LayoutParams(0,-2,1));}
    }
}
