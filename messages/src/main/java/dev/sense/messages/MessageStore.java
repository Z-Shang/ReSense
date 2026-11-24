package dev.sense.messages;

import android.Manifest;
import android.app.role.RoleManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.*;
import java.util.*;
import java.util.concurrent.*;

/** System SMS provider access. All queries and contact lookups run on the worker. */
final class MessageStore {
    static final ExecutorService WORK = Executors.newSingleThreadExecutor();
    static final ExecutorService EVENTS = Executors.newSingleThreadExecutor();
    record Message(long id,long thread,String address,String body,long date,int type,boolean read,int status,int error,boolean mms,boolean download) {}
    record ThreadRow(long id,String address,String name,String body,long date,int count,int unread,boolean failed) {}
    static boolean permission(Context c,String permission){return c.checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED;}
    static boolean isDefault(Context c){RoleManager role=c.getSystemService(RoleManager.class);return role!=null&&role.isRoleHeld(RoleManager.ROLE_SMS);}
    static String value(Cursor c,int index){String s=c.getString(index);return s==null?"":s;}
    static final String[] COLUMNS={"_id","thread_id","address","body","date","type","read","status","error_code"};
    static Message row(Cursor c){return new Message(c.getLong(0),c.getLong(1),value(c,2),value(c,3),c.getLong(4),c.getInt(5),c.getInt(6)!=0,c.getInt(7),c.getInt(8),false,false);}
    static List<ThreadRow> threads(Context context,String filter){return threads(context,filter,()->false);}
    static List<ThreadRow> threads(Context context,String filter,java.util.function.BooleanSupplier cancelled){
        LinkedHashMap<Long,Message> latest=new LinkedHashMap<>();Map<Long,int[]> totals=new HashMap<>();
        try(Cursor c=context.getContentResolver().query(Telephony.Sms.CONTENT_URI,COLUMNS,"type != ?",new String[]{"3"},"date DESC, _id DESC")){
            if(c!=null)while(c.moveToNext()){
                if(cancelled.getAsBoolean())return List.of();Message m=row(c);latest.putIfAbsent(m.thread,m);int[] counts=totals.computeIfAbsent(m.thread,k->new int[2]);counts[0]++;if(!m.read&&m.type==1)counts[1]++;
            }
        }
        for(Message m:MmsStore.messages(context,-1)){if(cancelled.getAsBoolean())return List.of();Message old=latest.get(m.thread());if(old==null||m.date()>old.date())latest.put(m.thread(),m);int[] counts=totals.computeIfAbsent(m.thread(),k->new int[2]);counts[0]++;if(!m.read()&&m.type()==1)counts[1]++;}
        List<ThreadRow> result=new ArrayList<>();Map<String,String> names=new HashMap<>();String query=filter.toLowerCase(Locale.ROOT);
        for(Message m:latest.values()){
            if(cancelled.getAsBoolean())return List.of();
            String name=names.computeIfAbsent(m.address,a->contactName(context,a));
            if(!query.isBlank()&&!(name+" "+m.address+" "+m.body).toLowerCase(Locale.ROOT).contains(query))continue;
            int[] counts=totals.get(m.thread);result.add(new ThreadRow(m.thread,m.address,name,m.body,m.date,counts[0],counts[1],m.type==5));
        }
        result.sort(java.util.Comparator.comparingLong(ThreadRow::date).reversed());return result;
    }
    static List<Message> conversation(Context c,long thread,String address,int limit){
        List<Message> rows=new ArrayList<>();String where=thread>0?"thread_id = ?":"address = ?";String arg=thread>0?Long.toString(thread):SendCoordinator.key(address);
        try(Cursor cursor=c.getContentResolver().query(Telephony.Sms.CONTENT_URI,COLUMNS,where+" AND type != ?",new String[]{arg,"3"},"date DESC, _id DESC")){
            if(cursor!=null)while(cursor.moveToNext()&&rows.size()<limit)rows.add(row(cursor));
        }
        long mmsThread=thread;
        for(var m:MmsStore.messages(c,mmsThread))if(thread>0||android.telephony.PhoneNumberUtils.compare(m.address(),address))rows.add(m);
        rows.sort(java.util.Comparator.comparingLong(Message::date).thenComparingLong(Message::id));if(rows.size()>limit)return new ArrayList<>(rows.subList(rows.size()-limit,rows.size()));return rows;
    }
    static String contactName(Context c,String address){
        if(permission(c,Manifest.permission.READ_CONTACTS)&&!address.isEmpty())try(Cursor rows=c.getContentResolver().query(Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,Uri.encode(address)),new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME},null,null,null)){
            if(rows!=null&&rows.moveToFirst())return value(rows,0);
        }catch(SecurityException ignored){}
        return address.isEmpty()?"Unknown sender":address;
    }
    static void markRead(Context c,long thread){
        if(!isDefault(c)||thread<=0)return;
        ContentValues values=new ContentValues();values.put("read",1);values.put("seen",1);
        c.getContentResolver().update(Telephony.Sms.CONTENT_URI,values,"thread_id = ? AND read = 0",new String[]{Long.toString(thread)});
        c.getContentResolver().update(Telephony.Mms.CONTENT_URI,values,"thread_id = ? AND read = 0",new String[]{Long.toString(thread)});
        c.getSystemService(android.app.NotificationManager.class).cancel("thread:"+thread,1);
    }
    static Uri incoming(Context c,String address,String body,long sent,int subscription){
        if(!isDefault(c))return null;
        ContentValues values=new ContentValues();values.put("address",address);values.put("body",body);values.put("date",System.currentTimeMillis());values.put("date_sent",sent);values.put("read",0);values.put("seen",0);values.put("type",1);values.put("sub_id",subscription);
        values.put("thread_id",Telephony.Threads.getOrCreateThreadId(c,address));
        return c.getContentResolver().insert(Telephony.Sms.Inbox.CONTENT_URI,values);
    }
    static void update(Context c,long id,int type,int status){
        if(!isDefault(c))return;ContentValues v=new ContentValues();v.put("type",type);v.put("status",status);
        c.getContentResolver().update(ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI,id),v,null,null);
    }
}
