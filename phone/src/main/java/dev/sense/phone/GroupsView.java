package dev.sense.phone;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.ContactsContract.*;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import dev.sense.framework.SenseStyle;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Stock Groups badge + "name (count)" rows backed by real system membership. */
final class GroupsView extends LinearLayout {
    record Group(long id,String title,int members) {String label(){return title+" ("+members+")";}}
    private final Activity activity;
    private final Consumer<Group> selected;
    private final java.util.concurrent.ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ArrayList<Group> groups=new ArrayList<>();
    private final GroupAdapter adapter=new GroupAdapter();
    private ListView list;private TextView status;
    private boolean closed,observing;private int generation;
    private final Runnable refresh=this::load;
    private final ContentObserver observer=new ContentObserver(main){@Override public void onChange(boolean self){main.removeCallbacks(refresh);main.postDelayed(refresh,180);}};
    GroupsView(Activity activity,Runnable permission,Consumer<Group> selected){
        super(activity);this.activity=activity;this.selected=selected;setOrientation(VERTICAL);setBackgroundColor(0xfffafafa);
        LinearLayout header=new LinearLayout(activity);header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xff646964,0xff353b36,0xff242a25}));
        TextView title=SenseStyle.text(activity,"Groups",22,Color.WHITE);title.setPadding(dp(14),0,dp(14),0);title.setAccessibilityHeading(true);header.addView(title,new LayoutParams(-1,-2));addView(header,new LayoutParams(-1,dp(56)));
        status=SenseStyle.text(activity,"Loading groups…",16,0xff666666);status.setGravity(Gravity.CENTER);status.setPadding(dp(20),dp(24),dp(20),dp(24));addView(status);
        if(activity.checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED){
            status.setText("Allow contacts access to show your groups and their members.");
            Button allow=new dev.sense.framework.SensePreferences.ButtonControl(activity);allow.setText("Allow access to contacts");allow.setOnClickListener(v->{
                if(activity.getPreferences(0).getBoolean("contactsPermissionRequested",false)&&!activity.shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS))activity.startActivity(new android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.fromParts("package",activity.getPackageName(),null)));
                else{activity.getPreferences(0).edit().putBoolean("contactsPermissionRequested",true).apply();permission.run();}
            });addView(allow,new LayoutParams(-1,dp(52)));return;
        }
        list=new ListView(activity);SenseStyle.list(list);list.setAdapter(adapter);
        list.setOnItemClickListener((parent,view,position,id)->selected.accept(groups.get(position)));addView(list,new LayoutParams(-1,0,1));
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    @Override protected void onAttachedToWindow(){
        super.onAttachedToWindow();if(list==null)return;
        try{activity.getContentResolver().registerContentObserver(Groups.CONTENT_URI,true,observer);observing=true;activity.getContentResolver().registerContentObserver(Data.CONTENT_URI,true,observer);}catch(SecurityException ignored){}
        load();
    }
    private void load(){
        if(closed||list==null)return;int token=++generation;
        worker.execute(()->{ArrayList<Group> result=new ArrayList<>();String error=null;
            try{
                Map<Long,Set<Long>> members=new HashMap<>();
                try(var cursor=activity.getContentResolver().query(Data.CONTENT_URI,new String[]{Data.DATA1,Data.CONTACT_ID},Data.MIMETYPE+"=?",new String[]{CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE},null)){
                    if(cursor!=null)while(cursor.moveToNext())members.computeIfAbsent(cursor.getLong(0),key->new HashSet<>()).add(cursor.getLong(1));
                }
                try(var cursor=activity.getContentResolver().query(Groups.CONTENT_URI,new String[]{Groups._ID,Groups.TITLE},Groups.DELETED+"=0",null,Groups.TITLE+" COLLATE LOCALIZED ASC")){
                    if(cursor==null)throw new IllegalStateException();while(cursor.moveToNext()){String title=cursor.getString(1);result.add(new Group(cursor.getLong(0),title==null||title.isBlank()?"Unnamed group":title,members.getOrDefault(cursor.getLong(0),Set.of()).size()));}
                }
            }catch(RuntimeException failure){error="Couldn’t load contact groups. Tap to retry.";}
            String failure=error;main.post(()->{
                if(closed||token!=generation)return;int first=list.getFirstVisiblePosition();View item=list.getChildAt(0);int top=item==null?0:item.getTop();groups.clear();groups.addAll(result);adapter.notifyDataSetChanged();list.setSelectionFromTop(first,top);
                status.setText(failure!=null?failure:"No contact groups");status.setVisibility(groups.isEmpty()?VISIBLE:GONE);status.setOnClickListener(failure==null?null:v->load());
            });
        });
    }
    private final class GroupAdapter extends BaseAdapter {
        public int getCount(){return groups.size();}public Group getItem(int position){return groups.get(position);}public long getItemId(int position){return getItem(position).id();}public boolean hasStableIds(){return true;}
        public View getView(int position,View recycled,ViewGroup parent){GroupRow row=recycled instanceof GroupRow r?r:new GroupRow();Group group=getItem(position);row.name.setText(group.label());row.setContentDescription(group.label());return row;}
    }
    private final class GroupRow extends LinearLayout {
        final TextView name;
        GroupRow(){
            super(activity);setGravity(Gravity.CENTER_VERTICAL);setMinimumHeight(dp(73));setPadding(dp(12),dp(8),dp(12),dp(8));
            android.graphics.drawable.StateListDrawable background=new android.graphics.drawable.StateListDrawable();background.addState(new int[]{android.R.attr.state_pressed},activity.getDrawable(dev.sense.framework.R.drawable.sense_list_selector_background_pressed));background.addState(new int[]{},new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));setBackground(background);
            FrameLayout badge=new FrameLayout(activity);badge.setBackgroundResource(dev.sense.framework.R.drawable.sense_common_photo_frame);ImageView image=new ImageView(activity);image.setImageResource(dev.sense.framework.R.drawable.sense_common_icon_default_group);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);FrameLayout.LayoutParams picture=new FrameLayout.LayoutParams(-1,-1);picture.setMargins(dp(3),dp(3),dp(3),dp(3));badge.addView(image,picture);addView(badge,new LayoutParams(dp(48),dp(48)));
            name=SenseStyle.text(activity,"",18,0xff222222);name.setSingleLine();name.setEllipsize(TextUtils.TruncateAt.END);name.setPadding(dp(12),0,0,0);addView(name,new LayoutParams(0,-2,1));
        }
    }
    @Override protected void onDetachedFromWindow(){closed=true;generation++;if(observing)activity.getContentResolver().unregisterContentObserver(observer);main.removeCallbacksAndMessages(null);worker.shutdownNow();super.onDetachedFromWindow();}
}
