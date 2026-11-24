package dev.sense.launcher;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Stock effect rendering, proportional geometry, fractional playback and activity lifecycle. */
final class FullscreenWeatherInstrumentation {
    private final Instrumentation test;
    FullscreenWeatherInstrumentation(Instrumentation test){this.test=test;}
    void run(){
        Bundle result=new Bundle();int status=Activity.RESULT_OK;MainActivity[] activity={null};
        var prefs=test.getTargetContext().getSharedPreferences(FullscreenWeather.PREFERENCES,0);Map<String,?> backup=prefs.getAll();
        WeatherRepository.get(test.getTargetContext()).localStorageForTests(true);
        var weatherPrefs=test.getTargetContext().getSharedPreferences("weather",0);Map<String,?> weatherBackup=weatherPrefs.getAll();
        LauncherSettingsActivity[] settings={null};
        try{
            prefs.edit().putBoolean("home",false).putBoolean("unlock",false).commit();
            Context context=test.getTargetContext();
            int[] mapping=FullscreenWeather.readMapping(context);
            if(mapping[17]!=3 || mapping[21]!=4 || mapping[31]!=7 || mapping[32]!=0)throw new AssertionError("Stock overlay mapping changed");
            RectF fit=new RectF();
            for(int[] size:new int[][]{{540,960},{1080,2400},{2400,1080}}){
                FullscreenWeather.Overlay.sceneBounds(size[0],size[1],fit);
                if(Math.abs(fit.width()/540f-fit.height()/960f)>.00001f || Math.abs(fit.centerX()-size[0]/2f)>.01f || Math.abs(fit.centerY()-size[1]/2f)>.01f)
                    throw new AssertionError("Overlay stretched or off center");
            }
            float quarter=FullscreenWeather.Overlay.frameAt(8_333_333L,0,150,5000);
            if(Math.abs(quarter-.25f)>.001f || FullscreenWeather.Overlay.frameAt(5_000_000_000L,0,150,5000)!=150)throw new AssertionError("120 Hz fractional timing or source duration changed");
            Bitmap sheet=Bitmap.createBitmap(270*4,480*2,Bitmap.Config.ARGB_8888);Canvas montage=new Canvas(sheet);montage.drawColor(0xff20302b);
            int rendered=0,interpolated=0;
            for(int effect=1;effect<=7;effect++){
                HtcWeatherScene scene=new HtcWeatherScene(context,String.format(Locale.ROOT,"htc_overlay_scene_%02d",effect),effect);
                if(scene.duration!=5000)throw new AssertionError("Effect duration differs from original");
                Bitmap bitmap=Bitmap.createBitmap(270,480,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);RectF viewport=new RectF(0,0,270,480);
                boolean visible=false,changed=false;
                for(float frame:new float[]{15,45,75,105,135}){
                    bitmap.eraseColor(Color.TRANSPARENT);scene.draw(canvas,viewport,frame);
                    int count=0;for(int y=0;y<480;y+=4)for(int x=0;x<270;x+=4)if(Color.alpha(bitmap.getPixel(x,y))>12)count++;
                    visible|=count>4;rendered++;
                    if(frame==75)montage.drawBitmap(bitmap,((effect-1)%4)*270,((effect-1)/4)*480,null);
                }
                if(!visible)throw new AssertionError("Blank full-screen effect "+effect);
                bitmap.eraseColor(Color.TRANSPARENT);scene.draw(canvas,viewport,45.25f);int[] first=new int[270*480];bitmap.getPixels(first,0,270,0,0,270,480);
                bitmap.eraseColor(Color.TRANSPARENT);scene.draw(canvas,viewport,45.75f);
                for(int y=0;y<480 && !changed;y+=3)for(int x=0;x<270;x+=3)if(first[y*270+x]!=bitmap.getPixel(x,y)){changed=true;break;}
                if(changed)interpolated++;bitmap.recycle();
            }
            if(interpolated<5)throw new AssertionError("Effects did not interpolate between source frames");
            try(var output=context.openFileOutput("fullscreen-weather-effects.png",0)){sheet.compress(Bitmap.CompressFormat.PNG,100,output);}sheet.recycle();
            HtcWeatherScene rain=new HtcWeatherScene(context,"htc_overlay_scene_03",3);
            Bitmap rainSheet=Bitmap.createBitmap(270*4,600*3,Bitmap.Config.ARGB_8888);
            Canvas rainCanvas=new Canvas(rainSheet);rainCanvas.drawColor(0xff707878);
            Paint caption=new Paint(Paint.ANTI_ALIAS_FLAG);caption.setColor(Color.WHITE);caption.setTextSize(15);
            for(int i=0;i<12;i++){
                int saved=rainCanvas.save();rainCanvas.translate((i%4)*270,(i/4)*600);
                RectF view=new RectF(0,0,270,600),bounds=new RectF();FullscreenWeather.Overlay.sceneBounds(270,600,bounds);
                rain.draw(rainCanvas,bounds,view,45+i*5);rainCanvas.drawText("Frame "+(45+i*5),8,20,caption);rainCanvas.restoreToCount(saved);
            }
            try(var output=context.openFileOutput("fullscreen-rain-framing.png",0)){rainSheet.compress(Bitmap.CompressFormat.PNG,100,output);}rainSheet.recycle();
            activity[0]=(MainActivity)test.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            await(()->activity[0].hasWindowFocus(),"Home focus");
            main(()->{
                Workspace workspace=find(activity[0].getWindow().getDecorView(),Workspace.class);
                if(workspace.page==0){workspace.page=1;workspace.render();}
                activity[0].fullscreenWeather().preview(3);
            });
            await(()->activity[0].fullscreenWeather().overlay()!=null,"Rain preview attached");
            FullscreenWeather.Overlay[] overlay={null};main(()->overlay[0]=activity[0].fullscreenWeather().overlay());
            await(()->overlay[0].callbacks()>10,"Display frame callbacks");
            main(()->{
                if(overlay[0].isClickable() || overlay[0].isFocusable())throw new AssertionError("Overlay intercepts home controls");
                long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,10,10,0);activity[0].dispatchTouchEvent(down);down.recycle();
                MotionEvent cancel=MotionEvent.obtain(now,now,MotionEvent.ACTION_CANCEL,10,10,0);activity[0].dispatchTouchEvent(cancel);cancel.recycle();
                if(activity[0].fullscreenWeather().overlay()!=null)throw new AssertionError("Touch did not dismiss effect");
                activity[0].fullscreenWeather().preview(5);
            });
            await(()->activity[0].fullscreenWeather().overlay()!=null,"Cached/new preview");
            main(()->activity[0].showApps());
            main(()->{if(activity[0].fullscreenWeather().overlay()!=null)throw new AssertionError("Drawer left fullscreen playback alive");});
            main(()->activity[0].closeDrawer());
            await(()->find(activity[0].getWindow().getDecorView(),dev.sense.launcher.ui.SenseDrawerLayout.class)==null,"Drawer close");
            SystemClock.sleep(2100);
            main(()->{
                String key=weatherPrefs.getString("latitude","-33.8688")+","+weatherPrefs.getString("longitude","151.2093");
                String data="{\"current\":{\"temperature_2m\":18,\"weather_code\":61,\"is_day\":1},\"daily\":{\"temperature_2m_max\":[21],\"temperature_2m_min\":[14]},\"fetched\":"+System.currentTimeMillis()+"}";
                weatherPrefs.edit().putString("data",data).putString("cacheKey",key).putString("status","Updated").apply();
                prefs.edit().putBoolean("home",true).apply();activity[0].fullscreenWeather().enterHome();
            });
            await(()->activity[0].fullscreenWeather().overlay()!=null,"Automatic live-condition home effect");
            main(()->activity[0].fullscreenWeather().stop());SystemClock.sleep(2100);
            main(()->activity[0].onBackPressed());
            await(()->activity[0].fullscreenWeather().overlay()!=null,"Home rebuild schedules automatic effect without another resume");
            settings[0]=(LauncherSettingsActivity)test.startActivitySync(new Intent(context,LauncherSettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            main(()->{if(activity[0].fullscreenWeather().overlay()!=null)throw new AssertionError("Leaving home retained overlay");settings[0].finish();});
            await(()->activity[0].hasWindowFocus(),"Home focus restored");
            if(!context.getSystemService(android.app.KeyguardManager.class).isDeviceSecure()){
                main(()->{prefs.edit().putBoolean("home",false).putBoolean("unlock",true).apply();activity[0].fullscreenWeather().stop();});
                SystemClock.sleep(2100);
                shell("input keyevent 223");
                await(()->!context.getSystemService(android.os.PowerManager.class).isInteractive(),"Screen off");
                SystemClock.sleep(250);shell("input keyevent 224");shell("wm dismiss-keyguard");
                await(()->activity[0].fullscreenWeather().overlay()!=null,"Real SystemUI unlock broadcast triggers effect with home playback disabled");
            }
            result.putString("result",rendered+" original effect poses, all seven effects visible, "+interpolated+" fractional-frame changes; aspect-preserving portrait/tall/landscape geometry, 120 Hz time sampling, vsync callbacks, touch dismissal, drawer cancellation, automatic home playback and pause cleanup passed");
        }catch(Throwable error){status=Activity.RESULT_CANCELED;result.putString("error",android.util.Log.getStackTraceString(error));}
        finally{
            main(()->{
                if(settings[0]!=null && !settings[0].isFinishing())settings[0].finish();
                if(activity[0]!=null)activity[0].fullscreenWeather().stop();
                restore(prefs,backup);restore(weatherPrefs,weatherBackup);
            });
            WeatherRepository.get(test.getTargetContext()).localStorageForTests(false);
        }
        test.finish(status,result);
    }
    private void shell(String command){
        try(var descriptor=test.getUiAutomation().executeShellCommand(command);var input=new java.io.FileInputStream(descriptor.getFileDescriptor())){input.readAllBytes();}
        catch(java.io.IOException error){throw new IllegalStateException(error);}
    }
    private static void restore(android.content.SharedPreferences prefs,Map<String,?> values){
        var editor=prefs.edit().clear();for(var e:values.entrySet()){
            Object v=e.getValue();String k=e.getKey();if(v instanceof Boolean b)editor.putBoolean(k,b);else if(v instanceof String t)editor.putString(k,t);else if(v instanceof Long l)editor.putLong(k,l);else if(v instanceof Integer i)editor.putInt(k,i);else if(v instanceof Float f)editor.putFloat(k,f);
        }editor.commit();
    }
    private void main(Runnable work){Throwable[] error={null};test.runOnMainSync(()->{try{work.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError(error[0]);}
    private void await(BooleanSupplier ready,String label)throws Exception{long end=SystemClock.uptimeMillis()+8000;while(SystemClock.uptimeMillis()<end){boolean[] value={false};main(()->value[0]=ready.getAsBoolean());if(value[0])return;Thread.sleep(30);}throw new AssertionError(label+" timed out");}
    private static <T>T find(View view,Class<T> type){if(type.isInstance(view))return type.cast(view);if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){T found=find(group.getChildAt(i),type);if(found!=null)return found;}return null;}
}
