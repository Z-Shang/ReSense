package dev.sense.messages;
import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.*;

/** Narrow temporary grants for viewing a single attachment, without granting SMS access. */
public final class MmsAttachmentProvider extends ContentProvider {
    public boolean onCreate(){return true;}
    static Uri uri(Context c,long part){return Uri.parse("content://"+c.getPackageName()+".mms-attachment/"+part);}
    private Uri part(Uri u)throws FileNotFoundException{if(u.getPathSegments().size()!=1||!u.getLastPathSegment().matches("[0-9]{1,19}")||!MessageStore.permission(getContext(),android.Manifest.permission.READ_SMS))throw new FileNotFoundException();return Uri.parse("content://mms/part/"+u.getLastPathSegment());}
    public ParcelFileDescriptor openFile(Uri u,String mode)throws FileNotFoundException{if(!"r".equals(mode))throw new FileNotFoundException();return getContext().getContentResolver().openFileDescriptor(part(u),"r");}
    public String getType(Uri u){try{return getContext().getContentResolver().getType(part(u));}catch(FileNotFoundException e){return null;}}
    public Cursor query(Uri u,String[] p,String s,String[] a,String o){return null;}
    public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
