package dev.sense.launcher;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.RemoteViews;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Exercise RemoteViews/PendingIntent editor returns without changing real widget data. */
final class WidgetWeatherReturnInstrumentation {
    private final Instrumentation test;
    WidgetWeatherReturnInstrumentation(Instrumentation test){this.test=test;}
    void run(){
        Context context=test.getTargetContext();Bundle result=new Bundle();int status=Activity.RESULT_OK;
        var effects=context.getSharedPreferences(FullscreenWeather.PREFERENCES,0);var effectsBackup=effects.getAll();
        var weather=context.getSharedPreferences("weather",0);var weatherBackup=weather.getAll();
        WeatherRepository.get(context).localStorageForTests(true);
        MainActivity[] home={null};Activity[] editor={null};
        try{
            String key=weather.getString("latitude","-33.8688")+","+weather.getString("longitude","151.2093");
            weather.edit().putString("cacheKey",key).putString("status","Updated").putString("data",
                    "{\"current\":{\"temperature_2m\":18,\"weather_code\":61,\"is_day\":1},\"daily\":{\"temperature_2m_max\":[21],\"temperature_2m_min\":[14]},\"fetched\":"+System.currentTimeMillis()+"}").commit();
            effects.edit().putBoolean("home",true).putBoolean("unlock",true).commit();
            home[0]=(MainActivity)test.startActivitySync(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .setClass(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            await(()->home[0].hasWindowFocus(),"Initial home focus");
            await(()->home[0].fullscreenWeather().overlay()!=null,"Initial home animation");
            main(()->home[0].fullscreenWeather().stop());SystemClock.sleep(2100);

            editor[0]=openWidgetEditor(home[0]);
            await(()->editor[0].hasWindowFocus(),"Widget editor focus");
            main(()->editor[0].finish());await(()->home[0].hasWindowFocus(),"Widget return focus");
            assertQuiet(home,"Returning from RemoteViews widget editor");

            main(()->home[0].onNewIntent(new Intent(Intent.ACTION_VIEW).setClass(context,MainActivity.class)));
            assertQuiet(home,"Non-home intent");

            var recreated=test.addMonitor(MainActivity.class.getName(),null,false);
            try{
                main(()->home[0].recreate());
                Activity next=test.waitForMonitorWithTimeout(recreated,8000);
                if(!(next instanceof MainActivity))throw new AssertionError("Launcher did not recreate");
                home[0]=(MainActivity)next;
            }finally{test.removeMonitor(recreated);}
            await(()->home[0].hasWindowFocus(),"Restored home focus");
            assertQuiet(home,"Saved-state launcher recreation");

            editor[0]=openWidgetEditor(home[0]);await(()->editor[0].hasWindowFocus(),"Second widget editor focus");
            main(()->editor[0].startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .setClass(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
            await(()->home[0].hasWindowFocus(),"Explicit HOME focus");
            await(()->home[0].fullscreenWeather().overlay()!=null,"Explicit HOME still animates");
            result.putString("result","Initial home and explicit HOME animate; RemoteViews PendingIntent return, non-HOME intent and saved-state recreation stay quiet");
        }catch(Throwable error){status=Activity.RESULT_CANCELED;result.putString("error",android.util.Log.getStackTraceString(error));}
        finally{
            main(()->{
                if(editor[0]!=null&&!editor[0].isFinishing())editor[0].finish();
                if(home[0]!=null)home[0].fullscreenWeather().stop();
                restore(effects,effectsBackup);restore(weather,weatherBackup);
            });
            WeatherRepository.get(context).localStorageForTests(false);
        }
        test.finish(status,result);
    }
    private Activity openWidgetEditor(MainActivity home){
        var monitor=test.addMonitor(LauncherSettingsActivity.class.getName(),null,false);
        try{
            main(()->{
                // A real RemoteViews click launches the PendingIntent, as hosted third-party widgets do.
                // Existing launcher settings stands in for an editor Activity; no settings are changed.
                PendingIntent action=PendingIntent.getActivity(home,903,new Intent(home,LauncherSettingsActivity.class),
                        PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
                RemoteViews views=new RemoteViews("android",android.R.layout.simple_list_item_1);
                views.setTextViewText(android.R.id.text1,"Widget editor fixture");views.setOnClickPendingIntent(android.R.id.text1,action);
                views.apply(home,null).findViewById(android.R.id.text1).performClick();
            });
            Activity activity=test.waitForMonitorWithTimeout(monitor,8000);
            if(activity==null)throw new AssertionError("Widget PendingIntent did not open editor");
            return activity;
        }finally{test.removeMonitor(monitor);}
    }
    private void assertQuiet(MainActivity[] home,String reason){
        long until=SystemClock.uptimeMillis()+1500;
        while(SystemClock.uptimeMillis()<until){
            main(()->{if(home[0].fullscreenWeather().overlay()!=null)throw new AssertionError(reason+" started weather");});
            SystemClock.sleep(30);
        }
    }
    private void await(BooleanSupplier ready,String reason){
        long until=SystemClock.uptimeMillis()+8000;
        while(SystemClock.uptimeMillis()<until){boolean[] value={false};main(()->value[0]=ready.getAsBoolean());if(value[0])return;SystemClock.sleep(30);}
        throw new AssertionError(reason+" timed out");
    }
    private void main(Runnable action){Throwable[] error={null};test.runOnMainSync(()->{try{action.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError(error[0]);}
    private static void restore(SharedPreferences prefs,Map<String,?> values){
        var edit=prefs.edit().clear();for(var e:values.entrySet()){
            Object v=e.getValue();String k=e.getKey();if(v instanceof Boolean b)edit.putBoolean(k,b);else if(v instanceof String s)edit.putString(k,s);
            else if(v instanceof Integer i)edit.putInt(k,i);else if(v instanceof Long l)edit.putLong(k,l);else if(v instanceof Float f)edit.putFloat(k,f);
        }edit.commit();
    }
}
