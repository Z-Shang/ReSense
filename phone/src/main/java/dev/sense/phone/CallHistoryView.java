package dev.sense.phone;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Read-only system call history. All provider and contact lookups run off the UI thread. */
final class CallHistoryView extends LinearLayout {
    record Entry(long id, String number, String name, int type, long date, long duration, int presentation) {
        boolean callable() { return presentation == Calls.PRESENTATION_ALLOWED && number != null && !number.isBlank(); }
        String title() {
            if (presentation == Calls.PRESENTATION_RESTRICTED) return "Private number";
            if (presentation == Calls.PRESENTATION_PAYPHONE) return "Payphone";
            if (!callable()) return "Unknown number";
            return name != null && !name.isBlank() ? name : number;
        }
        String direction() {
            return switch(type) {
                case Calls.INCOMING_TYPE -> "Incoming";
                case Calls.OUTGOING_TYPE -> "Outgoing";
                case Calls.MISSED_TYPE -> "Missed call";
                case Calls.REJECTED_TYPE -> "Declined";
                case Calls.BLOCKED_TYPE -> "Blocked";
                case Calls.VOICEMAIL_TYPE -> "Voicemail";
                case Calls.ANSWERED_EXTERNALLY_TYPE -> "Answered elsewhere";
                default -> "Call";
            };
        }
        String detail() {
            String durationText = duration >= 3600 ? String.format(java.util.Locale.getDefault(), "%d:%02d:%02d", duration/3600, duration/60%60, duration%60)
                    : String.format(java.util.Locale.getDefault(), "%d:%02d", duration/60, duration%60);
            return direction() + (duration > 0 ? " · " + durationText : "");
        }
    }
    private final Activity activity;
    private final Runnable requestPermission;
    private final Consumer<String> selectNumber;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ArrayList<Entry> entries = new ArrayList<>();
    private final ArrayList<Object> rows = new ArrayList<>();
    private final HistoryAdapter adapter = new HistoryAdapter();
    private ListView list;
    private AlertDialog viewDialog;
    private TextView status, more;
    private boolean loading, observing, closed;
    private int callType;
    private static final String[] VIEW_LABELS={"All calls","Incoming calls","Outgoing calls","Missed calls"};
    private int generation;
    private final Runnable refresh = () -> load(true);
    private final ContentObserver observer = new ContentObserver(handler) {
        @Override public void onChange(boolean selfChange) {
            handler.removeCallbacks(refresh); handler.postDelayed(refresh, 200);
        }
    };

    CallHistoryView(Activity activity, Runnable requestPermission, Consumer<String> selectNumber) {
        super(activity); this.activity=activity;this.requestPermission=requestPermission;this.selectNumber=selectNumber;
        setOrientation(VERTICAL);setBackgroundColor(0xfffafafa);callType=activity.getPreferences(0).getInt("historyViewType",0);if(callType<0||callType>=VIEW_LABELS.length)callType=0;
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(activity);view.setText(value);view.setTextSize(size);view.setTextColor(color);return view;
    }
    private boolean permitted() { return activity.checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED; }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if(!permitted()) { permissionView(); return; }
        buildList();
        try {
            activity.getContentResolver().registerContentObserver(Calls.CONTENT_URI, true, observer);observing=true;
            activity.getContentResolver().registerContentObserver(ContactsContract.Contacts.CONTENT_URI,true,observer);
        } catch(SecurityException ignored) { /* Reading contacts is optional. */ }
        load(true);
    }
    @Override protected void onDetachedFromWindow() {
        closed=true;generation++;if(viewDialog!=null)viewDialog.dismiss();handler.removeCallbacksAndMessages(null);
        if(observing)activity.getContentResolver().unregisterContentObserver(observer);
        worker.shutdownNow();super.onDetachedFromWindow();
    }
    private void permissionView() {
        removeAllViews();addHeader();
        TextView heading=text("Call log access",22,0xff222222);heading.setPadding(dp(20),dp(24),dp(20),dp(8));addView(heading);
        TextView explanation=text("Allow call log access to show incoming, outgoing and missed calls from your phone.",16,0xff555555);
        explanation.setPadding(dp(20),dp(8),dp(20),dp(16));addView(explanation);
        boolean requested=activity.getPreferences(0).getBoolean("historyPermissionRequested",false);
        boolean blocked=requested && !activity.shouldShowRequestPermissionRationale(Manifest.permission.READ_CALL_LOG);
        TextView allow=action(blocked ? "Open app permissions" : "Allow call log access");
        allow.setOnClickListener(v->{
            if(blocked)activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.fromParts("package",activity.getPackageName(),null)));
            else {activity.getPreferences(0).edit().putBoolean("historyPermissionRequested",true).apply();requestPermission.run();}
        });addView(allow,new LayoutParams(-1,dp(52)));
        if(blocked){TextView restricted=text("If Call logs is unavailable in app permissions, your installer or device has restricted access.",14,0xff555555);restricted.setPadding(dp(20),dp(16),dp(20),dp(16));addView(restricted);}
    }
    private TextView action(String title) {
        return dev.sense.framework.SenseStyle.action(activity,title,()->{});
    }
    private void buildList() {
        addHeader();
        status=text("Loading call history…",14,0xff555555);status.setGravity(Gravity.CENTER);status.setPadding(dp(12),dp(12),dp(12),dp(12));addView(status);
        list=new ListView(activity);dev.sense.framework.SenseStyle.list(list);
        more=action("Load older calls");more.setOnClickListener(v->load(false));list.addFooterView(more,null,false);
        list.setAdapter(adapter);list.setOnItemClickListener((parent,view,position,id)->{
            if(position>=rows.size() || !(rows.get(position) instanceof Entry entry))return;
            if(entry.callable()) selectNumber.accept(entry.number());
            else Toast.makeText(activity,"This caller’s number is unavailable",Toast.LENGTH_SHORT).show();
        });
        addView(list,new LayoutParams(-1,0,1));
    }
    private void addHeader(){
        LinearLayout header=new LinearLayout(activity);header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xff646964,0xff353b36,0xff242a25}));
        TextView title=text("Call History",22,Color.WHITE);title.setPadding(dp(14),0,dp(8),0);header.addView(title,new LayoutParams(0,-2,1));
        ImageButton options=dev.sense.framework.SenseHeaderIconButton.overflow(activity,"Call history options");
        options.setOnClickListener(anchor->{PopupMenu menu=new PopupMenu(activity,anchor,Gravity.END);menu.getMenu().add("View").setOnMenuItemClickListener(item->{showViewDialog();return true;});menu.show();});
        header.addView(options,new LayoutParams(dp(48),dp(48)));addView(header,new LayoutParams(-1,dp(56)));
    }
    private void showViewDialog(){
        ArrayAdapter<String> choices=new ArrayAdapter<>(activity,android.R.layout.simple_list_item_single_choice,VIEW_LABELS){
            @Override public View getView(int position,View recycled,ViewGroup parent){
                dev.sense.framework.SensePreferences.Choice row=recycled instanceof dev.sense.framework.SensePreferences.Choice c?c:new dev.sense.framework.SensePreferences.Choice(activity);
                row.setText(getItem(position));row.setChecked(position==callType);row.setFocusable(false);row.setClickable(false);return row;
            }
        };
        viewDialog=new AlertDialog.Builder(activity).setTitle("View").setSingleChoiceItems(choices,callType,(dialog,which)->{
            if(closed){dialog.dismiss();return;}
            callType=which;activity.getPreferences(0).edit().putInt("historyViewType",which).apply();dialog.dismiss();load(true);
        }).show();
    }
    private void load(boolean reset) {
        if(closed)return;
        if(!permitted()){generation++;permissionView();return;}
        if(loading && !reset)return;
        int token=++generation;
        int selectedType=callType;
        int offset=reset?0:entries.size();
        loading=true;more.setEnabled(false);
        if(reset){entries.clear();rows.clear();adapter.notifyDataSetChanged();status.setText("Loading call history…");status.setVisibility(VISIBLE);}
        worker.execute(()->{
            ArrayList<Entry> page=new ArrayList<>();String error=null;
            try {
                Uri uri=Calls.CONTENT_URI.buildUpon().appendQueryParameter(Calls.LIMIT_PARAM_KEY,"100").appendQueryParameter(Calls.OFFSET_PARAM_KEY,Integer.toString(offset)).build();
                String[] projection={Calls._ID,Calls.NUMBER,Calls.CACHED_NAME,Calls.TYPE,Calls.DATE,Calls.DURATION,Calls.NUMBER_PRESENTATION};
                try(Cursor cursor=activity.getContentResolver().query(uri,projection,selectedType==0?null:Calls.TYPE+"=?",selectedType==0?null:new String[]{Integer.toString(selectedType)},Calls.DATE+" DESC, "+Calls._ID+" DESC")){
                    if(cursor==null)throw new IllegalStateException("Call log provider unavailable");
                    HashMap<String,String> names=new HashMap<>();
                    while(cursor.moveToNext()){
                        String number=cursor.getString(1),name=cursor.getString(2);int presentation=cursor.getInt(6);
                        if(presentation==Calls.PRESENTATION_ALLOWED && number!=null && !number.isBlank() && activity.checkSelfPermission(Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED){
                            if(!names.containsKey(number)) names.put(number,lookup(number,name));name=names.get(number);
                        }
                        page.add(new Entry(cursor.getLong(0),number,name,cursor.getInt(3),cursor.getLong(4),Math.max(0,cursor.getLong(5)),presentation));
                    }
                }
            }catch(SecurityException e){error="Call log access was denied. Check Call logs in app permissions.";}
            catch(RuntimeException e){error="Couldn’t load call history. Tap to retry.";}
            String failure=error;
            handler.post(()->{
                if(closed || token!=generation)return;
                loading=false;more.setEnabled(true);
                if(failure!=null){status.setText(failure);status.setVisibility(VISIBLE);status.setOnClickListener(v->{if(permitted())load(true);else permissionView();});return;}
                status.setOnClickListener(null);entries.addAll(page);rebuildRows();adapter.notifyDataSetChanged();
                status.setText(callType==0?"No calls yet":"No "+VIEW_LABELS[callType].toLowerCase(java.util.Locale.ROOT));status.setVisibility(entries.isEmpty()?VISIBLE:GONE);
                more.setVisibility(page.size()==100?VISIBLE:GONE);
                if(reset)list.setSelection(0);
            });
        });
    }
    private String lookup(String number,String cached) {
        try(Cursor c=activity.getContentResolver().query(Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,Uri.encode(number)),new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME},null,null,null)){
            if(c!=null && c.moveToFirst())return c.getString(0);
        }catch(SecurityException ignored){}
        return cached;
    }
    private LocalDate day(long millis) {return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate();}
    private void rebuildRows() {
        rows.clear();LocalDate previous=null,today=LocalDate.now();
        for(Entry entry:entries){
            LocalDate current=day(entry.date());
            if(!current.equals(previous)){
                rows.add(current.equals(today)?"Today":current.equals(today.minusDays(1))?"Yesterday":DateUtils.formatDateTime(activity,entry.date(),DateUtils.FORMAT_SHOW_DATE|DateUtils.FORMAT_SHOW_YEAR|DateUtils.FORMAT_ABBREV_MONTH));previous=current;
            }rows.add(entry);
        }
    }
    private final class HistoryAdapter extends BaseAdapter {
        public int getCount(){return rows.size();}public Object getItem(int position){return rows.get(position);}
        public long getItemId(int position){return rows.get(position) instanceof Entry entry?entry.id():-position-1;}
        public int getViewTypeCount(){return 2;}public int getItemViewType(int position){return rows.get(position) instanceof Entry?1:0;}
        public boolean areAllItemsEnabled(){return false;}public boolean isEnabled(int position){return rows.get(position) instanceof Entry;}
        public View getView(int position,View recycled,ViewGroup parent){
            if(rows.get(position) instanceof String day){
                TextView header=recycled instanceof TextView t?t:text("",13,Color.WHITE);
                header.setText(day);header.setTypeface(null,Typeface.BOLD);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(14),dp(5),dp(14),dp(5));
                header.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xff676767,0xff414141}));return header;
            }
            Entry entry=(Entry)rows.get(position);
            HistoryRow row=recycled instanceof HistoryRow r?r:new HistoryRow();row.bind(entry);return row;
        }
    }
    private final class HistoryRow extends LinearLayout {
        final ImageView icon;final TextView name,number,detail,time;
        HistoryRow(){
            super(activity);setOrientation(HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);setMinimumHeight(dp(84));setPadding(dp(12),dp(10),dp(12),dp(10));
            icon=new ImageView(activity);icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);addView(icon,new LayoutParams(dp(32),dp(36)));
            LinearLayout labels=new LinearLayout(activity);labels.setOrientation(VERTICAL);labels.setPadding(dp(10),0,dp(8),0);
            name=text("",18,0xff202020);name.setTypeface(null,Typeface.BOLD);number=text("",14,0xff555555);detail=text("",13,0xff666666);
            for(TextView label:new TextView[]{name,number,detail}){label.setSingleLine(true);label.setEllipsize(TextUtils.TruncateAt.END);labels.addView(label,new LayoutParams(-1,-2));}
            addView(labels,new LayoutParams(0,-2,1));time=text("",12,0xff666666);time.setGravity(Gravity.END);addView(time,new LayoutParams(-2,-2));
        }
        void bind(Entry entry){
            boolean missed=entry.type()==Calls.MISSED_TYPE;
            icon.setImageResource(missed?dev.sense.framework.R.drawable.sense_common_icon_callhistory_misscall:entry.type()==Calls.OUTGOING_TYPE?dev.sense.framework.R.drawable.sense_common_icon_callhistory_outgoing:dev.sense.framework.R.drawable.sense_common_icon_callhistory_incoming);
            name.setText(entry.title());name.setTextColor(missed?0xffb42117:0xff202020);
            boolean showNumber=entry.callable()&&!entry.title().equals(entry.number());number.setText(showNumber?entry.number():"");number.setVisibility(showNumber?VISIBLE:GONE);
            detail.setText(entry.detail());time.setText(DateFormat.getTimeFormat(activity).format(new java.util.Date(entry.date())));
            setContentDescription(entry.title()+", "+entry.detail()+", "+DateUtils.formatDateTime(activity,entry.date(),DateUtils.FORMAT_SHOW_DATE|DateUtils.FORMAT_SHOW_TIME));
        }
    }
}
