package dev.sense.messages;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import java.io.*;

/** Only explicit temporary URI grants allow the system MMS service to access a PDU. */
public final class MmsTransferProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    static Uri uri(Context c,String token){return Uri.parse("content://"+c.getPackageName()+".mms-transfer/"+token);}
    static File file(Context c,String token)throws FileNotFoundException{if(token==null||!token.matches("[0-9a-f-]{36}"))throw new FileNotFoundException("Invalid MMS token");return new File(MmsFiles.directory(c,"mms-transfers"),token);}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{if(uri.getPathSegments().size()!=1)throw new FileNotFoundException();String token=uri.getLastPathSegment();try(MmsTransfers db=new MmsTransfers(getContext())){var job=db.get(token);if(job==null||job.state()!=0)throw new FileNotFoundException("Inactive transfer");boolean write="w".equals(mode)||"wt".equals(mode)||"rw".equals(mode);if(write!=job.download())throw new FileNotFoundException("Wrong transfer direction");return ParcelFileDescriptor.open(file(getContext(),token),write?ParcelFileDescriptor.MODE_CREATE|ParcelFileDescriptor.MODE_TRUNCATE|ParcelFileDescriptor.MODE_WRITE_ONLY:ParcelFileDescriptor.MODE_READ_ONLY);}}
    @Override public String getType(Uri uri){return "application/vnd.wap.mms-message";}
    @Override public Cursor query(Uri u,String[] p,String s,String[] a,String o){return null;}
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
