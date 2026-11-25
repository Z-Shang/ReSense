package dev.sense.messages;

import android.content.Context;
import android.graphics.*;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.*;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.lang.ref.WeakReference;
import java.util.concurrent.*;

/** Bounded-size media decoding away from message queries and the UI thread. */
final class MmsPreview extends FrameLayout {
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final ExecutorService WORK=Executors.newFixedThreadPool(2);
    private static final LruCache<String,Bitmap> CACHE=new LruCache<>(8*1024*1024){
        @Override protected int sizeOf(String key,Bitmap bitmap){return bitmap.getByteCount();}
    };
    private final ImageView image;
    private final TextView placeholder;
    MmsPreview(Context context,Uri uri,String mime,Runnable open){
        super(context);
        boolean video=mime.startsWith("video/");
        setContentDescription(video?"MMS video preview":"MMS photo preview");
        setBackgroundColor(0xffdeded9);
        image=new ImageView(context);image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        addView(image,new LayoutParams(-1,-1));
        placeholder=new TextView(context);placeholder.setText("Loading preview…");placeholder.setTextColor(0xff444444);placeholder.setTextSize(12);placeholder.setGravity(Gravity.CENTER);
        addView(placeholder,new LayoutParams(-1,-1));
        if(video){ImageView play=new ImageView(context);play.setImageResource(R.drawable.msg_mms_play_btn);play.setScaleType(ImageView.ScaleType.FIT_CENTER);int size=Math.round(40*getResources().getDisplayMetrics().density);addView(play,new LayoutParams(size,size,Gravity.CENTER));}
        if(open!=null){setOnClickListener(v->open.run());setFocusable(true);}
        String key=mime+":"+uri;
        Bitmap cached=CACHE.get(key);
        if(cached!=null){show(cached);return;}
        Context app=context.getApplicationContext();WeakReference<MmsPreview> target=new WeakReference<>(this);
        WORK.execute(()->{
            if(target.get()==null)return;
            Bitmap bitmap=null;
            try{bitmap=decode(app,uri,mime);if(bitmap!=null)CACHE.put(key,bitmap);}catch(Exception|OutOfMemoryError unavailable){/* Keep the attachment accessible even if its preview is invalid. */}
            Bitmap result=bitmap;
            MAIN.post(()->{MmsPreview view=target.get();if(view!=null)view.show(result);});
        });
    }
    private void show(Bitmap bitmap){
        if(bitmap==null){placeholder.setText("Preview unavailable");return;}
        image.setImageBitmap(bitmap);placeholder.setVisibility(View.GONE);
    }
    boolean ready(){return image.getDrawable()!=null;}
    static Bitmap decode(Context context,Uri uri,String mime)throws Exception{
        if(mime.startsWith("video/")){
            try(MediaMetadataRetriever retriever=new MediaMetadataRetriever()){
                retriever.setDataSource(context,uri);
                return retriever.getScaledFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,480,480);
            }
        }
        ImageDecoder.Source input="file".equals(uri.getScheme())
                ?ImageDecoder.createSource(new java.io.File(uri.getPath()))
                :ImageDecoder.createSource(context.getContentResolver(),uri);
        return ImageDecoder.decodeBitmap(input,(decoder,info,source)->{
            int width=info.getSize().getWidth(),height=info.getSize().getHeight();
            float scale=Math.min(1,Math.min(480f/width,480f/height));
            decoder.setTargetSize(Math.max(1,Math.round(width*scale)),Math.max(1,Math.round(height*scale)));
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
        });
    }
}
