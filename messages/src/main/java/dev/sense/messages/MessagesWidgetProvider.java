package dev.sense.messages;

import android.Manifest;
import android.app.PendingIntent;
import android.appwidget.*;
import android.content.*;
import android.database.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import android.text.*;
import android.text.style.StyleSpan;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.RemoteViews;
import java.util.*;
import java.util.concurrent.*;

/** Stock Messages artwork with native collections; does not require Rosie/Fusion at runtime. */
public class MessagesWidgetProvider extends AppWidgetProvider {
    private static final String REFRESH="dev.sense.messages.REFRESH_WIDGETS";
    private static final ExecutorService WORK=Executors.newSingleThreadExecutor();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static ContentObserver observer;
    private static Runnable changed;
    @Override public void onUpdate(Context context,AppWidgetManager manager,int[] ids){updateAsync(context);}
    @Override public void onReceive(Context context,Intent intent){if(REFRESH.equals(intent.getAction()))updateAsync(context);else super.onReceive(context,intent);}
    private void updateAsync(Context context){
        PendingResult result=goAsync();Context app=context.getApplicationContext();
        WORK.execute(()->{try{observe(app);updateAll(app);}finally{if(result!=null)result.finish();}});
    }
    static void refresh(Context context){context.sendBroadcast(new Intent(context,MessagesWidgetProvider.class).setAction(REFRESH));}
    static synchronized void observe(Context context){
        if(observer!=null||!MessageStore.permission(context,Manifest.permission.READ_SMS))return;
        Context app=context.getApplicationContext();
        changed=()->refresh(app);
        ContentObserver candidate=new ContentObserver(MAIN){@Override public void onChange(boolean self){MAIN.removeCallbacks(changed);MAIN.postDelayed(changed,250);}};
        try{
            app.getContentResolver().registerContentObserver(Telephony.Sms.CONTENT_URI,true,candidate);
            app.getContentResolver().registerContentObserver(Telephony.Mms.CONTENT_URI,true,candidate);
            app.getContentResolver().registerContentObserver(Telephony.MmsSms.CONTENT_URI,true,candidate);
            observer=candidate;
        }catch(SecurityException denied){app.getContentResolver().unregisterContentObserver(candidate);}
    }
    static int[] ids(Context context){
        AppWidgetManager manager=AppWidgetManager.getInstance(context);
        return java.util.stream.Stream.of(MessagesWidgetProvider.class,MessagesSummaryWidgetProvider.class,MessagesCardWidgetProvider.class)
                .flatMapToInt(type->Arrays.stream(manager.getAppWidgetIds(new ComponentName(context,type)))).toArray();
    }
    static void updateAll(Context context){
        int[] widgets=ids(context);if(widgets.length==0)return;
        java.util.List<MessageStore.ThreadRow> rows=java.util.List.of();String empty="No messages";
        if(MessageStore.permission(context,Manifest.permission.READ_SMS)){
            try{rows=MessageStore.threads(context,"");}catch(RuntimeException unavailable){empty="Messages unavailable · Tap to open";}
        }else empty="Open Messages to enable SMS access";
        AppWidgetManager manager=AppWidgetManager.getInstance(context);
        for(int id:widgets){
            AppWidgetProviderInfo info=manager.getAppWidgetInfo(id);if(info==null)continue;
            String name=info.provider.getClassName();String style=name.contains("Card")?"card":name.contains("Summary")?"summary":"list";
            try{manager.updateAppWidget(id,render(context,id,style,rows,empty));}
            catch(RuntimeException error){android.util.Log.w("SenseMessages","Cannot update Messages widget",error);}
        }
    }
    static RemoteViews render(Context context,int id,String style,java.util.List<MessageStore.ThreadRow> rows,String empty){
        boolean cards=style.equals("card"),summary=style.equals("summary");
        boolean emptyCards=cards&&rows.isEmpty();
        RemoteViews widget=new RemoteViews(context.getPackageName(),emptyCards?R.layout.messages_widget_cards_empty:cards?R.layout.messages_widget_cards:R.layout.messages_widget);
        widget.setTextViewText(R.id.widget_empty,empty);if(!emptyCards)widget.setEmptyView(R.id.widget_list,R.id.widget_empty);
        PendingIntent inbox=PendingIntent.getActivity(context,id,new Intent(context,MessagesActivity.class).setAction("dev.sense.messages.INBOX."+id),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent compose=PendingIntent.getActivity(context,id,new Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:")).setClass(context,MessagesActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        widget.setOnClickPendingIntent(R.id.widget_empty,inbox);widget.setOnClickPendingIntent(R.id.widget_inbox,inbox);widget.setOnClickPendingIntent(R.id.widget_compose,compose);
        if(!cards)widget.setOnClickPendingIntent(R.id.widget_header,inbox);
        int unread=rows.stream().mapToInt(MessageStore.ThreadRow::unread).sum();
        widget.setTextViewText(R.id.widget_unread,unread>99?"99+":Integer.toString(unread));widget.setViewVisibility(R.id.widget_unread,unread>0?View.VISIBLE:View.GONE);
        if(emptyCards)return widget;
        // Only this explicit Activity can receive collection fill-in extras.
        PendingIntent open=PendingIntent.getActivity(context,id,new Intent(context,MessagesActivity.class).setAction("dev.sense.messages.THREAD."+id),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_MUTABLE);
        widget.setPendingIntentTemplate(R.id.widget_list,open);
        RemoteViews.RemoteCollectionItems.Builder collection=new RemoteViews.RemoteCollectionItems.Builder().setHasStableIds(true).setViewTypeCount(1);
        int count=Math.min(rows.size(),cards?10:20);
        for(int i=0;i<count;i++){
            MessageStore.ThreadRow row=rows.get(i);
            RemoteViews item=new RemoteViews(context.getPackageName(),cards?R.layout.messages_widget_card:summary?R.layout.messages_widget_summary_row:R.layout.messages_widget_row);
            SpannableString name=new SpannableString(snippet(row.name(),80));if(row.unread()>0)name.setSpan(new StyleSpan(Typeface.BOLD),0,name.length(),0);
            item.setTextViewText(R.id.widget_name,name);
            item.setTextViewText(R.id.widget_time,DateUtils.getRelativeTimeSpanString(row.date(),System.currentTimeMillis(),DateUtils.MINUTE_IN_MILLIS));
            item.setTextViewText(R.id.widget_body,snippet(row.body(),cards?1200:summary?300:180));
            Bitmap photo=photo(context,row.address());if(photo!=null)item.setImageViewBitmap(R.id.widget_photo,photo);
            Intent thread=new Intent().putExtra("thread",row.id()).putExtra("address",row.address());item.setOnClickFillInIntent(R.id.widget_row,thread);
            if(cards){item.setTextViewText(R.id.widget_position,(i+1)+" / "+count);item.setOnClickFillInIntent(R.id.widget_reply,thread);}
            collection.addItem(row.id(),item);
        }
        widget.setRemoteAdapter(R.id.widget_list,collection.build());
        return widget;
    }
    private static String snippet(String text,int limit){
        int end=text.offsetByCodePoints(0,Math.min(text.codePointCount(0,text.length()),limit));
        return end==text.length()?text:text.substring(0,end)+"…";
    }
    private static Bitmap photo(Context context,String number){
        if(!MessageStore.permission(context,Manifest.permission.READ_CONTACTS)||number.isBlank())return null;
        try(Cursor cursor=context.getContentResolver().query(Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,Uri.encode(number)),new String[]{ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI},null,null,null)){
            if(cursor==null||!cursor.moveToFirst()||cursor.isNull(0))return null;
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.getContentResolver(),Uri.parse(cursor.getString(0))),(decoder,info,source)->{
                float scale=Math.min(1,96f/Math.max(info.getSize().getWidth(),info.getSize().getHeight()));
                decoder.setTargetSize(Math.max(1,Math.round(info.getSize().getWidth()*scale)),Math.max(1,Math.round(info.getSize().getHeight()*scale)));decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            });
        }catch(Exception unavailable){return null;}
    }
}
