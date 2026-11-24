package dev.sense.messages;

import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.io.*;
import java.util.*;
import org.json.*;

/** Draft attachments are private copies: picker grants need not survive a reboot. */
final class MmsFiles {
    static final int MAX_BYTES=20*1024*1024;
    record Attachment(String file,String name,String mime,long bytes){}
    static File directory(Context c,String name){File dir=new File(c.getFilesDir(),name);if(!dir.isDirectory()&&!dir.mkdirs())throw new IllegalStateException("Cannot create attachment storage");return dir;}
    static File file(Context c,Attachment a){if(!a.file().matches("[0-9a-f-]{36}"))throw new IllegalArgumentException("Invalid attachment");return new File(directory(c,"mms-attachments"),a.file());}
    static Attachment copy(Context c,Uri uri)throws IOException{
        if(!"content".equals(uri.getScheme()))throw new IOException("Choose a file from an Android document provider.");
        String mime=c.getContentResolver().getType(uri);if(mime==null)throw new IOException("Unknown attachment type");
        if(!(mime.startsWith("image/")||mime.startsWith("video/")||mime.startsWith("audio/")||mime.equals("text/vcard")||mime.equals("text/x-vcard")))throw new IOException("Choose a photo, video, audio file or contact card.");
        String name="Attachment";try(Cursor cursor=c.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(cursor!=null&&cursor.moveToFirst()&&!cursor.isNull(0))name=cursor.getString(0);}name=name.replaceAll("[\\r\\n\\x00]","");if(name.length()>120)name=name.substring(0,120);
        Attachment result=new Attachment(UUID.randomUUID().toString(),name,mime,0);File output=file(c,result);
        try(InputStream in=c.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(output)){if(in==null)throw new IOException("Cannot open attachment");byte[] buffer=new byte[8192];int n,total=0;while((n=in.read(buffer))!=-1){total+=n;if(total>MAX_BYTES)throw new IOException("Attachment is too large (20 MB maximum before image resizing).");out.write(buffer,0,n);}}catch(IOException|RuntimeException e){output.delete();throw e;}
        return new Attachment(result.file(),name,mime,output.length());
    }
    static byte[] bytes(Context c,Attachment attachment,int budget,int maxWidth,int maxHeight)throws IOException{
        File source=file(c,attachment);if(!source.isFile()||source.length()>MAX_BYTES)throw new IOException("Attachment unavailable");
        if(attachment.mime().startsWith("image/")&&!attachment.mime().equals("image/gif")){
            Bitmap bitmap=ImageDecoder.decodeBitmap(ImageDecoder.createSource(source),(decoder,info,s)->{int width=info.getSize().getWidth(),height=info.getSize().getHeight();float scale=Math.min(1,Math.min(maxWidth/(float)width,maxHeight/(float)height));decoder.setTargetSize(Math.max(1,(int)(width*scale)),Math.max(1,(int)(height*scale)));decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);});
            try{for(int round=0;round<5;round++){for(int quality:new int[]{85,65,45}){ByteArrayOutputStream output=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.JPEG,quality,output);if(output.size()<=budget)return output.toByteArray();}Bitmap reduced=Bitmap.createScaledBitmap(bitmap,Math.max(1,bitmap.getWidth()*3/4),Math.max(1,bitmap.getHeight()*3/4),true);if(reduced==bitmap)break;bitmap.recycle();bitmap=reduced;}}finally{bitmap.recycle();}
            throw new IOException("Photo cannot fit this carrier's MMS limit.");
        }
        if(source.length()>budget)throw new IOException("Attachment exceeds this carrier's MMS limit. Choose a smaller file.");return java.nio.file.Files.readAllBytes(source.toPath());
    }
    static String encode(List<Attachment> attachments){JSONArray array=new JSONArray();for(var a:attachments)try{array.put(new JSONObject().put("file",a.file()).put("name",a.name()).put("mime",a.mime()).put("bytes",a.bytes()));}catch(JSONException impossible){throw new IllegalStateException(impossible);}return array.toString();}
    static List<Attachment> decode(String value){List<Attachment> result=new ArrayList<>();try{JSONArray array=new JSONArray(value);for(int i=0;i<Math.min(10,array.length());i++){JSONObject a=array.getJSONObject(i);result.add(new Attachment(a.getString("file"),a.getString("name"),a.getString("mime"),a.getLong("bytes")));}}catch(JSONException ignored){}return result;}
    static List<Attachment> draft(Context c,String number){return decode(c.getSharedPreferences("drafts",0).getString("attachments:"+SendCoordinator.key(number),"[]"));}
    static void save(Context c,String number,List<Attachment> attachments){c.getSharedPreferences("drafts",0).edit().putString("attachments:"+SendCoordinator.key(number),encode(attachments)).apply();}
}
