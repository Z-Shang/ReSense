package dev.sense.framework.services;

import dev.sense.framework.weather.WeatherShareClient;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** Signature-protected canonical weather store. Only semantic commands cross processes. */
public final class WeatherShareProvider extends ContentProvider {
    static final String AUTHORITY="dev.sense.framework.weather",PERMISSION="dev.sense.framework.permission.WEATHER";
    static final Uri URI=Uri.parse("content://"+AUTHORITY+"/state");
    private WeatherRepository repository;
    private android.content.SharedPreferences.OnSharedPreferenceChangeListener changed;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService streams=Executors.newSingleThreadExecutor();
    @Override public boolean onCreate(){
        repository=WeatherRepository.get(getContext());
        if(!repository.prefs.contains("weatherStoreId"))repository.prefs.edit().putString("weatherStoreId",UUID.randomUUID().toString()).apply();
        changed=(prefs,key)->{if(WeatherShareClient.KEYS.contains(key))getContext().getContentResolver().notifyChange(URI,null);};repository.prefs.registerOnSharedPreferenceChangeListener(changed);
        return true;
    }
    private void enforce(){if(Binder.getCallingUid()!=android.os.Process.myUid())getContext().enforceCallingPermission(PERMISSION,"Sense weather access requires the same signing key");}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws java.io.FileNotFoundException{
        enforce();if(!URI.equals(uri)||!"r".equals(mode))throw new java.io.FileNotFoundException("Read-only weather state");
        try{
            var values=repository.prefs.getAll();JSONObject state=new JSONObject();
            state.put("_storeId",values.get("weatherStoreId"));
            for(String key:WeatherShareClient.KEYS)if(values.containsKey(key))state.put(key,values.get(key));
            byte[] data=state.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createPipe();
            streams.execute(()->{try(var output=new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])){output.write(data);}catch(java.io.IOException ignored){}});
            return pipe[0];
        }catch(java.io.IOException|JSONException e){throw new java.io.FileNotFoundException(e.toString());}
    }
    @Override public Bundle call(String method,String arg,Bundle extras){
        enforce();String caller=getCallingPackage();FutureTask<Bundle> task=new FutureTask<>(()->{
            Bundle result=new Bundle();
            switch(method){
                case "select" -> repository.select(new JSONObject(arg));
                case "gps" -> {var city=WeatherRepository.City.from(new JSONObject(arg));repository.selectCurrentLocation(city.latitude(),city.longitude());}
                case "remove" -> result.putBoolean("removed",repository.removeCity(arg));
                case "refresh" -> repository.refresh(extras!=null&&extras.getBoolean("force"));
                case "import" -> mergeLegacy(new JSONObject(arg),caller);
                default -> throw new IllegalArgumentException("Unknown weather command");
            }
            return result;
        });
        if(Looper.myLooper()==Looper.getMainLooper())task.run();else main.post(task);
        try{return task.get(5,TimeUnit.SECONDS);}catch(Exception error){throw new IllegalStateException("Weather command failed",error);}
    }
    private void mergeLegacy(JSONObject incoming,String caller)throws JSONException{
        var prefs=repository.prefs;JSONObject caches=new JSONObject(prefs.getString("cityForecasts","{}"));
        JSONObject current=repository.forecast();if(current!=null)caches.put(repository.selectedCityKey(),current);
        JSONObject imported=incoming.optJSONObject("cityForecasts");
        if(imported!=null)for(var keys=imported.keys();keys.hasNext();){String key=keys.next();mergeForecast(caches,key,imported.optJSONObject(key));}
        String importedKey=incoming.optString("cacheKey","");
        if(!importedKey.isEmpty())try{mergeForecast(caches,importedKey,new JSONObject(incoming.optString("data","{}")));}catch(JSONException ignored){}
        LinkedHashMap<String,WeatherRepository.City> cities=new LinkedHashMap<>();if(prefs.contains("city")||prefs.contains("cities"))for(var city:repository.cities())cities.put(city.key(),city);
        JSONArray saved;try{saved=new JSONArray(incoming.optString("cities","[]"));}catch(JSONException ignored){saved=new JSONArray();}
        for(int i=0;i<saved.length();i++)try{var city=WeatherRepository.City.from(saved.getJSONObject(i));cities.put(city.key(),city);}catch(JSONException ignored){}
        boolean fromWeather="dev.sense.weather".equals(caller);
        boolean choose=!prefs.getBoolean("imported:"+caller,false)&&incoming.has("city")&&!incoming.optBoolean("noCity",false)&&(fromWeather||!prefs.getBoolean("weatherSelectionImported",false));
        var edit=prefs.edit();String selected=repository.selectedCityKey();
        if(choose){
            var city=WeatherRepository.City.from(new JSONObject().put("name",incoming.getString("city")).put("latitude",Double.parseDouble(incoming.getString("latitude"))).put("longitude",Double.parseDouble(incoming.getString("longitude"))));
            selected=city.key();boolean gps=incoming.optBoolean("currentLocation",false);if(!gps)cities.putIfAbsent(selected,city);
            edit.putString("city",city.name()).putString("latitude",Double.toString(city.latitude())).putString("longitude",Double.toString(city.longitude())).putBoolean("currentLocation",gps).putBoolean("noCity",false).putBoolean("imported:"+caller,true);
            if(fromWeather)edit.putBoolean("weatherSelectionImported",true);
        }
        JSONArray list=new JSONArray();for(var city:cities.values())list.put(city.json());
        edit.putString("cities",list.toString()).putString("cityForecasts",caches.toString());
        JSONObject forecast=caches.optJSONObject(selected);
        if(forecast!=null)edit.putString("data",forecast.toString()).putString("cacheKey",selected).putString("status","Saved forecast");
        else if(choose)edit.remove("data").remove("cacheKey").putString("status","Loading weather…");
        edit.apply();
    }
    private static void mergeForecast(JSONObject caches,String key,JSONObject data)throws JSONException{
        if(data==null)return;try{new WeatherRepository.Snapshot(data);}catch(JSONException invalid){return;}
        JSONObject old=caches.optJSONObject(key);if(old==null||data.optLong("fetched")>old.optLong("fetched"))caches.put(key,data);
    }
    @Override public String getType(Uri uri){return "application/json";}
    @Override public Cursor query(Uri u,String[] p,String s,String[] a,String o){throw new UnsupportedOperationException();}
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
    @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
}
