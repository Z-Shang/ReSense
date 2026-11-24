package dev.sense.launcher;

import android.app.*;
import android.graphics.*;
import android.os.Bundle;
import org.json.*;

/** Compare nominal weather clipping against the real parent widget's available width. */
final class WeatherWidthInstrumentation {
    private final Instrumentation test;
    WeatherWidthInstrumentation(Instrumentation test){this.test=test;}
    void run(){
        Bundle report=new Bundle();
        try{
            int[] changed={0},checked={0};StringBuilder names=new StringBuilder();
                var context=test.getTargetContext();JSONObject catalog;
                try(var input=context.getResources().openRawResource(R.raw.htc_weather_catalog)){catalog=new JSONObject(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
                JSONArray scenes=catalog.getJSONArray("scenes");
                WeatherWidgetGeometry geometry=new WeatherWidgetGeometry(context);RectF projection=new RectF();geometry.fit(374,272,projection);
                RectF clip=new RectF(2,Math.max(0,projection.top),372,Math.min(272,projection.bottom));
                Bitmap before=Bitmap.createBitmap(374,272,Bitmap.Config.ARGB_8888),after=Bitmap.createBitmap(374,272,Bitmap.Config.ARGB_8888);
                Canvas a=new Canvas(before),b=new Canvas(after);
                int[] beforePixels=new int[374*272],afterPixels=new int[374*272];
                Bitmap sheet=Bitmap.createBitmap(748,4*300,Bitmap.Config.ARGB_8888);Canvas sheetCanvas=new Canvas(sheet);sheetCanvas.drawColor(0xff25302f);Paint text=new Paint(Paint.ANTI_ALIAS_FLAG);text.setColor(Color.WHITE);text.setTextSize(15);
                int exported=0;
                for(int i=0;i<scenes.length();i++){
                    JSONObject entry=scenes.getJSONObject(i);int code=entry.getInt("code");HtcWeatherScene scene=new HtcWeatherScene(context,code);boolean affected=false;
                    for(float frame:new float[]{(scene.start+scene.entranceEnd)/2,scene.entranceEnd,scene.end}){
                        before.eraseColor(Color.TRANSPARENT);after.eraseColor(Color.TRANSPARENT);
                        scene.draw(a,projection,frame);scene.draw(b,projection,clip,frame);checked[0]++;
                        before.getPixels(beforePixels,0,374,0,0,374,272);after.getPixels(afterPixels,0,374,0,0,374,272);
                        boolean extra=false;
                        for(int y=(int)Math.ceil(clip.top)+2;y<Math.floor(clip.bottom)-2;y++)for(int x=4;x<370;x++){
                            int index=y*374+x;
                            if(x>projection.left+2 && x<projection.right-2){if(beforePixels[index]!=afterPixels[index])throw new AssertionError("Weather projection changed in scene "+code);}
                            else if((x<projection.left-2 || x>projection.right+2) && Color.alpha(afterPixels[index])>12 && Color.alpha(beforePixels[index])==0)extra=true;
                        }
                        affected|=extra;
                    }
                    if(affected){
                        changed[0]++;if(names.length()>0)names.append(", ");names.append(code);
                        if(exported<4){int y=exported*300;sheetCanvas.drawBitmap(before,0,y+22,null);sheetCanvas.drawBitmap(after,374,y+22,null);sheetCanvas.drawText(code+" · "+entry.getString("label")+" · before",8,y+17,text);sheetCanvas.drawText("Wider parent clip · after",382,y+17,text);exported++;}
                    }
                }
                if(changed[0]==0)throw new AssertionError("No scene demonstrated clipped side effects");
                try(var output=new java.io.FileOutputStream(new java.io.File(context.getFilesDir(),"weather-width-comparison.png"))){sheet.compress(Bitmap.CompressFormat.PNG,100,output);}
                before.recycle();after.recycle();sheet.recycle();

            report.putString("result",checked[0]+" weather poses checked; original projection preserved, with side effects recovered in "+changed[0]+" scenes ("+names+")");test.finish(Activity.RESULT_OK,report);
        }catch(Throwable error){report.putString("error",android.util.Log.getStackTraceString(error));test.finish(Activity.RESULT_CANCELED,report);}
    }
}
