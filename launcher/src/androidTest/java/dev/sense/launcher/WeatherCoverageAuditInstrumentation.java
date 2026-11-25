package dev.sense.launcher;

import android.app.*;
import android.graphics.*;
import android.os.Bundle;
import org.json.*;
import java.util.Locale;

/** Inspect coverage without assuming that a localized sun or cloud must fill the screen. */
final class WeatherCoverageAuditInstrumentation {
    private final Instrumentation test;
    WeatherCoverageAuditInstrumentation(Instrumentation test){this.test=test;}
    void run(){
        Bundle result=new Bundle();Throwable[] failure={null};int[] rendered={0};
        test.runOnMainSync(()->{
            try{
                JSONArray report=new JSONArray();
                int[] effects={1,2,4,5,6,7};
                float[] frames={0,10,20,30,40,50,60,70,80,90,100,110,120,121,130,140,149};
                Bitmap overview=Bitmap.createBitmap(1080,1600,Bitmap.Config.ARGB_8888);
                Canvas overviewCanvas=new Canvas(overview);overviewCanvas.drawColor(0xff43535c);
                Paint text=new Paint(Paint.ANTI_ALIAS_FLAG);text.setColor(Color.WHITE);text.setTextSize(13);
                int column=0;
                for(int effect:effects){
                    HtcWeatherScene scene=new HtcWeatherScene(test.getTargetContext(),String.format(Locale.ROOT,"htc_overlay_scene_%02d",effect),effect);
                    if(scene.duration!=5000)throw new AssertionError("Original timing changed for "+effect);
                    Bitmap sheet=Bitmap.createBitmap(1080,1800,Bitmap.Config.ARGB_8888);
                    Canvas montage=new Canvas(sheet);montage.drawColor(0xff43535c);
                    int layout=0;
                    for(int[] size:new int[][]{{270,480},{270,600},{600,270}}){
                        Bitmap bitmap=Bitmap.createBitmap(size[0],size[1],Bitmap.Config.ARGB_8888);
                        Canvas canvas=new Canvas(bitmap);RectF clip=new RectF(0,0,size[0],size[1]),bounds=new RectF();
                        FullscreenWeather.Overlay.sceneBounds(size[0],size[1],bounds);
                        float maxCoverage=0,maxBottom=0,maxTop=0,maxLeft=0,maxRight=0;
                        Rect union=new Rect();int visibleFrames=0;
                        for(float frame:frames){
                            bitmap.eraseColor(Color.TRANSPARENT);scene.draw(canvas,bounds,clip,frame);rendered[0]++;
                            int visible=0,total=0,bottom=0,bottomTotal=0,top=0,topTotal=0,left=0,leftTotal=0,right=0,rightTotal=0;
                            for(int y=0;y<size[1];y+=3)for(int x=0;x<size[0];x+=3){
                                boolean ink=Color.alpha(bitmap.getPixel(x,y))>12;total++;
                                if(ink){visible++;union.union(x,y,x+1,y+1);}
                                if(y>=size[1]*4/5){bottomTotal++;if(ink)bottom++;}
                                if(y<size[1]/5){topTotal++;if(ink)top++;}
                                if(x<size[0]/5){leftTotal++;if(ink)left++;}
                                if(x>=size[0]*4/5){rightTotal++;if(ink)right++;}
                            }
                            if(visible>0)visibleFrames++;
                            maxCoverage=Math.max(maxCoverage,visible/(float)total);
                            maxBottom=Math.max(maxBottom,bottom/(float)bottomTotal);
                            maxTop=Math.max(maxTop,top/(float)topTotal);
                            maxLeft=Math.max(maxLeft,left/(float)leftTotal);
                            maxRight=Math.max(maxRight,right/(float)rightTotal);
                        }
                        if(visibleFrames==0)throw new AssertionError("Blank effect "+effect+" at "+size[0]+"x"+size[1]);
                        report.put(new JSONObject().put("effect",effect).put("width",size[0]).put("height",size[1])
                                .put("visibleFrames",visibleFrames).put("sampleCount",frames.length)
                                .put("maxVisibleFraction",maxCoverage).put("maxBottomFifthFraction",maxBottom)
                                .put("maxTopFifthFraction",maxTop).put("maxLeftFifthFraction",maxLeft).put("maxRightFifthFraction",maxRight)
                                .put("visibleUnion",new JSONArray(new int[]{union.left,union.top,union.right,union.bottom})));
                        for(int row=0;row<3;row++){
                            float frame=new float[]{20,60,100}[row];bitmap.eraseColor(Color.TRANSPARENT);scene.draw(canvas,bounds,clip,frame);
                            if(layout<2)montage.drawBitmap(bitmap,layout*270,row*600,null);
                            else montage.drawBitmap(bitmap,null,new RectF(540,row*600,1080,row*600+243),null);
                            montage.drawText(size[0]+"x"+size[1]+" f"+(int)frame,layout<2?layout*270+5:545,row*600+17,text);
                        }
                        if(layout==1)for(int row=0;row<4;row++){
                            float frame=new float[]{20,60,100,121}[row];bitmap.eraseColor(Color.TRANSPARENT);scene.draw(canvas,bounds,clip,frame);
                            overviewCanvas.drawBitmap(bitmap,null,new RectF(column*180,row*400,column*180+180,row*400+400),null);
                            overviewCanvas.drawText("Effect "+effect+" f"+(int)frame,column*180+4,row*400+16,text);
                        }
                        bitmap.recycle();layout++;
                    }
                    save(String.format(Locale.ROOT,"weather-coverage-%02d.png",effect),sheet);sheet.recycle();column++;
                }
                save("weather-coverage-overview.png",overview);overview.recycle();
                try(var output=test.getTargetContext().openFileOutput("weather-coverage-audit.json",0)){
                    output.write(new JSONObject().put("alphaThreshold",12).put("pixelStep",3).put("frames",new JSONArray(frames))
                            .put("note","Peak fractions are sampled independently; transparent margins may be intentional.")
                            .put("cases",report).toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            }catch(Throwable error){failure[0]=error;}
        });
        if(failure[0]!=null){result.putString("error",android.util.Log.getStackTraceString(failure[0]));test.finish(Activity.RESULT_CANCELED,result);}
        else{result.putString("result",rendered[0]+" poses across six effects and three aspect ratios; all effects visible; coverage report and previews exported");test.finish(Activity.RESULT_OK,result);}
    }
    private void save(String name,Bitmap bitmap)throws java.io.IOException{
        try(var output=test.getTargetContext().openFileOutput(name,0)){bitmap.compress(Bitmap.CompressFormat.PNG,100,output);}
    }
}
