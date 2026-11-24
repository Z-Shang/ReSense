package dev.sense.framework.weather;

import dev.sense.framework.weather.WeatherShareClient;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import org.json.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/** Saved cities, per-location last-good forecasts, and HTTPS requests off the UI thread. */
final class WeatherRepository {
    interface SearchResult { void complete(JSONArray results,String error); }
    private static WeatherRepository instance;
    static synchronized WeatherRepository get(Context context) {
        if(instance==null)instance=new WeatherRepository(context.getApplicationContext());
        return instance;
    }
    final SharedPreferences prefs;
    private final WeatherShareClient sharing;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ExecutorService searches=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private boolean fetching;
    private long lastAttempt;
    private String attemptKey="";
    private WeatherRepository(Context context){prefs=context.getSharedPreferences("weather",0);sharing=new WeatherShareClient(context,prefs);sharing.connect();}
    void connectSharedWeather(){sharing.connect();}
    boolean sharingWeather(){return sharing.active();}
    void localStorageForTests(boolean local){sharing.suspendForTests(local);}
    String city(){return prefs.getBoolean("noCity",false)?"Choose a city":prefs.getString("city","Sydney");}
    private String key(){if(prefs.getBoolean("noCity",false))return "";return prefs.getString("latitude","-33.8688")+","+prefs.getString("longitude","151.2093");}
    Snapshot snapshot() {
        if(!key().equals(prefs.getString("cacheKey","")))return null;
        try{return new Snapshot(new JSONObject(prefs.getString("data","")));}
        catch(JSONException e){return null;}
    }
    JSONObject forecast(){try{return snapshot()==null?null:new JSONObject(prefs.getString("data",""));}catch(JSONException e){return null;}}
    String status(){return prefs.getString("status", "Loading weather…");}
    record City(String name,double latitude,double longitude,String region) {
        String key(){return Double.toString(latitude)+","+Double.toString(longitude);}
        String label(){return region.isBlank()?name:name+", "+region;}
        JSONObject json()throws JSONException{return new JSONObject().put("name",name).put("latitude",latitude).put("longitude",longitude).put("region",region);}
        static City from(JSONObject location)throws JSONException{
            double lat=location.getDouble("latitude"),lon=location.getDouble("longitude");String name=location.getString("name").trim();
            if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180 || name.isEmpty() || name.length()>200)throw new JSONException("Invalid location");
            String region=location.optString("region","");
            if(region.isBlank()){
                String admin=location.optString("admin1",""),country=location.optString("country","");
                region=admin.isBlank()?country:country.isBlank()||country.equals(admin)?admin:admin+", "+country;
            }
            return new City(name,lat==0?0:lat,lon==0?0:lon,region);
        }
    }
    List<City> cities(){
        LinkedHashMap<String,City> locations=new LinkedHashMap<>();
        try{
            JSONArray saved=new JSONArray(prefs.getString("cities","[]"));
            for(int i=0;i<saved.length();i++)try{City city=City.from(saved.getJSONObject(i));locations.put(city.key(),city);}catch(JSONException ignored){}
        }catch(JSONException ignored){}
        if(!prefs.getBoolean("noCity",false) && !isCurrentLocation())try{
            City current=City.from(new JSONObject().put("name",city()).put("latitude",Double.parseDouble(prefs.getString("latitude","-33.8688"))).put("longitude",Double.parseDouble(prefs.getString("longitude","151.2093"))));
            locations.putIfAbsent(current.key(),current);
        }catch(JSONException|NumberFormatException ignored){}
        return List.copyOf(locations.values());
    }
    String selectedCityKey(){return key();}
    boolean isCurrentLocation(){return prefs.getBoolean("currentLocation",false);}
    private JSONObject caches(){try{return new JSONObject(prefs.getString("cityForecasts","{}"));}catch(JSONException ignored){return new JSONObject();}}
    private JSONObject cached(JSONObject caches,String key){
        JSONObject value=caches.optJSONObject(key);if(value==null)return null;
        try{new Snapshot(value);return value;}catch(JSONException ignored){return null;}
    }
    void select(JSONObject location)throws JSONException{select(City.from(location));}
    void select(City location)throws JSONException{
        select(location,false);
    }
    void selectCurrentLocation(double latitude,double longitude)throws JSONException{
        select(new City("Current location",latitude,longitude,""),true);
    }
    private void select(City location,boolean currentLocation)throws JSONException{
        // Validate even records constructed by another caller, then retain the
        // current legacy cache before moving to a different coordinate key.
        City target=City.from(location.json());List<City> saved=new ArrayList<>(cities());boolean exists=false;
        for(int i=0;!currentLocation && i<saved.size();i++)if(saved.get(i).key().equals(target.key())){
            if(target.region().isBlank())target=new City(target.name(),target.latitude(),target.longitude(),saved.get(i).region());
            saved.set(i,target);exists=true;break;
        }
        if(!currentLocation && !exists)saved.add(target);
        JSONObject caches=caches(),current=forecast();if(current!=null)caches.put(key(),current);
        if(isCurrentLocation() && !key().equals(target.key()) && saved.stream().noneMatch(city->city.key().equals(key())))caches.remove(key());
        JSONObject restored=cached(caches,target.key());JSONArray list=new JSONArray();for(City city:saved)list.put(city.json());
        var edit=prefs.edit().putBoolean("noCity",false).putBoolean("currentLocation",currentLocation).putString("city",target.name()).putString("latitude",Double.toString(target.latitude())).putString("longitude",Double.toString(target.longitude()))
                .putString("cities",list.toString()).putString("cityForecasts",caches.toString());
        if(restored!=null)edit.putString("data",restored.toString()).putString("cacheKey",target.key()).putString("status","Saved forecast");
        else edit.remove("data").remove("cacheKey").putString("status","Loading weather…");
        edit.apply();if(sharing.active()){sharing.command(currentLocation?"gps":"select",target.json().toString(),false);return;}refresh(restored==null);
    }
    boolean removeCity(String locationKey){
        List<City> saved=new ArrayList<>(cities());int removed=-1;
        for(int i=0;i<saved.size();i++)if(saved.get(i).key().equals(locationKey)){removed=i;break;}
        if(removed<0)return false;
        saved.remove(removed);JSONObject caches=caches();caches.remove(locationKey);
        JSONArray list=new JSONArray();try{for(City city:saved)list.put(city.json());}catch(JSONException impossible){throw new IllegalStateException(impossible);}
        var edit=prefs.edit().putString("cities",list.toString()).putString("cityForecasts",caches.toString());
        boolean selected=!isCurrentLocation() && locationKey.equals(key());
        if(selected){
            if(saved.isEmpty())edit.putBoolean("noCity",true).remove("city").remove("latitude").remove("longitude").remove("data").remove("cacheKey").putString("status","Add a city to see the forecast");
            else{
                City next=saved.get(Math.min(removed,saved.size()-1));JSONObject restored=cached(caches,next.key());
                edit.putBoolean("noCity",false).putString("city",next.name()).putString("latitude",Double.toString(next.latitude())).putString("longitude",Double.toString(next.longitude()));
                if(restored==null)edit.remove("data").remove("cacheKey").putString("status","Loading weather…");
                else edit.putString("data",restored.toString()).putString("cacheKey",next.key()).putString("status","Saved forecast");
            }
        }
        edit.apply();if(sharing.active()){sharing.command("remove",locationKey,false);return true;}if(selected && !saved.isEmpty())refresh(snapshot()==null);return true;
    }
    void refresh(boolean force) {
        sharing.connect();
        if(sharing.active()){sharing.command("refresh",null,force);return;}
        if(prefs.getBoolean("noCity",false))return;
        String locationKey=key();long now=System.currentTimeMillis();
        if(fetching)return;
        Snapshot saved=snapshot();
        if(!force && saved!=null && now>=saved.fetched && now-saved.fetched<30*60_000)return;
        if(!force && locationKey.equals(attemptKey) && now-lastAttempt<2*60_000)return;
        fetching=true;lastAttempt=now;attemptKey=locationKey;
        prefs.edit().putString("status","Updating weather…").apply();
        String[] coordinates=locationKey.split(",");
        worker.execute(()->{
            JSONObject payload=null;String error=null;
            try {
                String url="https://api.open-meteo.com/v1/forecast?latitude="+coordinates[0]+"&longitude="+coordinates[1]
                        +"&current=temperature_2m,weather_code,is_day,relative_humidity_2m,apparent_temperature,wind_speed_10m&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset&hourly=temperature_2m,weather_code,precipitation_probability,is_day"
                        +"&timezone=auto&forecast_days=6&temperature_unit=celsius&wind_speed_unit=kmh&timeformat=unixtime";
                payload=request(url);
                Snapshot.validate(payload);
                payload.put("fetched",System.currentTimeMillis());
            } catch(Exception e){android.util.Log.w("SenseWeather","Forecast refresh failed",e);error="Weather unavailable";}
            JSONObject response=payload;String failure=error;
            main.post(()->finishRefresh(locationKey,response,failure));
        });
    }
    void finishRefresh(String locationKey,JSONObject response,String failure){
        fetching=false;
        if(sharing.active()){sharing.sync();return;}
        if(!locationKey.equals(key()) && cities().stream().noneMatch(city->city.key().equals(locationKey))){refresh(false);return;}
        if(failure==null){
            JSONObject caches=caches();try{caches.put(locationKey,response);}catch(JSONException impossible){throw new IllegalStateException(impossible);}
            prefs.edit().putString("cityForecasts",caches.toString()).apply();
        }
        if(!locationKey.equals(key())){refresh(false);return;}
        if(failure==null)prefs.edit().putString("data",response.toString()).putString("cacheKey",locationKey).putString("status","Updated").apply();
        else prefs.edit().putString("status",failure).apply();
    }
    void search(String name,SearchResult callback) {
        searches.execute(()->{
            JSONArray matches=new JSONArray();String error=null;
            try {
                JSONObject result=request("https://geocoding-api.open-meteo.com/v1/search?count=10&language=en&format=json&name="
                        +URLEncoder.encode(name,StandardCharsets.UTF_8));
                if(result.optJSONArray("results")!=null)matches=result.getJSONArray("results");
            } catch(Exception e){error="Could not search cities. Check your connection.";}
            JSONArray locations=matches;String failure=error;
            main.post(()->callback.complete(locations,failure));
        });
    }
    private static JSONObject request(String url) throws Exception {
        HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();
        connection.setConnectTimeout(10_000);connection.setReadTimeout(15_000);
        connection.setRequestProperty("Accept","application/json");
        try {
            if(connection.getResponseCode()!=200)throw new java.io.IOException("HTTP "+connection.getResponseCode());
            try(var input=connection.getInputStream()) {
                byte[] data=input.readNBytes(1_048_577);
                if(data.length>1_048_576)throw new java.io.IOException("Response too large");
                JSONObject result=new JSONObject(new String(data,StandardCharsets.UTF_8));
                if(result.optBoolean("error"))throw new java.io.IOException("API error");
                return result;
            }
        } finally {connection.disconnect();}
    }
    static final class Snapshot {
        final double temperature,high,low;
        final int code;
        final boolean day;
        final long fetched;
        Snapshot(JSONObject data) throws JSONException {
            validate(data);
            JSONObject current=data.getJSONObject("current"),daily=data.getJSONObject("daily");
            temperature=current.getDouble("temperature_2m");code=current.getInt("weather_code");day=current.getInt("is_day")==1;
            high=daily.getJSONArray("temperature_2m_max").getDouble(0);low=daily.getJSONArray("temperature_2m_min").getDouble(0);
            fetched=data.getLong("fetched");
        }
        static void validate(JSONObject data) throws JSONException {
            JSONObject current=data.getJSONObject("current"),daily=data.getJSONObject("daily");
            double t=current.getDouble("temperature_2m"),hi=daily.getJSONArray("temperature_2m_max").getDouble(0),lo=daily.getJSONArray("temperature_2m_min").getDouble(0);
            if(!Double.isFinite(t)||!Double.isFinite(hi)||!Double.isFinite(lo)||lo>hi)throw new JSONException("Missing temperatures");
            current.getInt("weather_code");int day=current.getInt("is_day");
            if(day!=0&&day!=1)throw new JSONException("Invalid day flag");
        }
    }
}
