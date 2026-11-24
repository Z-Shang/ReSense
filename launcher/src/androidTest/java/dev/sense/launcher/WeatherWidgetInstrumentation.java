package dev.sense.launcher;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.widget.*;
import org.json.*;
import java.time.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Isolated workspace and backed-up local forecast fixture; never writes the framework's weather store. */
final class WeatherWidgetInstrumentation {
    private final Instrumentation test;
    WeatherWidgetInstrumentation(Instrumentation test){this.test=test;}
    private void main(Runnable action){test.runOnMainSync(action);}
    private void await(BooleanSupplier condition,String label){long end=SystemClock.uptimeMillis()+20_000;boolean[] ready={false};while(SystemClock.uptimeMillis()<end){main(()->ready[0]=condition.getAsBoolean());if(ready[0])return;SystemClock.sleep(40);}throw new AssertionError(label);}
    private JSONObject forecast(int code)throws Exception {
        JSONObject current=new JSONObject().put("temperature_2m",23).put("weather_code",code).put("is_day",1);
        JSONArray times=new JSONArray(),high=new JSONArray(),low=new JSONArray(),codes=new JSONArray();
        long today=LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        for(int i=0;i<6;i++){times.put(today+i*86400);high.put(24+i);low.put(18+i);codes.put(new int[]{2,0,3,61,71,95}[i]);}
        return new JSONObject().put("current",current).put("daily",new JSONObject().put("time",times).put("temperature_2m_max",high).put("temperature_2m_min",low).put("weather_code",codes)).put("timezone","UTC").put("fetched",System.currentTimeMillis());
    }
    void run(){
        Bundle report=new Bundle();int status=Activity.RESULT_OK;MainActivity activity=null;
        var context=test.getTargetContext();var repo=WeatherRepository.get(context);repo.localStorageForTests(true);
        var prefs=repo.prefs;Map<String,?> backup=prefs.getAll();String isolated="weather-widget-tests";
        int[] ids=new AppWidgetHost(context,1024).getAppWidgetIds();
        try {
            prefs.edit().putBoolean("noCity",false).putString("city","London").putString("latitude","51.5").putString("longitude","-0.12")
                .putString("cacheKey","51.5,-0.12").putString("data",forecast(2).toString()).putString("status","Saved forecast").commit();
            activity=(MainActivity)test.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            MainActivity screen=activity;SenseWeatherView[] widgets=new SenseWeatherView[3];
            main(()->{LinearLayout row=new LinearLayout(screen);for(int i=0;i<3;i++){widgets[i]=new SenseWeatherView(screen,new String[]{"2x1","2x2","4x4"}[i]);row.addView(widgets[i],new LinearLayout.LayoutParams(0,-1,1));}screen.setContentView(row);});
            await(()->Arrays.stream(widgets).allMatch(SenseWeatherView::readyForTests),"Widget assets did not load: "+Arrays.toString(Arrays.stream(widgets).map(SenseWeatherView::errorForTests).toArray()));
            main(()->{
                Bitmap sheet=Bitmap.createBitmap(1110,580,Bitmap.Config.ARGB_8888);Canvas out=new Canvas(sheet);out.drawColor(0xff20252b);
                Paint caption=new Paint(Paint.ANTI_ALIAS_FLAG);caption.setColor(Color.WHITE);caption.setTextSize(18);
                for(int i=0;i<3;i++){
                    SenseWeatherView widget=widgets[i];widget.restForTests();int width=350,height=i==0?192:i==1?464:494;
                    widget.measure(android.view.View.MeasureSpec.makeMeasureSpec(width,1073741824),android.view.View.MeasureSpec.makeMeasureSpec(height,1073741824));widget.layout(0,0,width,height);
                    Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);widget.draw(new Canvas(bitmap));
                    int visible=0,bright=0;for(int y=0;y<height;y+=3)for(int x=0;x<width;x+=3){int pixel=bitmap.getPixel(x,y);if(Color.alpha(pixel)>10)visible++;if(Color.alpha(pixel)>50&&Color.red(pixel)>160&&Color.green(pixel)>160)bright++;}
                    if(visible<400||bright<60)throw new AssertionError("Blank panel/text/art in "+widget.style+": "+visible+"/"+bright);
                    out.drawText(widget.style,i*370+12,23,caption);out.drawBitmap(bitmap,i*370+10,40,null);bitmap.recycle();
                    if(!widget.getContentDescription().toString().contains("23°"))throw new AssertionError("Shared forecast not bound");
                }
                try(var output=context.openFileOutput("weather-widget-layouts.png",0)){sheet.compress(Bitmap.CompressFormat.PNG,100,output);}catch(Exception error){throw new RuntimeException(error);}sheet.recycle();
            });
            prefs.edit().putString("data",forecast(71).toString()).commit();
            await(()->Arrays.stream(widgets).allMatch(v->v.readyForTests()&&v.conditionCodeForTests()==22),"Condition change not observed");
            prefs.edit().putString("data",forecast(999).toString()).commit();
            await(()->Arrays.stream(widgets).allMatch(v->v.readyForTests()&&v.conditionCodeForTests()==0),"Unknown weather retained old animation");
            var families=WidgetPicker.families(context,List.of());var family=families.stream().filter(f->f.key().equals("sense:weather")).findFirst().orElseThrow();
            if(family.styles().size()!=3||!family.styles().get(0).weatherStyle().equals("2x1")||!family.styles().get(2).weatherStyle().equals("4x4"))throw new AssertionError("Weather picker styles");
            Workspace[] workspace={null};main(()->{
                var saved=screen.getSharedPreferences(isolated,0);saved.edit().clear().putString("items","[]").putInt("columns",4).putInt("rows",6).putInt("pages",1).commit();
                workspace[0]=new Workspace(screen,new SenseWidgetHost(screen,2048),saved);screen.setContentView(workspace[0]);
                for(String size:List.of("2x1","2x2","4x4"))workspace[0].addWeather(size);
                if(workspace[0].items.length()!=3)throw new AssertionError("Placement missing");
                Workspace restored=new Workspace(screen,new SenseWidgetHost(screen,2048),saved);
                if(restored.items.length()!=3)throw new AssertionError("Persistence missing");
                for(int i=0;i<3;i++)if(!"weather".equals(restored.items.optJSONObject(i).optString("type")))throw new AssertionError("Widget type lost");
                JSONObject item=workspace[0].items.optJSONObject(2);workspace[0].beginResize(item);workspace[0].finishResize();workspace[0].remove(item);
                if(workspace[0].items.length()!=2)throw new AssertionError("Remove failed");
            });
            if(!Arrays.equals(ids,new AppWidgetHost(context,1024).getAppWidgetIds()))throw new AssertionError("Built-in weather allocated provider IDs");
            report.putString("result","PASS original 3 styles, shared forecast and condition updates, unknown fallback, nonblank Canvas snapshots, picker, placement/persistence, resize/remove and passive widget IDs");
        }catch(Throwable failure){status=Activity.RESULT_CANCELED;report.putString("error",android.util.Log.getStackTraceString(failure));}
        finally {
            if(activity!=null){MainActivity screen=activity;main(screen::finish);}test.waitForIdleSync();
            var edit=prefs.edit().clear();for(var entry:backup.entrySet()){Object value=entry.getValue();if(value instanceof String s)edit.putString(entry.getKey(),s);else if(value instanceof Boolean b)edit.putBoolean(entry.getKey(),b);else if(value instanceof Integer i)edit.putInt(entry.getKey(),i);else if(value instanceof Long l)edit.putLong(entry.getKey(),l);else if(value instanceof Float f)edit.putFloat(entry.getKey(),f);else if(value instanceof Set<?> set){@SuppressWarnings("unchecked")var strings=(Set<String>)set;edit.putStringSet(entry.getKey(),strings);}}edit.commit();repo.localStorageForTests(false);context.deleteSharedPreferences(isolated);
        }
        test.finish(status,report);
    }
}
