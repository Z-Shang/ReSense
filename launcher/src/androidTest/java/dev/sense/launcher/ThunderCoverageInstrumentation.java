package dev.sense.launcher;

import android.app.*;
import android.graphics.*;
import android.os.Bundle;

/** Ensure the original flash track lights the full viewport without an inset rectangle. */
final class ThunderCoverageInstrumentation {
    private final Instrumentation test;
    ThunderCoverageInstrumentation(Instrumentation test){this.test=test;}
    void run(){
        Bundle result=new Bundle();Throwable[] failure={null};
        test.runOnMainSync(()->{
            try{
                HtcWeatherScene scene=new HtcWeatherScene(test.getTargetContext(),"htc_overlay_scene_06",6);
                if(scene.duration!=5000)throw new AssertionError("Stock thunder timing changed");
                Bitmap sheet=Bitmap.createBitmap(1080,1800,Bitmap.Config.ARGB_8888);
                Canvas montage=new Canvas(sheet);montage.drawColor(0xff43535c);
                int layout=0;
                for(int[] size:new int[][]{{270,480},{270,600},{600,270}}){
                    Bitmap image=Bitmap.createBitmap(size[0],size[1],Bitmap.Config.ARGB_8888);
                    RectF clip=new RectF(0,0,size[0],size[1]),bounds=new RectF();
                    FullscreenWeather.Overlay.sceneBounds(size[0],size[1],bounds);
                    for(int frame:new int[]{21,63,121}){
                        image.eraseColor(Color.TRANSPARENT);
                        scene.draw(new Canvas(image),bounds,clip,frame);
                        for(int y=0;y<size[1];y+=3)for(int x=0;x<size[0];x+=3)
                            if(Color.alpha(image.getPixel(x,y))<175)throw new AssertionError("Flash does not reach "+x+","+y+" at "+size[0]+"x"+size[1]+" frame "+frame);
                    }
                    image.eraseColor(Color.TRANSPARENT);scene.draw(new Canvas(image),bounds,clip,22);
                    boolean clear=false;
                    for(int y=0;y<size[1];y+=3)for(int x=0;x<size[0];x+=3)
                        if(Color.alpha(image.getPixel(x,y))==0)clear=true;
                    if(!clear)throw new AssertionError("Flash persisted beyond its original track");
                    Bitmap offset=Bitmap.createBitmap(size[0]+20,size[1]+20,Bitmap.Config.ARGB_8888);
                    RectF shifted=new RectF(10,10,size[0]+10,size[1]+10),fit=new RectF(bounds);fit.offset(10,10);
                    scene.draw(new Canvas(offset),fit,shifted,121);
                    if(Color.alpha(offset.getPixel(0,0))!=0||Color.alpha(offset.getPixel(10,10))<175)
                        throw new AssertionError("Flash escaped or ignored offset viewport");
                    offset.recycle();
                    for(int row=0;row<3;row++){
                        image.eraseColor(Color.TRANSPARENT);scene.draw(new Canvas(image),bounds,clip,new float[]{20,63,121}[row]);
                        if(layout<2)montage.drawBitmap(image,layout*270,row*600,null);
                        else montage.drawBitmap(image,null,new RectF(540,row*600,1080,row*600+243),null);
                    }
                    image.recycle();layout++;
                }
                try(var output=test.getTargetContext().openFileOutput("thunder-coverage-extension.png",0)){sheet.compress(Bitmap.CompressFormat.PNG,100,output);}
                sheet.recycle();
            }catch(Throwable error){failure[0]=error;}
        });
        if(failure[0]!=null){result.putString("error",android.util.Log.getStackTraceString(failure[0]));test.finish(Activity.RESULT_CANCELED,result);}
        else{result.putString("result","Thunder flash covers stock, tall and landscape viewports; original timing, flash release and offset clipping passed");test.finish(Activity.RESULT_OK,result);}
    }
}
