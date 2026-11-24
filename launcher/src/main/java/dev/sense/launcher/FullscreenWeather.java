package dev.sense.launcher;

import android.animation.ValueAnimator;
import android.app.KeyguardManager;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.FrameLayout;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import org.json.*;

/** Activity-owned transparent weather overlay; original timing, display-paced interpolation. */
final class FullscreenWeather {
    static final String PREFERENCES="weather_effects";
    private final MainActivity activity;
    private final FrameLayout stage;
    private final BooleanSupplier homeVisible;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService loader=Executors.newSingleThreadExecutor();
    private final android.util.LruCache<Integer,HtcWeatherScene> scenes=new android.util.LruCache<>(7);
    private final WeatherRepository weather;
    private final int[] mapping;
    private Overlay current;
    private int generation,pendingPreview;
    private boolean resumed,destroyed,pendingAutomatic,screenWasOff,hasResumed,pendingHomeEntry;
    private long lastPlayed=-10000,pendingUnlock=-10000;
    private final Runnable attempt=this::attemptAutomatic;
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener weatherChanged=(prefs,key)->{
        if(pendingAutomatic && "data".equals(key))schedule();
    };
    private final BroadcastReceiver screen=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            if(Intent.ACTION_SCREEN_OFF.equals(intent.getAction())){screenWasOff=true;pendingUnlock=-10000;stop();}
            else if(Intent.ACTION_USER_PRESENT.equals(intent.getAction())){
                screenWasOff=false;
                if(resumed)requestAutomatic("unlock");else pendingUnlock=SystemClock.elapsedRealtime();
            }
        }
    };
    FullscreenWeather(MainActivity activity,FrameLayout stage,BooleanSupplier homeVisible,boolean restored){
        this.activity=activity;this.stage=stage;this.homeVisible=homeVisible;weather=WeatherRepository.get(activity);
        hasResumed=restored;
        screenWasOff=!activity.getSystemService(PowerManager.class).isInteractive()||activity.getSystemService(KeyguardManager.class).isKeyguardLocked();
        mapping=readMapping(activity);
        IntentFilter filter=new IntentFilter(Intent.ACTION_USER_PRESENT);filter.addAction(Intent.ACTION_SCREEN_OFF);
        // USER_PRESENT is protected, but may originate from SystemUI's UID.
        activity.registerReceiver(screen,filter,Context.RECEIVER_EXPORTED);
        weather.prefs.registerOnSharedPreferenceChangeListener(weatherChanged);
    }
    static int[] readMapping(Context context){
        try(var input=context.getResources().openRawResource(R.raw.htc_overlay_catalog)){
            JSONArray values=new JSONObject(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getJSONArray("mapping");
            int[] result=new int[values.length()];for(int i=0;i<result.length;i++)result[i]=values.getInt(i);return result;
        }catch(java.io.IOException|JSONException e){throw new IllegalStateException("Invalid fullscreen weather catalog",e);}
    }
    void resume(boolean automatic){
        resumed=true;
        boolean homeEntry=!hasResumed||pendingHomeEntry;hasResumed=true;pendingHomeEntry=false;
        boolean unlocked=screenWasOff||SystemClock.elapsedRealtime()-pendingUnlock<10000;screenWasOff=false;pendingUnlock=-10000;
        // Queue now and wait for focus/unlock in ready(), rather than losing
        // a resume that arrives before keyguard dismissal has completed.
        // A widget's PendingIntent can pause us while its editor is open. Its
        // return is not a new home entry; only initial entry, HOME or unlock is.
        if(automatic&&(unlocked||homeEntry))requestAutomatic(unlocked?"unlock":"home");
    }
    void enterHome(){if(resumed)requestAutomatic("home");else pendingHomeEntry=true;}
    void focus(){if(pendingPreview>0)tryPreview();else if(pendingAutomatic)schedule();}
    private void requestAutomatic(String reason){
        if(destroyed || !resumed || !homeVisible.getAsBoolean() || !activity.getSharedPreferences(PREFERENCES,0).getBoolean(reason,true) || !ValueAnimator.areAnimatorsEnabled())return;
        pendingAutomatic=true;schedule();
    }
    private void schedule(){main.removeCallbacks(attempt);main.postDelayed(attempt,400);}
    private boolean ready(){KeyguardManager keyguard=activity.getSystemService(KeyguardManager.class);return !destroyed && resumed && homeVisible.getAsBoolean() && activity.hasWindowFocus() && activity.getSystemService(PowerManager.class).isInteractive() && !keyguard.isKeyguardLocked() && !keyguard.isDeviceLocked();}
    private void attemptAutomatic(){
        if(!pendingAutomatic || !ready())return;
        var snapshot=weather.snapshot();if(snapshot==null)return;
        pendingAutomatic=false;
        int condition=WeatherCodes.htcScene(snapshot.code,snapshot.day);
        int effect=condition>0 && condition<=mapping.length?mapping[condition-1]:0;
        if(effect>0 && SystemClock.elapsedRealtime()-lastPlayed>2000)play(effect);
    }
    void preview(int effect){if(effect>=1 && effect<=7){stop();pendingPreview=effect;tryPreview();}}
    private void tryPreview(){if(ready()){int effect=pendingPreview;pendingPreview=0;play(effect);}}
    private void play(int effect){
        stop();if(destroyed || !resumed || !homeVisible.getAsBoolean())return;
        int request=generation;
        HtcWeatherScene cached=scenes.get(effect);
        if(cached!=null){attach(cached,request);return;}
        Context context=activity.getApplicationContext();
        loader.execute(()->{
            HtcWeatherScene loaded;
            try{loaded=new HtcWeatherScene(context,String.format(java.util.Locale.ROOT,"htc_overlay_scene_%02d",effect),effect);}
            catch(RuntimeException error){android.util.Log.e("SenseWeather","Cannot load fullscreen effect",error);return;}
            main.post(()->{
                if(destroyed)return;scenes.put(effect,loaded);
                attach(loaded,request);
            });
        });
    }
    private void attach(HtcWeatherScene scene,int request){
        if(generation!=request || !ready())return;
        current=new Overlay(activity,scene,this::finished);
        stage.addView(current,new FrameLayout.LayoutParams(-1,-1));
        lastPlayed=SystemClock.elapsedRealtime();
    }
    private void finished(Overlay overlay){if(current==overlay){current=null;overlay.stop();stage.removeView(overlay);}}
    void pause(){resumed=false;pendingHomeEntry=false;stop();}
    void stop(){
        generation++;pendingPreview=0;pendingAutomatic=false;main.removeCallbacks(attempt);
        if(current!=null){Overlay old=current;current=null;old.stop();stage.removeView(old);}
    }
    void destroy(){destroyed=true;stop();activity.unregisterReceiver(screen);weather.prefs.unregisterOnSharedPreferenceChangeListener(weatherChanged);loader.shutdownNow();scenes.evictAll();}
    Overlay overlay(){return current;}

    static final class Overlay extends View implements Choreographer.FrameCallback {
        private final HtcWeatherScene scene;
        private final java.util.function.Consumer<Overlay> finished;
        private final RectF viewport=new RectF(),projection=new RectF();
        private long started=-1;
        private boolean running;
        private float frame;
        private int callbacks;
        private final Runnable reducedMotionEnd=this::complete;
        private void complete(){finished.accept(this);}
        Overlay(Context context,HtcWeatherScene scene,java.util.function.Consumer<Overlay> finished){
            super(context);this.scene=scene;this.finished=finished;
            setClickable(false);setFocusable(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        static float frameAt(long elapsedNanos,float start,float end,long durationMillis){return start+(end-start)*Math.min(1,Math.max(0,elapsedNanos/(durationMillis*1_000_000.0f)));}
        static void sceneBounds(float width,float height,RectF result){float scale=Math.min(width/540f,height/960f);float w=540*scale,h=960*scale;result.set((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2);}
        @Override protected void onAttachedToWindow(){
            super.onAttachedToWindow();running=true;
            if(ValueAnimator.areAnimatorsEnabled())Choreographer.getInstance().postFrameCallback(this);
            else{frame=(scene.start+scene.end)/2;invalidate();postDelayed(reducedMotionEnd,1000);}
        }
        @Override public void doFrame(long frameTimeNanos){
            if(!running)return;if(started<0)started=frameTimeNanos;
            frame=frameAt(frameTimeNanos-started,scene.start,scene.end,scene.duration);callbacks++;
            if(frameTimeNanos-started>=scene.duration*1_000_000L){finished.accept(this);return;}
            invalidate();Choreographer.getInstance().postFrameCallback(this);
        }
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);viewport.set(0,0,getWidth(),getHeight());sceneBounds(getWidth(),getHeight(),projection);scene.draw(canvas,projection,viewport,frame);}
        int callbacks(){return callbacks;}
        float frame(){return frame;}
        void stop(){running=false;Choreographer.getInstance().removeFrameCallback(this);removeCallbacks(reducedMotionEnd);}
        @Override protected void onDetachedFromWindow(){stop();super.onDetachedFromWindow();}
    }
}
