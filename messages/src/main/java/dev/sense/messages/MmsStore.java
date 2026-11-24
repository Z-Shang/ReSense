package dev.sense.messages;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import dev.sense.messages.mms.pdu.*;

/** MMS rows and parts share Android thread IDs with SMS. */
final class MmsStore {
    record Part(Uri uri,String mime,String name){}
    static byte[] ascii(String value){return value.getBytes(StandardCharsets.UTF_8);}
    static String string(byte[] value){return value==null?"":new String(value,StandardCharsets.UTF_8);}
    static String address(EncodedStringValue value){if(value==null)return "";String text=value.getString();int suffix=text.indexOf("/TYPE=");return suffix<0?text:text.substring(0,suffix);}
    static Uri persist(Context c,MultimediaMessagePdu pdu,int subscription,boolean outgoing,String token)throws IOException{
        if(!MessageStore.isDefault(c))throw new IOException("Sense Messages is no longer the default SMS app.");
        String number=outgoing&&pdu.getTo()!=null&&pdu.getTo().length>0?address(pdu.getTo()[0]):address(pdu.getFrom());if(number.isBlank())throw new IOException("MMS has no sender or recipient");
        ContentValues values=new ContentValues();values.put("thread_id",Telephony.Threads.getOrCreateThreadId(c,number));values.put("date",System.currentTimeMillis()/1000);values.put("date_sent",pdu.getDate());values.put("msg_box",outgoing?4:1);values.put("read",outgoing?1:0);values.put("seen",outgoing?1:0);values.put("m_type",0);values.put("v",18);values.put("ct_t","application/vnd.wap.multipart.related");values.put("sub_id",subscription);values.put("tr_id",token);if(pdu.getSubject()!=null){values.put("sub",pdu.getSubject().getString());values.put("sub_cs",106);}
        Uri row=c.getContentResolver().insert(Telephony.Mms.CONTENT_URI,values);if(row==null)throw new IOException("Cannot save MMS");
        try{long id=ContentUris.parseId(row);addAddress(c,id,number,outgoing?151:137);if(pdu.getTo()!=null&&!outgoing)for(var to:pdu.getTo())addAddress(c,id,address(to),151);if(pdu instanceof RetrieveConf received&&received.getCc()!=null)for(var cc:received.getCc())addAddress(c,id,address(cc),130);
            PduBody body=pdu.getBody();if(body!=null){if(body.getPartsNum()>100)throw new IOException("Too many MMS parts");for(int i=0;i<body.getPartsNum();i++)part(c,id,body.getPart(i),i);}ContentValues complete=new ContentValues();complete.put("m_type",outgoing?128:132);c.getContentResolver().update(row,complete,null,null);return row;
        }catch(IOException|RuntimeException failure){c.getContentResolver().delete(row,null,null);throw failure;}
    }
    static void addAddress(Context c,long id,String address,int type){ContentValues values=new ContentValues();values.put("address",address);values.put("type",type);values.put("charset",106);c.getContentResolver().insert(Uri.parse("content://mms/"+id+"/addr"),values);}
    static void part(Context c,long id,PduPart part,int position)throws IOException{
        String mime=string(part.getContentType());byte[] data=part.getData();if(data==null)data=new byte[0];if(data.length>MmsFiles.MAX_BYTES)throw new IOException("MMS part too large");
        ContentValues values=new ContentValues();values.put("ct",mime);values.put("seq",mime.equals("application/smil")?-1:position);values.put("chset",part.getCharset()==0?106:part.getCharset());values.put("cl",string(part.getContentLocation()));values.put("name",string(part.getName()));values.put("cid",string(part.getContentId()));
        boolean text=mime.equals("text/plain")||mime.equals("application/smil")||mime.equals("text/html");if(text){String charset="UTF-8";try{charset=CharacterSets.getMimeName(part.getCharset());}catch(Exception ignored){}if(charset==null||charset.isEmpty()||part.getCharset()==0)charset="UTF-8";try{values.put("text",new String(data,charset));}catch(UnsupportedEncodingException e){values.put("text",new String(data,StandardCharsets.UTF_8));}}
        Uri uri=c.getContentResolver().insert(Uri.parse("content://mms/"+id+"/part"),values);if(uri==null)throw new IOException("Cannot save MMS part");if(!text)try(OutputStream out=c.getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException("Cannot write MMS part");out.write(data);}
    }
    static List<Part> parts(Context c,long id){List<Part> result=new ArrayList<>();try(Cursor rows=c.getContentResolver().query(Uri.parse("content://mms/"+id+"/part"),new String[]{"_id","ct","name","cl"},null,null,"seq ASC")){if(rows!=null)while(rows.moveToNext()){String mime=MessageStore.value(rows,1);if(!mime.equals("text/plain")&&!mime.equals("application/smil")&&!mime.equals("text/html"))result.add(new Part(Uri.parse("content://mms/part/"+rows.getLong(0)),mime,rows.isNull(2)?MessageStore.value(rows,3):rows.getString(2)));}}return result;}
    static List<MessageStore.Message> messages(Context c,long thread){List<MessageStore.Message> result=new ArrayList<>();try(Cursor cursor=c.getContentResolver().query(Telephony.Mms.CONTENT_URI,new String[]{"_id","thread_id","date","msg_box","read","resp_st","m_type","sub"},thread>0?"thread_id = ? AND msg_box != 3 AND m_type IN (128,130,132)":"msg_box != 3 AND m_type IN (128,130,132)",thread>0?new String[]{Long.toString(thread)}:null,"date DESC, _id DESC")){
        if(cursor!=null)while(cursor.moveToNext()){
            long id=cursor.getLong(0);int box=cursor.getInt(3),type=box==1?1:box==2?2:cursor.getInt(5)>=129?5:4;String number="",body="";
            try(Cursor addresses=c.getContentResolver().query(Uri.parse("content://mms/"+id+"/addr"),new String[]{"address","type"},"type = ?",new String[]{box==1?"137":"151"},null)){if(addresses!=null&&addresses.moveToFirst())number=MessageStore.value(addresses,0);}
            try(Cursor parts=c.getContentResolver().query(Uri.parse("content://mms/"+id+"/part"),new String[]{"ct","text"},null,null,"seq ASC")){if(parts!=null)while(parts.moveToNext()){String mime=MessageStore.value(parts,0);if(mime.equals("text/plain"))body+=(body.isEmpty()?"":"\n")+MessageStore.value(parts,1);}}
            if(body.isBlank())body=cursor.getInt(6)==130?"MMS · Tap to download":"MMS attachment";
            int error=0;if(type==4||type==5)try(MmsTransfers db=new MmsTransfers(c)){var job=db.forRow("content://mms/"+id);if(job!=null&&job.state()==3){error=10001;type=5;}}
            result.add(new MessageStore.Message(id,cursor.getLong(1),number,body,cursor.getLong(2)*1000,type,cursor.getInt(4)!=0,-1,error,true,cursor.getInt(6)==130));
        }
    }return result;}
    static Uri received(Context c,String token){try(Cursor cursor=c.getContentResolver().query(Telephony.Mms.CONTENT_URI,new String[]{"_id"},"tr_id=? AND m_type=132",new String[]{token},null)){return cursor!=null&&cursor.moveToFirst()?ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI,cursor.getLong(0)):null;}}
    static boolean matches(Context c,MmsTransfers.Job job){if(!MessageStore.isDefault(c))return false;try(Cursor cursor=c.getContentResolver().query(Uri.parse(job.row()),new String[]{"tr_id"},null,null,null)){return cursor!=null&&cursor.moveToFirst()&&job.token().equals(cursor.getString(0));}}
    static void state(Context c,MmsTransfers.Job job,boolean success){if(!matches(c,job))return;ContentValues values=new ContentValues();values.put("msg_box",success?2:4);values.put("resp_st",success?128:129);c.getContentResolver().update(Uri.parse(job.row()),values,null,null);}
}
