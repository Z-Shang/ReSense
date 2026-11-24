package dev.sense.framework.weather;

import android.content.*;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** In-memory/private-cache mirror; IPC and migration always run off the UI thread. */
public final class WeatherShareClient {
    public static final Set<String> KEYS=Set.of("city","latitude","longitude","cities","cityForecasts","currentLocation","noCity","data","cacheKey","status");
    private static final Uri URI=Uri.parse("content://dev.sense.framework.weather/state");
    private final Context context;private final android.content.SharedPreferences prefs;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ContentObserver observer=new ContentObserver(main){@Override public void onChange(boolean self){sync();}};
    private volatile long generation;private volatile boolean active,suspended;
    private ContentProviderClient connection;
    public WeatherShareClient(Context context,android.content.SharedPreferences prefs){this.context=context;this.prefs=prefs;}
    public boolean active(){return active&&!suspended;}
    public void connect(){
        if(suspended||context.getPackageName().equals("dev.sense.framework.services"))return;
        boolean available=context.getPackageManager().checkSignatures(context.getPackageName(),"dev.sense.framework.services")==PackageManager.SIGNATURE_MATCH
                &&context.getPackageManager().resolveContentProvider("dev.sense.framework.weather",0)!=null;
        if(!available){if(active)context.getContentResolver().unregisterContentObserver(observer);if(connection!=null){connection.close();connection=null;}active=false;return;}
        if(active){sync();return;}
        active=true;
        long version=++generation;Map<String,?> legacy=prefs.getAll();
        worker.execute(()->{
            try{
                if(!active())return;
                connection=context.getContentResolver().acquireContentProviderClient(URI);
                if(connection==null)throw new java.io.IOException("Framework weather service unavailable");
                if(!active()){connection.close();connection=null;return;}
                context.getContentResolver().registerContentObserver(URI,false,observer);
                String store=readState().getString("_storeId");
                if(!store.equals(prefs.getString("weatherShareStoreId",""))){
                    JSONObject initial=new JSONObject();for(String key:KEYS)if(!key.equals("cityForecasts")&&legacy.containsKey(key))initial.put(key,legacy.get(key));
                    call("import",initial.toString(),null);
                    Object legacyCaches=legacy.get("cityForecasts");JSONObject caches=new JSONObject(legacyCaches instanceof String value?value:"{}");
                    for(var keys=caches.keys();keys.hasNext();){String key=keys.next();JSONObject cache=caches.optJSONObject(key);if(cache==null)continue;
                        JSONObject chunk=new JSONObject().put("cityForecasts",new JSONObject().put(key,cache));
                        if(chunk.toString().length()<200_000)call("import",chunk.toString(),null);
                    }
                    prefs.edit().putBoolean("weatherShareImportedFrameworkV1",true).putString("weatherShareStoreId",store).apply();
                }
                read(version);
            }catch(Exception error){failed(version);}
        });
    }
    public void command(String method,String arg,boolean force){
        long version=++generation;worker.execute(()->{
            try{Bundle extras=new Bundle();extras.putBoolean("force",force);call(method,arg,extras);read(version);}catch(Exception error){failed(version);}
        });
    }
    private void call(String method,String arg,Bundle extras){context.getContentResolver().call(URI,method,arg,extras);}
    public void sync(){if(!active())return;long version=generation;worker.execute(()->{try{read(version);}catch(Exception error){failed(version);}});}
    private void read(long version)throws Exception{
        if(version!=generation||!active())return;
        JSONObject state=readState();
        main.post(()->{
            if(version!=generation||!active())return;
            var edit=prefs.edit();Map<String,?> old=prefs.getAll();
            for(String key:KEYS){Object value=state.opt(key);if(value==null||value==JSONObject.NULL){if(old.containsKey(key))edit.remove(key);}else if(!Objects.equals(old.get(key),value)){if(value instanceof Boolean b)edit.putBoolean(key,b);else edit.putString(key,value.toString());}}
            edit.apply();
        });
    }
    private JSONObject readState()throws Exception{
        try(var input=context.getContentResolver().openInputStream(URI)){
            if(input==null)throw new java.io.IOException("Missing weather state");byte[] data=input.readNBytes(8*1024*1024+1);
            if(data.length>8*1024*1024)throw new java.io.IOException("Weather cache too large");return new JSONObject(new String(data,java.nio.charset.StandardCharsets.UTF_8));
        }
    }
    private void failed(long version){main.post(()->{if(version==generation&&active())prefs.edit().putString("status","Shared weather unavailable").apply();});}
    public void suspendForTests(boolean value){
        suspended=value;generation++;
        if(value){
            active=false;
            try{worker.submit(()->{context.getContentResolver().unregisterContentObserver(observer);if(connection!=null){connection.close();connection=null;}}).get(10,TimeUnit.SECONDS);}
            catch(Exception error){throw new IllegalStateException(error);}
        }else connect();
    }
}
