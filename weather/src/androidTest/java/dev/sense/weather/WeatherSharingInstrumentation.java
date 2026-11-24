package dev.sense.weather;

import android.app.*;
import android.net.Uri;
import android.os.*;
import org.json.*;
import java.util.function.BooleanSupplier;

/** Real cross-process provider/client checks; host runner backs up and restores both stores. */
final class WeatherSharingInstrumentation {
    private final Instrumentation test;
    private final Uri uri=Uri.parse("content://dev.sense.framework.weather/state");
    WeatherSharingInstrumentation(Instrumentation test){this.test=test;}
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private JSONObject state(){try(var in=test.getTargetContext().getContentResolver().openInputStream(uri)){return new JSONObject(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}catch(Exception e){throw new IllegalStateException(e);}}
    private void command(String method,String value){test.getTargetContext().getContentResolver().call(uri,method,value,new Bundle());}
    private void await(BooleanSupplier condition)throws Exception{long end=SystemClock.uptimeMillis()+8000;while(SystemClock.uptimeMillis()<end){if(condition.getAsBoolean())return;Thread.sleep(30);}throw new AssertionError("Shared weather update timed out");}
    void run(){Bundle result=new Bundle();try{
        var repo=WeatherRepository.get(test.getTargetContext());repo.connectSharedWeather();check(repo.sharingWeather(),"Shared weather provider unavailable");
        await(()->!repo.prefs.getString("weatherShareStoreId","").isEmpty() && repo.prefs.getString("weatherShareStoreId","").equals(state().optString("_storeId")));
        var a=new WeatherRepository.City("Shared weather test A",12.3456,45.6789,"");var b=new WeatherRepository.City("Shared weather test B",23.4567,56.7891,"");
        JSONObject caches=new JSONObject().put(a.key(),forecast(20)).put(b.key(),forecast(17));
        JSONObject imported=new JSONObject().put("cities",new JSONArray().put(a.json()).put(b.json()).toString()).put("cityForecasts",caches);
        command("import",imported.toString());command("select",a.json().toString());
        await(()->a.key().equals(repo.selectedCityKey())&&repo.snapshot()!=null&&repo.snapshot().temperature==20);
        test.runOnMainSync(()->{try{repo.select(b);}catch(JSONException e){throw new IllegalStateException(e);}});
        await(()->b.key().equals(state().optString("latitude")+","+state().optString("longitude")));
        await(()->repo.snapshot()!=null&&repo.snapshot().temperature==17);
        test.runOnMainSync(()->{try{repo.selectCurrentLocation(b.latitude(),b.longitude());}catch(JSONException e){throw new IllegalStateException(e);}});
        await(()->state().optBoolean("currentLocation"));
        command("remove",a.key());await(()->repo.cities().stream().noneMatch(c->c.key().equals(a.key())));
        check(repo.snapshot()!=null&&repo.snapshot().temperature==17,"Removing other city changed shared forecast");
        long fetched=repo.snapshot().fetched;test.runOnMainSync(()->repo.refresh(false));Thread.sleep(250);
        check(repo.snapshot().fetched==fetched,"Fresh shared cache unexpectedly replaced");
        result.putString("result","PASS: real cross-process migration, owner-to-weather observation, weather-to-owner city/GPS selection, shared cache, deletion and fresh-cache refresh");test.finish(Activity.RESULT_OK,result);
    }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));test.finish(Activity.RESULT_CANCELED,result);}}
    private static JSONObject forecast(int temperature)throws JSONException{
        long now=System.currentTimeMillis()/1000;JSONObject daily=new JSONObject();
        for(String key:new String[]{"time","weather_code","temperature_2m_max","temperature_2m_min"})daily.put(key,new JSONArray());
        for(int i=0;i<6;i++){daily.getJSONArray("time").put(now+i*86400);daily.getJSONArray("weather_code").put(2);daily.getJSONArray("temperature_2m_max").put(temperature+5);daily.getJSONArray("temperature_2m_min").put(temperature-3);}
        return new JSONObject().put("current",new JSONObject().put("temperature_2m",temperature).put("weather_code",2).put("is_day",1)).put("daily",daily).put("timezone","UTC").put("fetched",System.currentTimeMillis());
    }
}
