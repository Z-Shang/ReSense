package dev.sense.launcher;

import android.app.*;
import android.graphics.*;
import android.os.Bundle;

/** Render the actual stock frost scene, checking that its corners follow the glass edges. */
final class IceCoverageInstrumentation {
    private final Instrumentation test;
    IceCoverageInstrumentation(Instrumentation test){this.test=test;}
    void run(){
        Bundle result=new Bundle();Throwable[] failure={null};
        test.runOnMainSync(()->{
            try{
                HtcWeatherScene scene=new HtcWeatherScene(test.getTargetContext(),"htc_overlay_scene_04",4);
                if(scene.duration!=5000)throw new AssertionError("Stock ice timing changed");
                Bitmap sheet=Bitmap.createBitmap(1080,1800,Bitmap.Config.ARGB_8888);
                Canvas montage=new Canvas(sheet);montage.drawColor(0xff43535c);
                int layout=0;
                for(int[] size:new int[][]{{270,480},{270,600},{600,270}}){
                    Bitmap image=Bitmap.createBitmap(size[0],size[1],Bitmap.Config.ARGB_8888);
                    RectF clip=new RectF(0,0,size[0],size[1]),bounds=new RectF();
                    FullscreenWeather.Overlay.sceneBounds(size[0],size[1],bounds);
                    for(int row=0;row<3;row++){
                        image.eraseColor(Color.TRANSPARENT);
                        scene.draw(new Canvas(image),bounds,clip,new float[]{20,60,100}[row]);
                        if(row>0){
                            // Frost is established by frame 60. Each corner must reach the screen,
                            // rather than the edge of a centered portrait-shaped inset.
                            for(int corner=0;corner<4;corner++){
                                int hits=0;
                                int x0=(corner==0||corner==3)?0:size[0]-12;
                                int y0=corner<2?0:size[1]-12;
                                for(int y=y0;y<y0+12;y++)for(int x=x0;x<x0+12;x++)
                                    if(Color.alpha(image.getPixel(x,y))>12)hits++;
                                if(hits<3)throw new AssertionError("Frost corner "+corner+" missing at "+size[0]+"x"+size[1]+" frame "+row);
                            }
                        }
                        if(layout<2)montage.drawBitmap(image,layout*270,row*600,null);
                        else montage.drawBitmap(image,null,new RectF(540,row*600,1080,row*600+243),null);
                    }
                    image.recycle();layout++;
                }
                try(var output=test.getTargetContext().openFileOutput("ice-coverage-extension.png",0)){sheet.compress(Bitmap.CompressFormat.PNG,100,output);}
                sheet.recycle();
            }catch(Throwable error){failure[0]=error;}
        });
        if(failure[0]!=null){result.putString("error",android.util.Log.getStackTraceString(failure[0]));test.finish(Activity.RESULT_CANCELED,result);}
        else{result.putString("result","Ice/snow frost reaches all four screen corners at stock, tall and landscape ratios; original timing retained");test.finish(Activity.RESULT_OK,result);}
    }
}
