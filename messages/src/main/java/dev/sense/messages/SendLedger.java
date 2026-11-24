package dev.sense.messages;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;

/** Durable per-part results: duplicate/out-of-order callbacks cannot turn failure into success. */
final class SendLedger extends SQLiteOpenHelper {
    private final Context context;
    SendLedger(Context c){super(c,"sms-results.db",null,2);context=c.getApplicationContext();}
    @Override public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE parts(token TEXT, message_id INTEGER, part INTEGER, sent INTEGER DEFAULT 0, delivered INTEGER DEFAULT 0, identity TEXT, PRIMARY KEY(token,part))");}
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){if(oldVersion<2)db.execSQL("ALTER TABLE parts ADD COLUMN identity TEXT");}
    void register(String token,long id,int parts){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{for(int i=0;i<parts;i++){ContentValues v=new ContentValues();v.put("token",token);v.put("message_id",id);v.put("part",i);v.put("identity",fingerprint(context,id));db.insertOrThrow("parts",null,v);}db.setTransactionSuccessful();}finally{db.endTransaction();}}
    void reconcile(Context context){
        if(!MessageStore.isDefault(context)||!MessageStore.permission(context,android.Manifest.permission.READ_SMS))return;
        SQLiteDatabase db=getReadableDatabase();
        try(Cursor c=db.rawQuery("SELECT token, message_id FROM parts GROUP BY token",null)){
            while(c.moveToNext()){
                try(Cursor state=context.getContentResolver().query(android.content.ContentUris.withAppendedId(android.provider.Telephony.Sms.CONTENT_URI,c.getLong(1)),new String[]{"type"},null,null,null)){
                    if(state==null||!state.moveToFirst()||state.getInt(0)==3)continue;
                }
                Result result=aggregate(db,c.getString(0));if(result!=null&&matches(context,result)&&result.type()!=4)MessageStore.update(context,result.id(),result.type(),result.status());
            }
        }
        // An interrupted or unacknowledged submission is uncertain: never retransmit automatically.
        android.content.ContentValues values=new android.content.ContentValues();values.put("type",5);values.put("status",64);values.put("error_code",10001);
        context.getContentResolver().update(android.provider.Telephony.Sms.CONTENT_URI,values,"creator = ? AND type IN (4,6) AND date < ?",new String[]{context.getPackageName(),Long.toString(System.currentTimeMillis()-120000)});
    }
    private Result aggregate(SQLiteDatabase db,String token){
        try(Cursor c=db.rawQuery("SELECT message_id, MIN(sent), MIN(delivered), MIN(identity) FROM parts WHERE token = ? GROUP BY message_id",new String[]{token})){
            if(!c.moveToFirst())return null;long id=c.getLong(0);int sent=c.getInt(1),delivered=c.getInt(2);
            return new Result(id,sent<0?5:sent==0?4:2,sent<0||delivered<0?64:delivered==1?0:32,c.getString(3));
        }
    }
    record Result(long id,int type,int status,String identity){}
    static boolean matches(Context c,Result result){return result.identity()!=null&&result.identity().equals(fingerprint(c,result.id()));}
    private static String fingerprint(Context c,long id){
        try(Cursor cursor=c.getContentResolver().query(android.content.ContentUris.withAppendedId(android.provider.Telephony.Sms.CONTENT_URI,id),new String[]{"date","address","body"},null,null,null)){
            if(cursor==null||!cursor.moveToFirst())return null;
            String identity=cursor.getLong(0)+"\n"+cursor.getString(1)+"\n"+cursor.getString(2);
            try{return android.util.Base64.encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8)),android.util.Base64.NO_WRAP);}catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
        }
    }
    Result record(String token,int part,boolean delivery,boolean success){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            String column=delivery?"delivered":"sent";
            // Failure is terminal for a part, including duplicate callbacks.
            db.execSQL("UPDATE parts SET "+column+" = CASE WHEN "+column+" = -1 THEN -1 ELSE ? END WHERE token = ? AND part = ?",new Object[]{success?1:-1,token,part});
            try(Cursor c=db.rawQuery("SELECT message_id, MIN(sent), MIN(delivered), MIN(identity) FROM parts WHERE token = ? GROUP BY message_id",new String[]{token})){
                if(!c.moveToFirst())return null;long id=c.getLong(0);int sent=c.getInt(1),delivered=c.getInt(2);
                db.setTransactionSuccessful();return new Result(id,sent<0?5:sent==0?4:2,sent<0||delivered<0?64:delivered==1?0:32,c.getString(3));
            }
        }finally{db.endTransaction();}
    }
}
