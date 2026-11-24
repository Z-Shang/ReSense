package dev.sense.weather;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Real activities, generated original art and deterministic Open-Meteo-shaped data. */
public final class WeatherInstrumentation extends Instrumentation {
    private boolean sharingSuite;
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);sharingSuite=arguments!=null&&"weather_share".equals(arguments.getString("suite"));start();}
    @Override public void onStart(){
        if(sharingSuite){new WeatherSharingInstrumentation(this).run();return;}
        Bundle result=new Bundle();int status=Activity.RESULT_OK;WeatherActivity[] activity={null};
        WeatherRepository.get(getTargetContext()).localStorageForTests(true);
        var prefs=getTargetContext().getSharedPreferences("weather",0);Map<String,?> backup=prefs.getAll();
        try{
            try(var output=getTargetContext().openFileOutput("weather-test-backup.json",0)){output.write(new JSONObject(backup).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
            JSONObject data=fixture();
            testPeriods();
            if(WeatherActivity.needsForecast(data,2) || WeatherActivity.needsForecast(data,1))throw new AssertionError("Complete API-shaped forecast rejected");
            JSONObject legacy=new JSONObject().put("daily",new JSONObject().put("time",new JSONArray().put(1)));
            if(!WeatherActivity.needsForecast(legacy,2) || !WeatherActivity.needsForecast(legacy,1))throw new AssertionError("Legacy cache did not request a full forecast");
            prefs.edit().clear().putString("city","Test Tokyo").putString("latitude","35.0").putString("longitude","139.0")
                    .putString("data",data.toString()).putString("cacheKey","35.0,139.0").putString("status","Updated").commit();
            activity[0]=(WeatherActivity)startActivitySync(new Intent(getTargetContext(),WeatherActivity.class).putExtra("city","Test Tokyo").putExtra("latitude",35.0).putExtra("longitude",139.0).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            waitForIdleSync();
            main(()->{
                if(activity[0].getIntent().hasExtra("latitude"))throw new AssertionError("Launch city would be reapplied on rotation");
                assertText(activity[0],"Test Tokyo");assertText(activity[0],"21°");assertText(activity[0],"Partly cloudy");
                assertText(activity[0],"Today");assertText(activity[0],"Tonight");assertText(activity[0],"Tomorrow");
                if(((LinearLayout)activity[0].findViewById(R.id.now_periods)).getChildCount()!=5)throw new AssertionError("Expected three Now cards and two dividers");
                for(int i=0;i<3;i++)if(((TextView)((LinearLayout)activity[0].findViewById(R.id.tabs)).getChildAt(i)).getCompoundDrawables()[1]!=null)throw new AssertionError("Weather tabs must be text-only");
                LinearLayout ribbon=activity[0].findViewById(R.id.provider_bar);
                if(ribbon.getOrientation()!=LinearLayout.HORIZONTAL || ((TextView)activity[0].findViewById(R.id.status)).getLineCount()>1)throw new AssertionError("Provider/time ribbon is not a single row");
                activity[0].findViewById(R.id.fullscreen).performClick();
                if(activity[0].findViewById(R.id.now_periods).getVisibility()!=View.GONE || ribbon.getVisibility()!=View.GONE || activity[0].findViewById(R.id.tabs).getVisibility()!=View.GONE)throw new AssertionError("Full screen failed to hide forecast chrome");
                activity[0].findViewById(R.id.fullscreen).performClick();
                LinearLayout tabs=activity[0].findViewById(R.id.tabs);tabs.getChildAt(1).performClick();
                HourlyForecastView chart=activity[0].findViewById(R.id.hourly_chart);
                if(chart.sampleCount()!=24 || chart.temperatureAt(0)!=21)throw new AssertionError("Hourly chart data incorrect");
                if(activity[0].findViewById(R.id.current_scene).isShown())throw new AssertionError("Hourly forecast hidden below current panel");
                tabs=activity[0].findViewById(R.id.tabs);tabs.getChildAt(2).performClick();
                if(!text(activity[0].findViewById(R.id.attribution),"Open-Meteo"))throw new AssertionError("Daily source not identified");
                LinearLayout cards=activity[0].findViewById(R.id.now_periods);
                if(cards.getChildCount()!=9 || !activity[0].findViewById(R.id.current_scene).isShown())throw new AssertionError("Expected video and five daily cards");
                if(!text(cards,"26°/17°"))throw new AssertionError("Tomorrow range missing");
                // Compiled HTC nine-patch chunks must survive resource generation.
                if(!(activity[0].getDrawable(R.drawable.wx_common_titlebar_center_rest) instanceof android.graphics.drawable.NinePatchDrawable))throw new AssertionError("Original header lost nine-patch metadata");
                if(activity[0].getDrawable(R.drawable.wx_condition_03)==null)throw new AssertionError("Recovered forecast icon missing");
                ((LinearLayout)activity[0].findViewById(R.id.tabs)).getChildAt(0).performClick();
            });
            main(()->((LinearLayout)activity[0].findViewById(R.id.tabs)).getChildAt(1).performClick());
            waitForIdleSync();
            SystemClock.sleep(150);
            main(()->{
                HourlyForecastView chart=activity[0].findViewById(R.id.hourly_chart);
                if(chart.getChildAt(0).getHeight()<chart.getHeight()*.8)throw new AssertionError("Chart plot collapsed vertically");
                var bitmap=android.graphics.Bitmap.createBitmap(180,240,android.graphics.Bitmap.Config.ARGB_8888);
                var canvas=new android.graphics.Canvas(bitmap);canvas.scale(180f/chart.getWidth(),240f/chart.getHeight());chart.draw(canvas);
                java.util.Set<Integer> colors=new java.util.HashSet<>();for(int y=0;y<240;y+=8)for(int x=0;x<180;x+=8)colors.add(bitmap.getPixel(x,y));bitmap.recycle();
                if(colors.size()<5)throw new AssertionError("Hourly chart rendered blank");
            });
            var hourlyShot=getUiAutomation().takeScreenshot();
            try(var output=getTargetContext().openFileOutput("sense-weather-hourly-test.png",0)){hourlyShot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);}hourlyShot.recycle();
            main(()->((LinearLayout)activity[0].findViewById(R.id.tabs)).getChildAt(0).performClick());
            SystemClock.sleep(1200);waitForIdleSync();
            main(()->{
                WeatherSceneView video=activity[0].findViewById(R.id.scene);
                if(!"partly_sunny".equals(video.currentMovie()) || !video.playing())throw new AssertionError("Original full-scene video did not play");
                if(!"rain_night".equals(WeatherSceneView.movie(61,false)) || !"snow_day".equals(WeatherSceneView.movie(85,true)) || WeatherSceneView.movie(-1,true)!=null)throw new AssertionError("Movie mapping/fallback incorrect");
            });
            var shot=getUiAutomation().takeScreenshot();
            try(var output=getTargetContext().openFileOutput("sense-weather-test.png",0)){shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);}shot.recycle();
            main(()->{
                // A failed refresh keeps the last good forecast on screen.
                prefs.edit().putString("status","Weather unavailable").apply();
            });
            waitForIdleSync();
            main(()->{
                assertText(activity[0],"21°");
                if(!text(activity[0].findViewById(R.id.status),"Offline"))throw new AssertionError("Offline status missing");
                // Malformed optional forecast data cannot fabricate sunny conditions.
                try{
                    JSONObject partial=fixture();partial.getJSONObject("daily").remove("weather_code");
                    prefs.edit().putString("data",partial.toString()).apply();
                    ((LinearLayout)activity[0].findViewById(R.id.tabs)).getChildAt(2).performClick();
                    View card=((LinearLayout)activity[0].findViewById(R.id.now_periods)).getChildAt(0);
                    if(!card.getContentDescription().toString().contains("Conditions unavailable"))throw new AssertionError("Missing forecast code fabricated a condition");
                }catch(JSONException e){throw new AssertionError(e);}
            });
            main(()->{
                try{
                    JSONObject tokyo=fixture(),london=fixture();london.getJSONObject("current").put("temperature_2m",8).put("weather_code",61).put("is_day",0);london.put("timezone","Europe/London");
                    JSONObject tokyoCity=new JSONObject().put("name","Test Tokyo").put("latitude",35.0).put("longitude",139.0);
                    JSONObject londonCity=new JSONObject().put("name","Test London").put("latitude",51.5).put("longitude",-.12).put("country","United Kingdom");
                    JSONArray cities=new JSONArray().put(tokyoCity).put(londonCity).put(londonCity).put(new JSONObject().put("name","Invalid"));
                    prefs.edit().putString("cities",cities.toString()).putString("data",tokyo.toString()).putString("cacheKey","35.0,139.0")
                            .putString("cityForecasts",new JSONObject().put("51.5,-0.12",london).toString()).apply();
                    var repository=WeatherRepository.get(getTargetContext());
                    if(repository.cities().size()!=2)throw new AssertionError("Saved city deduplication/validation failed");
                    activity[0].findViewById(R.id.city).performClick();
                }catch(JSONException e){throw new AssertionError(e);}
            });waitForIdleSync();
            main(()->{
                var popup=activity[0].cityMenu();if(popup==null || !popup.isShowing())throw new AssertionError("Title did not open dropdown");
                View current=popup.getListView().getRootView().findViewWithTag("current-location");
                if(current==null || current.findViewById(R.id.remove_city).getVisibility()==View.VISIBLE || current.findViewById(R.id.remove_city).isEnabled())throw new AssertionError("Pinned GPS item missing or removable");
                var list=popup.getListView();if(list.getAdapter().getCount()!=3 || list.getCheckedItemPosition()!=0)throw new AssertionError("City dropdown choices/selection incorrect");
                list.performItemClick(null,1,1);assertText(activity[0],"Test London");
                if(WeatherRepository.get(getTargetContext()).snapshot().temperature!=8)throw new AssertionError("London did not restore its own forecast");
                swipe(activity[0],activity[0].findViewById(R.id.city),0,-180);
                assertText(activity[0],"Test Tokyo");
                if(WeatherRepository.get(getTargetContext()).snapshot().temperature!=21)throw new AssertionError("Tokyo cache was lost during switch");
                swipe(activity[0],activity[0].findViewById(R.id.city),0,180);assertText(activity[0],"Test London");
                if(!((LinearLayout)activity[0].findViewById(R.id.tabs)).getChildAt(2).isSelected())throw new AssertionError("City switch changed forecast tab");
                try{
                    JSONObject lateTokyo=fixture();lateTokyo.getJSONObject("current").put("temperature_2m",23);
                    var repository=WeatherRepository.get(getTargetContext());repository.finishRefresh("35.0,139.0",lateTokyo,null);
                    if(!"Test London".equals(repository.city()) || repository.snapshot().temperature!=8)throw new AssertionError("Late Tokyo response overwrote London");
                    repository.select(repository.cities().get(0));if(repository.snapshot().temperature!=23)throw new AssertionError("Late response was not saved for Tokyo");
                    repository.select(repository.cities().get(1));
                }catch(JSONException e){throw new AssertionError(e);}
                ((LinearLayout)activity[0].findViewById(R.id.tabs)).getChildAt(1).performClick();
            });waitForIdleSync();SystemClock.sleep(250);
            main(()->{
                HourlyForecastView chart=activity[0].findViewById(R.id.hourly_chart);chart.scrollTo(200,0);
                if(!"Test London".equals(WeatherRepository.get(getTargetContext()).city()))throw new AssertionError("Scrolling chart changed city");
                if(chart.getScrollX()==0)throw new AssertionError("Hourly chart stopped scrolling");
            });
            // The selected city and forecast tab survive recreation.
            main(()->activity[0].recreate());waitForIdleSync();
            WeatherActivity resumed=(WeatherActivity)getCurrentActivity();
            main(()->{
                assertText(resumed,"Test London");
                if(!((LinearLayout)resumed.findViewById(R.id.tabs)).getChildAt(1).isSelected())throw new AssertionError("Selected forecast tab lost on recreation");
                resumed.findViewById(R.id.city).performClick();
            });waitForIdleSync();
            main(()->{
                var popup=resumed.cityMenu();if(popup.getListView().getCheckedItemPosition()!=1)throw new AssertionError("Selected city lost on recreation");
                if(popup.getListView().getAdapter().getCount()!=3)throw new AssertionError("Saved-city/Add list unexpectedly changed");
                popup.getListView().performItemClick(null,2,2);
                try{
                    var field=WeatherActivity.class.getDeclaredField("cityDialog");field.setAccessible(true);
                    if(!(field.get(resumed) instanceof Dialog dialog) || !dialog.isShowing())throw new AssertionError("Dropdown Add city did not open search");
                    dialog.dismiss();
                }catch(ReflectiveOperationException e){throw new AssertionError(e);}
            });
            main(()->resumed.findViewById(R.id.city).performClick());waitForIdleSync();
            main(()->{
                var popup=resumed.cityMenu();View row=popup.getListView().getChildAt(0);
                row.findViewById(R.id.remove_city).performClick();
                if(resumed.cityMenu()!=popup || !popup.isShowing())throw new AssertionError("Removing a city recreated/dismissed the dropdown");
                if(popup.getListView().getAdapter().getCount()!=2)throw new AssertionError("Open dropdown did not update its city/Add rows");
                var repository=WeatherRepository.get(getTargetContext());
                if(repository.cities().size()!=1 || !"Test London".equals(repository.city()) || repository.snapshot().temperature!=8)
                    throw new AssertionError("Removing another city changed the current forecast");
                try{
                    JSONObject late=fixture();repository.finishRefresh("35.0,139.0",late,null);
                    if(new JSONObject(prefs.getString("cityForecasts","{}")).has("35.0,139.0"))throw new AssertionError("Deleted city cache was resurrected by late result");
                    resumed.cityMenu().dismiss();
                    JSONObject caches=new JSONObject(prefs.getString("cityForecasts","{}"));caches.put("35.0,139.0",late);prefs.edit().putString("cityForecasts",caches.toString()).apply();
                    var london=repository.cities().get(0);repository.select(new WeatherRepository.City("Test Tokyo",35,139,""));repository.select(london);
                }catch(JSONException e){throw new AssertionError(e);}
                resumed.findViewById(R.id.city).performClick();
            });waitForIdleSync();
            main(()->{
                resumed.cityMenu().getListView().getChildAt(0).findViewById(R.id.remove_city).performClick();
                var repository=WeatherRepository.get(getTargetContext());
                if(repository.cities().size()!=1 || !"Test Tokyo".equals(repository.city()) || repository.snapshot().temperature!=21)
                    throw new AssertionError("Removing selected city did not select the next cached city");
            });waitForIdleSync();
            main(()->{
                resumed.cityMenu().getListView().getChildAt(0).findViewById(R.id.remove_city).performClick();
                var repository=WeatherRepository.get(getTargetContext());
                if(!repository.cities().isEmpty() || repository.snapshot()!=null)throw new AssertionError("Final city could not be removed");
                assertText(resumed,"Add a city");repository.refresh(true);
                if(repository.status().contains("Updating"))throw new AssertionError("Empty city list started a default-city request");
                try{repository.finishRefresh("35.0,139.0",fixture(),null);}catch(JSONException e){throw new AssertionError(e);}
                if(!repository.cities().isEmpty() || repository.forecast()!=null)throw new AssertionError("Late response restored final removed city");
                resumed.recreate();
            });waitForIdleSync();
            WeatherActivity empty=(WeatherActivity)getCurrentActivity();
            main(()->{
                var repository=WeatherRepository.get(getTargetContext());
                if(!repository.cities().isEmpty())throw new AssertionError("Removed cities reappeared after recreation");
                assertText(empty,"Add a city");
                try{
                    prefs.edit().putString("cityForecasts",new JSONObject().put("35.0,139.0",fixture()).toString()).apply();
                    repository.select(new WeatherRepository.City("Test Tokyo",35,139,""));
                    if(repository.cities().size()!=1 || repository.snapshot().temperature!=21)throw new AssertionError("Could not add city after removing all");
                }catch(JSONException e){throw new AssertionError(e);}
            });
            main(()->{
                try{
                    var repository=WeatherRepository.get(getTargetContext());
                    // GPS can share coordinates with a saved city without owning that row.
                    repository.selectCurrentLocation(35,139);
                    if(!repository.isCurrentLocation() || repository.cities().size()!=1 || repository.snapshot().temperature!=21)throw new AssertionError("GPS selection duplicated or lost saved city/cache");
                    repository.removeCity("35.0,139.0");
                    if(!repository.isCurrentLocation() || !repository.cities().isEmpty() || repository.snapshot()==null)throw new AssertionError("Removing saved city removed current location");
                    repository.finishRefresh("35.0,139.0",fixture(),null);
                    if(repository.snapshot()==null)throw new AssertionError("GPS response rejected with no saved cities");
                    JSONObject caches=new JSONObject(prefs.getString("cityForecasts","{}"));caches.put("36.0,140.0",fixture());prefs.edit().putString("cityForecasts",caches.toString()).apply();
                    repository.selectCurrentLocation(36,140);
                    if(!repository.cities().isEmpty() || !repository.selectedCityKey().equals("36.0,140.0"))throw new AssertionError("Moving GPS location accumulated saved cities");
                    repository.finishRefresh("35.0,139.0",fixture(),null);
                    if(!repository.selectedCityKey().equals("36.0,140.0") || new JSONObject(prefs.getString("cityForecasts","{}")).has("35.0,139.0"))throw new AssertionError("Stale GPS result restored old location");
                    repository.select(new WeatherRepository.City("Saved at same coordinates",36,140,""));
                    if(repository.isCurrentLocation() || repository.cities().size()!=1)throw new AssertionError("Manual selection failed to leave GPS mode");
                    repository.removeCity("36.0,140.0");
                    empty.findViewById(R.id.city).performClick();
                }catch(JSONException e){throw new AssertionError(e);}
            });waitForIdleSync();
            main(()->{
                var popup=empty.cityMenu();
                if(popup==null || !popup.isShowing() || popup.getListView().getAdapter().getCount()!=1 || popup.getListView().getRootView().findViewWithTag("current-location")==null)
                    throw new AssertionError("Pinned GPS entry unavailable with no saved cities");
                popup.dismiss();
            });
            result.putString("result","Weather forecasts, tab switching, city picker/swipes/removal, pinned non-removable GPS entry including empty lists, separate GPS/saved-city state, GPS cache restoration and stale-location isolation passed");
        }catch(Throwable error){status=Activity.RESULT_CANCELED;result.putString("error",android.util.Log.getStackTraceString(error));}
        finally{
            main(()->{for(Activity a:List.copyOf(activities))if(!a.isFinishing())a.finish();});
            var edit=prefs.edit().clear();for(var item:backup.entrySet()){
                Object value=item.getValue();String key=item.getKey();
                if(value instanceof String v)edit.putString(key,v);else if(value instanceof Integer v)edit.putInt(key,v);else if(value instanceof Long v)edit.putLong(key,v);else if(value instanceof Boolean v)edit.putBoolean(key,v);else if(value instanceof Float v)edit.putFloat(key,v);
            }edit.commit();WeatherRepository.get(getTargetContext()).localStorageForTests(false);
        }
        finish(status,result);
    }
    private static void swipe(Activity activity,View target,float dx,float dy){
        int[] location=new int[2];target.getLocationOnScreen(location);float x=location[0]+target.getWidth()/2f,y=location[1]+target.getHeight()/2f;
        long time=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,x,y,0);activity.dispatchTouchEvent(down);down.recycle();
        for(int step=1;step<=6;step++){MotionEvent move=MotionEvent.obtain(time,time+step*16,MotionEvent.ACTION_MOVE,x+dx*step/6,y+dy*step/6,0);activity.dispatchTouchEvent(move);move.recycle();}
        MotionEvent up=MotionEvent.obtain(time,time+112,MotionEvent.ACTION_UP,x+dx,y+dy,0);activity.dispatchTouchEvent(up);up.recycle();
    }
    private void main(Runnable work){
        Throwable[] error={null};runOnMainSync(()->{try{work.run();}catch(Throwable e){error[0]=e;}});
        if(error[0]!=null)throw new AssertionError(error[0]);
    }
    private final List<Activity> activities=new ArrayList<>();
    @Override public void callActivityOnResume(Activity activity){super.callActivityOnResume(activity);if(!activities.contains(activity))activities.add(activity);}
    private Activity getCurrentActivity(){return activities.get(activities.size()-1);}
    private static boolean text(View view,String query){
        if(view instanceof TextView t && t.getText().toString().contains(query))return true;
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)if(text(group.getChildAt(i),query))return true;
        return false;
    }
    private static void assertText(Activity activity,String value){if(!text(activity.getWindow().getDecorView(),value))throw new AssertionError("Missing "+value);}
    private static JSONObject fixture()throws JSONException{
        long now=System.currentTimeMillis()/1000;now-=now%3600;
        JSONObject current=new JSONObject().put("time",now).put("temperature_2m",21).put("weather_code",2).put("is_day",1)
                .put("apparent_temperature",20).put("relative_humidity_2m",65).put("wind_speed_10m",12);
        JSONObject daily=new JSONObject(),hourly=new JSONObject();
        for(String key:new String[]{"time","weather_code","temperature_2m_max","temperature_2m_min"})daily.put(key,new JSONArray());
        for(int i=0;i<6;i++){daily.getJSONArray("time").put(now+i*86400);daily.getJSONArray("weather_code").put(2);daily.getJSONArray("temperature_2m_max").put(25+i);daily.getJSONArray("temperature_2m_min").put(16+i);}
        for(String key:new String[]{"time","temperature_2m","weather_code","precipitation_probability"})hourly.put(key,new JSONArray());
        for(int i=0;i<30;i++){hourly.getJSONArray("time").put(now+i*3600);hourly.getJSONArray("temperature_2m").put(21+i%4);hourly.getJSONArray("weather_code").put(2);hourly.getJSONArray("precipitation_probability").put(25);}
        return new JSONObject().put("current",current).put("daily",daily).put("hourly",hourly).put("timezone","Asia/Tokyo").put("fetched",System.currentTimeMillis());
    }
    private static void testPeriods()throws JSONException{
        var zone=java.time.ZoneId.of("America/Los_Angeles");var today=java.time.LocalDate.of(2026,3,7);
        JSONObject daily=new JSONObject().put("time",new JSONArray().put(today.atStartOfDay(zone).toEpochSecond()).put(today.plusDays(1).atStartOfDay(zone).toEpochSecond()))
                .put("temperature_2m_max",new JSONArray().put(22).put(24)).put("temperature_2m_min",new JSONArray().put(11).put(13)).put("weather_code",new JSONArray().put(2).put(0));
        JSONObject hourly=new JSONObject().put("time",new JSONArray()).put("temperature_2m",new JSONArray()).put("weather_code",new JSONArray());
        long start=today.atTime(18,0).atZone(zone).toEpochSecond(),end=today.plusDays(1).atTime(6,0).atZone(zone).toEpochSecond();int count=0;
        for(long time=start;time<end;time+=3600){hourly.getJSONArray("time").put(time);hourly.getJSONArray("temperature_2m").put(20-count);hourly.getJSONArray("weather_code").put(61);count++;}
        var periods=WeatherPeriods.build(new JSONObject().put("daily",daily).put("hourly",hourly),zone,today);
        if(count!=11 || periods.get(0).temperature()!=22 || periods.get(2).temperature()!=24 || periods.get(1).temperature()!=10 || periods.get(1).code()!=61 || periods.get(1).day())throw new AssertionError("City timezone/DST or Tonight aggregation incorrect");
        var missing=WeatherPeriods.build(null,zone,today);if(Double.isFinite(missing.get(0).temperature()) || missing.get(1).code()!=-1)throw new AssertionError("Missing Now forecast fabricated values");
    }
}
