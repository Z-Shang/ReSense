package dev.sense.messages;

import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import java.util.*;

/** Durable callback identities; carrier failures remain explicit and never automatically resend. */
final class MmsTransfers extends SQLiteOpenHelper {
    record Job(String token,boolean download,String row,String number,String location,int subscription,int state,long date){}
    MmsTransfers(Context c){super(c,"mms-transfers.db",null,2);}
    @Override public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE transfers(token TEXT PRIMARY KEY,download INTEGER,row_uri TEXT,number TEXT,location TEXT,subscription INTEGER,state INTEGER,date INTEGER,result INTEGER,response TEXT)");db.execSQL("CREATE UNIQUE INDEX notifications ON transfers(location,subscription) WHERE download=1");}
    @Override public void onUpgrade(SQLiteDatabase db,int old,int next){if(old==1&&next==2){db.execSQL("ALTER TABLE transfers ADD COLUMN result INTEGER");db.execSQL("ALTER TABLE transfers ADD COLUMN response TEXT");}else throw new IllegalStateException("Unknown MMS schema");}
    void add(Job j){ContentValues v=new ContentValues();v.put("token",j.token());v.put("download",j.download()?1:0);v.put("row_uri",j.row());v.put("number",j.number());v.put("location",j.location());v.put("subscription",j.subscription());v.put("state",j.state());v.put("date",j.date());getWritableDatabase().insertOrThrow("transfers",null,v);}
    Job get(String token){try(Cursor c=getReadableDatabase().query("transfers",null,"token=?",new String[]{token},null,null,null)){return c.moveToFirst()?row(c):null;}}
    Job notification(String location,int subscription){try(Cursor c=getReadableDatabase().query("transfers",null,"download=1 AND location=? AND subscription=?",new String[]{location,Integer.toString(subscription)},null,null,null)){return c.moveToFirst()?row(c):null;}}
    Job forRow(String uri){try(Cursor c=getReadableDatabase().query("transfers",null,"row_uri=?",new String[]{uri},null,null,"date DESC")){return c.moveToFirst()?row(c):null;}}
    private Job row(Cursor c){return new Job(c.getString(0),c.getInt(1)!=0,c.getString(2),c.getString(3),c.getString(4),c.getInt(5),c.getInt(6),c.getLong(7));}
    void state(String token,int state){ContentValues values=new ContentValues();values.put("state",state);getWritableDatabase().update("transfers",values,"token=?",new String[]{token});}
    void result(String token,int result,byte[] response){ContentValues values=new ContentValues();values.put("result",result);if(response!=null&&response.length<=1024*1024)values.put("response",android.util.Base64.encodeToString(response,android.util.Base64.NO_WRAP));getWritableDatabase().update("transfers",values,"token=?",new String[]{token});}
    Job renew(Job old){String token=UUID.randomUUID().toString();ContentValues values=new ContentValues();values.put("token",token);values.put("state",4);values.putNull("result");values.putNull("response");values.put("date",System.currentTimeMillis());getWritableDatabase().update("transfers",values,"token=?",new String[]{old.token()});return get(token);}
    void reconcile(Context c){
        if(MessageStore.isDefault(c)){
            record Saved(String token,int result,byte[] response){}
            java.util.ArrayList<Saved> replay=new java.util.ArrayList<>();
            try(Cursor cursor=getReadableDatabase().query("transfers",new String[]{"token","result","response"},"result IS NOT NULL AND state IN (0,3)",null,null,null,null)){while(cursor.moveToNext())replay.add(new Saved(cursor.getString(0),cursor.getInt(1),cursor.isNull(2)?null:android.util.Base64.decode(cursor.getString(2),android.util.Base64.DEFAULT)));}
            for(Saved saved:replay)MmsResultReceiver.complete(c,saved.token(),saved.result(),saved.response());
        }


        java.util.ArrayList<Job> stale=new java.util.ArrayList<>();try(Cursor cursor=getReadableDatabase().query("transfers",null,"state=0 AND date < ?",new String[]{Long.toString(System.currentTimeMillis()-20*60*1000)},null,null,null)){while(cursor.moveToNext())stale.add(row(cursor));}
        for(Job job:stale){state(job.token(),3);if(!job.download()&&!job.row().isEmpty())MmsStore.state(c,job,false);}
    }

}
