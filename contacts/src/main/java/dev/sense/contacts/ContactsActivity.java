package dev.sense.contacts;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.graphics.Typeface;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.Settings;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.Executors;
import dev.sense.contacts.PeopleRepository.*;
import static dev.sense.framework.SenseStyle.*;

/** Modern native People app using original Pyramid artwork and real aggregated contacts. */
public final class ContactsActivity extends Activity {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ArrayList<Object> rows=new ArrayList<>();
    private final PeopleAdapter adapter=new PeopleAdapter();
    private Snapshot snapshot=new Snapshot(List.of(),List.of());
    private LinearLayout root,body,header;
    private EditText search;
    private TextView status;
    private ContactPhotos photos;
    private String tab="People",query="";
    private long selected=-1,group=-1,pendingFavorite=-1;
    private boolean pendingFavoriteValue,loaded,observing;
    private int generation;
    private Uri pendingUri;
    private final Runnable refresh=this::load;
    private final ContentObserver observer=new ContentObserver(main){@Override public void onChange(boolean selfChange){main.removeCallbacks(refresh);main.postDelayed(refresh,200);}};
    private int dp(float value){return dev.sense.framework.SenseStyle.dp(this,value);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setDecorFitsSystemWindows(false);photos=new ContactPhotos(this);
        if(state!=null){tab=state.getString("tab","People");query=state.getString("query","");selected=state.getLong("selected",-1);group=state.getLong("group",-1);pendingFavorite=state.getLong("pendingFavorite",-1);pendingFavoriteValue=state.getBoolean("pendingFavoriteValue");String uri=state.getString("pendingUri");if(uri!=null)pendingUri=Uri.parse(uri);}
        else if(Intent.ACTION_VIEW.equals(getIntent().getAction()))pendingUri=getIntent().getData();
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xff202020);
        root.setOnApplyWindowInsetsListener((view,insets)->{var safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());view.setPadding(safe.left,safe.top,safe.right,safe.bottom);return insets;});setContentView(root);
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::back);
        render();
    }
    @Override protected void onResume(){super.onResume();if(permitted()){
        if(!observing){getContentResolver().registerContentObserver(Contacts.CONTENT_URI,true,observer);getContentResolver().registerContentObserver(ContactsContract.Groups.CONTENT_URI,true,observer);observing=true;}load();
    }else render();}
    private boolean permitted(){return checkSelfPermission(Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED;}
    private void back(){if(selected>=0){selected=-1;render();}else if(group>=0){group=-1;query="";render();}else finish();}
    private Person person(){for(Person p:snapshot.people())if(p.id()==selected)return p;return null;}
    private Group activeGroup(){for(Group g:snapshot.groups())if(g.id()==group)return g;return null;}
    private void render(){
        root.removeAllViews();search=null;
        header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setBackground(bar());
        if(selected>=0||group>=0){TextView back=action(this,"‹",this::back);back.setTextSize(32);back.setContentDescription("Back to contacts");header.addView(back,new LinearLayout.LayoutParams(dp(48),-1));}
        TextView title=text(this,selected>=0?"Contact":group>=0&&activeGroup()!=null?activeGroup().name():"People",24,0xffffffff);title.setSingleLine();title.setEllipsize(TextUtils.TruncateAt.END);title.setGravity(Gravity.CENTER_VERTICAL);title.setPadding(dp(14),0,dp(8),0);title.setAccessibilityHeading(true);header.addView(title,new LinearLayout.LayoutParams(0,-1,1));
        if(selected<0)header.addView(icon(this,R.drawable.people_phone_icon_addcontact,"Add contact",this::addContact),new LinearLayout.LayoutParams(dp(52),dp(48)));
        else if(person()!=null)header.addView(action(this,"Edit",this::editContact),new LinearLayout.LayoutParams(dp(60),dp(48)));
        root.addView(header,new LinearLayout.LayoutParams(-1,dp(56)));
        body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setBackgroundColor(0xfffafafa);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        if(!permitted()){permissionView();return;}
        if(selected>=0){detail();return;}
        searchBar();
        status=text(this,loaded?"":"Loading contacts…",15,0xff555555);status.setGravity(Gravity.CENTER);status.setPadding(dp(16),dp(16),dp(16),dp(16));body.addView(status);
        ListView list=new ListView(this);dev.sense.framework.SenseStyle.list(list);list.setAdapter(adapter);
        list.setOnItemClickListener((parent,view,position,id)->{
            if(position>=rows.size())return;Object row=rows.get(position);hideKeyboard();
            if(row instanceof Person p){selected=p.id();render();}
            else if(row instanceof Group g){group=g.id();query="";render();}
        });body.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        if(group<0){LinearLayout tabs=new LinearLayout(this);int i=0;
            for(String name:new String[]{"People","Favorites","Groups"}){TextView item=dev.sense.framework.SenseStyle.tab(this,name,i);item.setOnClickListener(v->{hideKeyboard();tab=name;query="";render();});item.setSelected(tab.equals(name));tabs.addView(item,new LinearLayout.LayoutParams(0,dp(52),1));i++;}root.addView(tabs);}
        filter();root.setFocusableInTouchMode(true);root.requestFocus();
    }
    private void permissionView(){
        TextView info=text(this,"Allow contacts access to show your people, favorites and groups.",17,0xff444444);info.setPadding(dp(20),dp(24),dp(20),dp(24));body.addView(info);
        boolean blocked=getPreferences(0).getBoolean("requestedRead",false)&&!shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS);
        body.addView(action(this,blocked?"Open app permissions":"Allow contacts access",()->{if(blocked)launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.fromParts("package",getPackageName(),null)));else{getPreferences(0).edit().putBoolean("requestedRead",true).apply();requestPermissions(new String[]{Manifest.permission.READ_CONTACTS},1);}}),new LinearLayout.LayoutParams(-1,dp(52)));
    }
    private void searchBar(){
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setBackgroundColor(0xffdddddd);bar.setPadding(dp(10),dp(8),dp(10),dp(8));
        ImageView glass=new ImageView(this);glass.setImageResource(dev.sense.framework.R.drawable.sense_people_search);glass.setScaleType(ImageView.ScaleType.CENTER_INSIDE);bar.addView(glass,new LinearLayout.LayoutParams(dp(36),dp(40)));
        search=new EditText(this);search.setTextSize(16);search.setTextColor(0xff222222);search.setHintTextColor(0xff777777);search.setSingleLine();search.setHint(tab.equals("Groups")&&group<0?"Search groups":"Search contacts");search.setContentDescription("Search contacts");search.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_FILTER);
        dev.sense.framework.SenseStyle.input(search);search.setText(query);bar.addView(search,new LinearLayout.LayoutParams(0,dp(44),1));
        ImageButton clear=new ImageButton(this);clear.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);clear.setColorFilter(0xff555555);clear.setBackgroundColor(0x00000000);clear.setContentDescription("Clear contact search");clear.setOnClickListener(v->search.setText(""));bar.addView(clear,new LinearLayout.LayoutParams(dp(48),dp(48)));body.addView(bar);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void afterTextChanged(Editable editable){}public void onTextChanged(CharSequence s,int start,int before,int count){query=s.toString();filter();}});
    }
    private static String normalize(String value){return Normalizer.normalize(value,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT);}
    private void filter(){
        rows.clear();String q=normalize(query.trim()),numeric=q.replaceAll("[^0-9+]","");
        if(tab.equals("Groups")&&group<0){for(Group g:snapshot.groups())if(normalize(g.name()).contains(q))rows.add(g);}
        else{
            Group current=activeGroup();String previous=null;
            for(Person p:snapshot.people()){
                if(group>=0&&(current==null||!current.members().contains(p.id())))continue;
                if(group<0&&tab.equals("Favorites")&&!p.favorite())continue;
                boolean match=q.isEmpty()||normalize(p.name()).contains(q);
                if(!match)for(Field f:p.fields())if((f.kind().equals("phone")||f.kind().equals("email"))&&(normalize(f.value()).contains(q)||(!numeric.isEmpty()&&q.matches("[0-9+() .-]+")&&f.value().replaceAll("[^0-9+]","").contains(numeric)))){match=true;break;}
                if(!match)continue;
                int cp=normalize(p.name()).codePointAt(0);String section=Character.isLetter(cp)?new String(Character.toChars(Character.toUpperCase(cp))):"#";
                if(!section.equals(previous)){rows.add(section);previous=section;}rows.add(p);
            }
        }
        adapter.notifyDataSetChanged();if(status!=null){status.setText(!loaded?"Loading contacts…":!q.isEmpty()?"No matches":group>=0?"No contacts in this group":tab.equals("Favorites")?"No favorite contacts":tab.equals("Groups")?"No contact groups":"No contacts yet");status.setVisibility(rows.isEmpty()?View.VISIBLE:View.GONE);}
    }
    private void load(){
        if(isDestroyed()||!permitted())return;int token=++generation;Uri incoming=pendingUri;
        worker.execute(()->{
            Snapshot result;long requested=-1;
            try{
                result=PeopleRepository.read(this);
                if(incoming!=null&&"content".equals(incoming.getScheme())&&ContactsContract.AUTHORITY.equals(incoming.getAuthority()))try(var cursor=getContentResolver().query(incoming,new String[]{Contacts._ID},null,null,null)){if(cursor!=null&&cursor.moveToFirst())requested=cursor.getLong(0);}
            }catch(RuntimeException error){main.post(()->{if(!isDestroyed()&&token==generation){loaded=true;if(status!=null){status.setText("Couldn’t load contacts. Tap to retry.");status.setVisibility(View.VISIBLE);status.setOnClickListener(v->load());}else Toast.makeText(this,"Couldn’t load contacts",Toast.LENGTH_SHORT).show();}});return;}
            long requestedId=requested;main.post(()->{if(isDestroyed()||token!=generation)return;snapshot=result;loaded=true;photos.invalidate();if(incoming!=null){pendingUri=null;selected=requestedId;if(requestedId<0)Toast.makeText(this,"Contact unavailable",Toast.LENGTH_SHORT).show();}
                if(selected>=0&&person()==null){selected=-1;Toast.makeText(this,"Contact no longer available",Toast.LENGTH_SHORT).show();}if(group>=0&&activeGroup()==null)group=-1;
                if(selected>=0||search==null)render();else{if(status!=null)status.setOnClickListener(null);filter();}
            });
        });
    }
    private void detail(){
        Person p=person();if(p==null){TextView info=text(this,loaded?"Contact unavailable":"Loading contact…",16,0xff555555);body.addView(info);return;}
        ScrollView scroll=new ScrollView(this);LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);scroll.addView(card);body.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        LinearLayout banner=new LinearLayout(this);banner.setGravity(Gravity.CENTER_VERTICAL);banner.setPadding(dp(14),dp(16),dp(14),dp(16));banner.setBackgroundColor(0xffe2e2e2);
        ImageView portrait=new ImageView(this);portrait.setScaleType(ImageView.ScaleType.CENTER_CROP);portrait.setBackgroundResource(dev.sense.framework.R.drawable.sense_common_photo_frame);portrait.setPadding(dp(3),dp(3),dp(3),dp(3));photos.bind(portrait,p.photo(),()->{if(selected==p.id()&&!isDestroyed())render();});banner.addView(portrait,new LinearLayout.LayoutParams(dp(88),dp(88)));
        TextView name=text(this,p.name(),24,0xff202020);name.setTypeface(null,Typeface.BOLD);name.setPadding(dp(14),0,dp(8),0);banner.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        banner.addView(icon(this,p.favorite()?dev.sense.framework.R.drawable.sense_common_icon_favorites_on:dev.sense.framework.R.drawable.sense_common_icon_favorites_rest,p.favorite()?"Remove from favorites":"Add to favorites",()->favorite(p)),new LinearLayout.LayoutParams(dp(48),dp(48)));card.addView(banner);
        for(Field field:p.fields()){
            String kind=field.kind();int image=switch(kind){case "phone"->R.drawable.people_people_icon_call_mobile;case "email"->R.drawable.people_people_icon_mail;case "address"->R.drawable.people_sym_action_map;case "organization"->R.drawable.people_sym_action_organization;default->R.drawable.people_sym_note;};
            card.addView(section(this,switch(kind){case "phone"->"Phone · "+field.label();case "email"->"Email · "+field.label();default->field.label();}));
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(12),dp(12),dp(12));row.setMinimumHeight(dp(64));
            ImageView symbol=new ImageView(this);symbol.setImageResource(image);symbol.setColorFilter(new android.graphics.LightingColorFilter(0xff777777,0));symbol.setScaleType(ImageView.ScaleType.CENTER_INSIDE);row.addView(symbol,new LinearLayout.LayoutParams(dp(36),dp(36)));
            TextView value=text(this,field.value(),17,0xff222222);value.setPadding(dp(12),0,dp(8),0);row.addView(value,new LinearLayout.LayoutParams(0,-2,1));
            if(Set.of("phone","email","address","website").contains(kind)){StateListDrawable selector=new StateListDrawable();selector.addState(new int[]{android.R.attr.state_pressed},getDrawable(dev.sense.framework.R.drawable.sense_list_selector_background_pressed));selector.addState(new int[]{},new android.graphics.drawable.ColorDrawable(0x00000000));row.setBackground(selector);row.setOnClickListener(v->openField(field));row.setContentDescription((kind.equals("phone")?"Dial ":"Open ")+field.value());}
            if(kind.equals("phone")){
                ImageButton message=icon(this,R.drawable.people_people_icon_sms,"Message "+field.value(),()->launch(new Intent(Intent.ACTION_SENDTO,Uri.fromParts("smsto",field.value(),null))));
                message.setColorFilter(new android.graphics.LightingColorFilter(0xff777777,0));row.addView(message,new LinearLayout.LayoutParams(dp(48),dp(48)));
            }
            card.addView(row);
        }
        if(p.fields().isEmpty()){TextView empty=text(this,"No contact details yet. Use Edit to add phone numbers or email addresses.",16,0xff555555);empty.setPadding(dp(20),dp(24),dp(20),dp(24));card.addView(empty);}
    }
    private void openField(Field f){
        switch(f.kind()){
            case "phone"->{Intent phone=getPackageManager().getLaunchIntentForPackage("dev.sense.phone");Uri uri=Uri.fromParts("tel",f.value(),null);launch(phone!=null?phone.setData(uri):new Intent(Intent.ACTION_DIAL,uri));}
            case "email"->launch(new Intent(Intent.ACTION_SENDTO,Uri.fromParts("mailto",f.value(),null)));
            case "address"->launch(new Intent(Intent.ACTION_VIEW,Uri.parse("geo:0,0?q="+Uri.encode(f.value()))));
            case "website"->{String url=f.value();if(!url.contains(":"))url="https://"+url;Uri uri=Uri.parse(url);if("https".equalsIgnoreCase(uri.getScheme())||"http".equalsIgnoreCase(uri.getScheme()))launch(new Intent(Intent.ACTION_VIEW,uri));else Toast.makeText(this,"Unsupported website address",Toast.LENGTH_SHORT).show();}
            default->{ }
        }
    }
    private void favorite(Person p){
        if(checkSelfPermission(Manifest.permission.WRITE_CONTACTS)!=PackageManager.PERMISSION_GRANTED){
            if(getPreferences(0).getBoolean("requestedWrite",false)&&!shouldShowRequestPermissionRationale(Manifest.permission.WRITE_CONTACTS)){
                Toast.makeText(this,"Allow Contacts access in app permissions to change favorites",Toast.LENGTH_LONG).show();launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.fromParts("package",getPackageName(),null)));return;
            }
            pendingFavorite=p.id();pendingFavoriteValue=!p.favorite();getPreferences(0).edit().putBoolean("requestedWrite",true).apply();requestPermissions(new String[]{Manifest.permission.WRITE_CONTACTS},2);return;
        }writeFavorite(p.id(),!p.favorite());
    }
    private void writeFavorite(long id,boolean value){worker.execute(()->{try{ContentValues data=new ContentValues();data.put(Contacts.STARRED,value?1:0);int changed=getContentResolver().update(ContentUris.withAppendedId(Contacts.CONTENT_URI,id),data,null,null);if(changed==0)throw new IllegalStateException();main.post(this::load);}catch(RuntimeException e){main.post(()->Toast.makeText(this,"Couldn’t update favorites",Toast.LENGTH_SHORT).show());}});}
    private void addContact(){launch(new Intent(Intent.ACTION_INSERT).setType(ContactsContract.RawContacts.CONTENT_TYPE));}
    private void editContact(){Person p=person();if(p!=null)launch(new Intent(Intent.ACTION_EDIT).setDataAndType(p.uri(),Contacts.CONTENT_ITEM_TYPE));}
    private void launch(Intent intent){try{startActivity(intent);}catch(ActivityNotFoundException|SecurityException e){Toast.makeText(this,"No compatible app available",Toast.LENGTH_SHORT).show();}}
    private void hideKeyboard(){if(search!=null)getSystemService(InputMethodManager.class).hideSoftInputFromWindow(search.getWindowToken(),0);}
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] grants){super.onRequestPermissionsResult(code,permissions,grants);if(code==1){render();if(permitted())load();}if(code==2&&pendingFavorite>=0){long id=pendingFavorite;pendingFavorite=-1;if(checkSelfPermission(Manifest.permission.WRITE_CONTACTS)==PackageManager.PERMISSION_GRANTED)writeFavorite(id,pendingFavoriteValue);}}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("tab",tab);state.putString("query",query);state.putLong("selected",selected);state.putLong("group",group);state.putLong("pendingFavorite",pendingFavorite);state.putBoolean("pendingFavoriteValue",pendingFavoriteValue);if(pendingUri!=null)state.putString("pendingUri",pendingUri.toString());}
    @Override protected void onDestroy(){generation++;if(observing)getContentResolver().unregisterContentObserver(observer);main.removeCallbacksAndMessages(null);worker.shutdownNow();photos.close();super.onDestroy();}
    private final class PeopleAdapter extends BaseAdapter {
        public int getCount(){return rows.size();}public Object getItem(int position){return rows.get(position);}public long getItemId(int position){Object item=rows.get(position);return item instanceof Person p?p.id():item instanceof Group g?g.id():-position-1;}
        public int getViewTypeCount(){return 2;}public int getItemViewType(int position){return rows.get(position) instanceof String?0:1;}public boolean areAllItemsEnabled(){return false;}public boolean isEnabled(int position){return !(rows.get(position) instanceof String);}
        public View getView(int position,View recycled,ViewGroup parent){Object item=rows.get(position);if(item instanceof String letter)return section(ContactsActivity.this,letter);
            PeopleRow row=recycled instanceof PeopleRow r?r:new PeopleRow();
            if(item instanceof Person p){row.name.setText(p.name());row.subtitle.setText(p.subtitle());photos.bind(row.photo,p.photo(),this::notifyDataSetChanged);row.setContentDescription(p.name()+", "+p.subtitle());}
            else if(item instanceof Group g){row.name.setText(g.name());row.subtitle.setText(g.members().size()+" contacts");row.photo.setImageResource(R.drawable.people_people_icon_contact);row.setContentDescription(g.name()+", "+g.members().size()+" contacts");}return row;
        }
    }
    private final class PeopleRow extends LinearLayout {
        final ImageView photo;final TextView name,subtitle;
        PeopleRow(){super(ContactsActivity.this);setGravity(Gravity.CENTER_VERTICAL);setMinimumHeight(dp(76));setPadding(dp(12),dp(10),dp(12),dp(10));photo=new ImageView(ContactsActivity.this);photo.setScaleType(ImageView.ScaleType.CENTER_CROP);photo.setBackgroundResource(dev.sense.framework.R.drawable.sense_common_photo_frame);photo.setPadding(dp(2),dp(2),dp(2),dp(2));addView(photo,new LayoutParams(dp(50),dp(50)));
            LinearLayout labels=new LinearLayout(ContactsActivity.this);labels.setOrientation(VERTICAL);labels.setPadding(dp(12),0,0,0);name=text(ContactsActivity.this,"",18,0xff202020);name.setTypeface(null,Typeface.BOLD);subtitle=text(ContactsActivity.this,"",14,0xff666666);for(TextView v:new TextView[]{name,subtitle}){v.setSingleLine();v.setEllipsize(TextUtils.TruncateAt.END);labels.addView(v,new LayoutParams(-1,-2));}addView(labels,new LayoutParams(0,-2,1));}
    }
}
