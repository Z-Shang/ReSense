package dev.sense.launcher;

import android.app.*;
import android.graphics.*;
import android.os.Bundle;

/** Actual Canvas rendering at multiple aspect ratios; no changes to user preferences. */
final class RainCoverageInstrumentation {
    private final Instrumentation test;
    RainCoverageInstrumentation(Instrumentation test){this.test=test;}
    void run(){
        Bundle result=new Bundle();Throwable[] failure={null};int[] samples={0};
        test.runOnMainSync(()->{
            try{
                HtcWeatherScene scene=new HtcWeatherScene(test.getTargetContext(),"htc_overlay_scene_03",3);
                if(scene.duration!=5000)throw new AssertionError("Original playback duration changed");
                Bitmap sheet=Bitmap.createBitmap(1080,1800,Bitmap.Config.ARGB_8888);
                Canvas montage=new Canvas(sheet);montage.drawColor(0xff43535c);
                int column=0;
                for(int[] size:new int[][]{{270,480},{270,600},{600,270}}){
                    Bitmap bitmap=Bitmap.createBitmap(size[0],size[1],Bitmap.Config.ARGB_8888);
                    Canvas canvas=new Canvas(bitmap);RectF clip=new RectF(0,0,size[0],size[1]),bounds=new RectF();
                    FullscreenWeather.Overlay.sceneBounds(size[0],size[1],bounds);
                    int row=0;
                    for(float frame:new float[]{20,55,70}){
                        bitmap.eraseColor(Color.TRANSPARENT);scene.draw(canvas,bounds,clip,frame);
                        if(frame==70){
                            int lower=0;
                            for(int y=size[1]*4/5;y<size[1];y+=3)for(int x=0;x<size[0];x+=3)
                                if(Color.alpha(bitmap.getPixel(x,y))>8)lower++;
                            if(lower<20)throw new AssertionError("Rain missing at lower screen: "+size[0]+"x"+size[1]+" pixels="+lower);
                        }
                        if(column<2)montage.drawBitmap(bitmap,column*270,row*600,null);
                        else montage.drawBitmap(bitmap,null,new RectF(540,row*600,1080,row*600+243),null);
                        row++;samples[0]++;
                    }
                    // Drawing into an offset viewport must neither assume a zero origin nor escape its clip.
                    Bitmap offset=Bitmap.createBitmap(size[0]+20,size[1]+20,Bitmap.Config.ARGB_8888);
                    RectF shifted=new RectF(10,10,size[0]+10,size[1]+10),fit=new RectF(bounds);fit.offset(10,10);
                    scene.draw(new Canvas(offset),fit,shifted,70);
                    for(int y=0;y<offset.getHeight();y++)if(Color.alpha(offset.getPixel(0,y))!=0)throw new AssertionError("Escaped viewport clip");
                    if(offset.getPixel(size[0]/2+10,size[1]-5)!=bitmap.getPixel(size[0]/2,size[1]-15))throw new AssertionError("Offset viewport changed mapping");
                    offset.recycle();bitmap.recycle();column++;
                }
                try(var output=test.getTargetContext().openFileOutput("rain-coverage-extension.png",0)){
                    sheet.compress(Bitmap.CompressFormat.PNG,100,output);
                }
                sheet.recycle();
            }catch(Throwable error){failure[0]=error;}
        });
        if(failure[0]!=null){result.putString("error",android.util.Log.getStackTraceString(failure[0]));test.finish(Activity.RESULT_CANCELED,result);}
        else{result.putString("result",samples[0]+" original-track rain poses; bottom coverage, landscape and offset clipping passed");test.finish(Activity.RESULT_OK,result);}
    }
}
