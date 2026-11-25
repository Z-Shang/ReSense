package dev.sense.messages;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import dev.sense.messages.mms.pdu.*;
import java.io.*;
import java.util.*;

/** Actual Telephony MMS parts, including a video fixture and a malformed photo; no carrier use. */
final class MmsPreviewInstrumentation {
    final Instrumentation test;
    MmsPreviewInstrumentation(Instrumentation test){this.test=test;}
    void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    void collect(View root,List<MmsPreview> previews){if(root instanceof MmsPreview p)previews.add(p);if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)collect(group.getChildAt(i),previews);}
    View text(View root,String label){if(root instanceof TextView t&&label.contentEquals(t.getText()))return root;if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=text(group.getChildAt(i),label);if(found!=null)return found;}return null;}
    void part(PduBody body,String mime,String name,byte[] bytes){PduPart part=new PduPart();part.setContentType(MmsStore.ascii(mime));part.setName(MmsStore.ascii(name));part.setData(bytes);body.addPart(part);}
    void run(){
        Bundle report=new Bundle();Context context=test.getTargetContext();Uri row=null;MessagesActivity activity=null;
        try{
            check(MessageStore.isDefault(context),"Preview suite needs the temporary SMS role");
            PduBody body=new PduBody();
            Bitmap source=Bitmap.createBitmap(1200,800,Bitmap.Config.ARGB_8888);source.eraseColor(0xff2255cc);
            ByteArrayOutputStream png=new ByteArrayOutputStream();source.compress(Bitmap.CompressFormat.PNG,100,png);source.recycle();
            part(body,"image/png","Preview photo.png",png.toByteArray());
            // preview.mp4 is a synthetic 0.3-second red H.264 frame (320 × 180).
            try(InputStream video=test.getContext().getAssets().open("preview.mp4")){part(body,"video/mp4","Preview video.mp4",video.readAllBytes());}
            part(body,"image/png","Broken photo.png",new byte[]{1,2,3,4});
            RetrieveConf message=new RetrieveConf();message.setFrom(new EncodedStringValue("5550182040"));message.setBody(body);
            row=MmsStore.persist(context,message,1,false,UUID.randomUUID().toString());
            List<MmsStore.Part> parts=MmsStore.parts(context,ContentUris.parseId(row));check(parts.size()==3,"Lost MMS parts");
            Bitmap photo=MmsPreview.decode(context,parts.get(0).uri(),"image/png");check(photo.getWidth()==480&&photo.getHeight()==320,"Photo sizing/aspect ratio changed");photo.recycle();
            File draft=File.createTempFile("mms-preview-", ".png", context.getCacheDir());
            try{
                java.nio.file.Files.write(draft.toPath(),png.toByteArray());
                Bitmap draftPhoto=MmsPreview.decode(context,Uri.fromFile(draft),"image/png");
                check(draftPhoto.getWidth()==480&&draftPhoto.getHeight()==320,"Private draft photo did not decode");draftPhoto.recycle();
            }finally{draft.delete();}
            Bitmap video=MmsPreview.decode(context,parts.get(1).uri(),"video/mp4");check(video!=null&&video.getWidth()<=480&&video.getHeight()<=480,"Video frame unavailable");check(Color.red(video.getPixel(video.getWidth()/2,video.getHeight()/2))>180,"Incorrect video first frame");video.recycle();
            long thread=android.provider.Telephony.Threads.getOrCreateThreadId(context,"5550182040");
            activity=(MessagesActivity)test.startActivitySync(new Intent(context,MessagesActivity.class).putExtra("thread",thread).putExtra("address","5550182040").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            MessagesActivity opened=activity;boolean loaded=false;
            for(int attempt=0;attempt<150&&!loaded;attempt++){
                boolean[] ready={false};test.runOnMainSync(()->{
                    List<MmsPreview> previews=new ArrayList<>();collect(opened.getWindow().getDecorView(),previews);
                    ready[0]=previews.size()==3&&previews.stream().filter(MmsPreview::ready).count()==2&&text(opened.getWindow().getDecorView(),"Preview unavailable")!=null;
                    if(ready[0])for(MmsPreview p:previews)check(p.getWidth()>100&&p.getHeight()>0,"Preview collapsed in message bubble");
                });loaded=ready[0];if(!loaded)SystemClock.sleep(80);
            }
            check(loaded,"Conversation media previews did not load (including broken attachment fallback)");
            List<Intent> openedIntents=new ArrayList<>();
            Instrumentation.ActivityMonitor monitor=new Instrumentation.ActivityMonitor(){
                @Override public Instrumentation.ActivityResult onStartActivity(Intent intent){openedIntents.add(intent);return new Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null);}
            };
            test.addMonitor(monitor);
            try{
                test.runOnMainSync(()->{List<MmsPreview> previews=new ArrayList<>();collect(opened.getWindow().getDecorView(),previews);previews.get(0).performClick();previews.get(1).performClick();});
                check(openedIntents.size()==2,"Tapping media did not open attachments");
                for(Intent intent:openedIntents){check(Intent.ACTION_VIEW.equals(intent.getAction()),"Wrong viewer action");check((intent.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0,"Viewer missing narrow URI grant");check(intent.getData().getAuthority().equals(context.getPackageName()+".mms-attachment"),"Raw SMS permission exposed to viewer");}
                check("image/png".equals(openedIntents.get(0).getType())&&"video/mp4".equals(openedIntents.get(1).getType()),"Viewer MIME type lost");
            }finally{test.removeMonitor(monitor);}
            try(OutputStream output=context.openFileOutput("messages-media-previews.png",0)){Bitmap screen=test.getUiAutomation().takeScreenshot();screen.compress(Bitmap.CompressFormat.PNG,100,output);screen.recycle();}
            report.putString("result","PASS: actual MMS photo/video parts, bounded aspect-correct image, first video frame, nonzero bubble sizing, broken-image fallback and tap-to-open URI grants; no carrier send");test.finish(Activity.RESULT_OK,report);
        }catch(Throwable failure){report.putString("error",android.util.Log.getStackTraceString(failure));test.finish(Activity.RESULT_CANCELED,report);}
        finally{if(activity!=null){MessagesActivity closing=activity;test.runOnMainSync(closing::finish);}if(row!=null)context.getContentResolver().delete(row,null,null);}
    }
}
