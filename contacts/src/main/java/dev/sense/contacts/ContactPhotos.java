package dev.sense.contacts;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executors;

final class ContactPhotos {
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService worker=Executors.newSingleThreadExecutor();
    private final LruCache<String,Bitmap> cache=new LruCache<>(4*1024*1024){@Override protected int sizeOf(String key,Bitmap b){return b.getAllocationByteCount();}};
    private final Set<String> pending=new HashSet<>(),failed=new HashSet<>();
    private boolean closed;
    ContactPhotos(Context context){this.context=context.getApplicationContext();}
    void bind(ImageView view,String uri,Runnable changed){
        view.setImageResource(dev.sense.framework.R.drawable.sense_people_default_photo);
        if(uri==null||uri.isBlank()||closed)return;
        Bitmap cached=cache.get(uri);if(cached!=null){view.setImageBitmap(cached);return;}
        if(failed.contains(uri)||!pending.add(uri))return;
        worker.execute(()->{
            Bitmap result=null;
            try{result=ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.getContentResolver(),Uri.parse(uri)),(d,info,source)->{
                int width=info.getSize().getWidth(),height=info.getSize().getHeight();float factor=Math.min(1,256f/Math.max(width,height));d.setTargetSize(Math.max(1,Math.round(width*factor)),Math.max(1,Math.round(height*factor)));d.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            });}catch(IOException|RuntimeException ignored){}
            Bitmap image=result;main.post(()->{pending.remove(uri);if(closed)return;if(image!=null)cache.put(uri,image);else failed.add(uri);changed.run();});
        });
    }
    void invalidate(){cache.evictAll();failed.clear();}
    void close(){closed=true;worker.shutdownNow();main.removeCallbacksAndMessages(null);cache.evictAll();}
}
