package dev.sense.launcher;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.view.*;
import dev.sense.framework.fusion.*;
import org.json.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Original standalone Weather widget layouts, bound to the same framework-backed data as the clock. */
final class SenseWeatherView extends View implements Choreographer.FrameCallback {
    final String style;
    private final WeatherRepository weather;
    private final android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener observer=(p,key)->{
        if(key==null||Set.of("city","data","cacheKey","noCity","currentLocation","cities","status").contains(key))update();
    };
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Map<Integer,Bitmap> icons=new HashMap<>();
    private WeatherWidgetAssets.Layout layout;
    private WeatherWidgetAssets.Condition condition;
    private FxSceneInstance layoutState,conditionState;
    private FxPlayback playback;
    private WeatherRepository.Snapshot snapshot;
    private JSONObject forecast;
    private int generation,requestedCode=-1;
    private boolean pending,posted,active;
    private float scale=1,offsetX,offsetY,tapY=Float.NaN;
    private String error;
    SenseWeatherView(Context context,String requestedStyle){
        super(context);style=validStyle(requestedStyle);weather=WeatherRepository.get(context);
        setClickable(true);setFocusable(true);
        setOnClickListener(v->{
            if(style.equals("4x4")&&Float.isFinite(tapY)&&tapY>700){weather.refresh(true);tapY=Float.NaN;return;}
            tapY=Float.NaN;if(context instanceof Activity activity)WeatherSettings.openWeather(activity);
        });
        snapshot=weather.snapshot();forecast=weather.forecast();describe();
    }
    static String validStyle(String value){return "2x1".equals(value)||"2x2".equals(value)||"4x4".equals(value)?value:"2x2";}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();active=true;weather.prefs.registerOnSharedPreferenceChangeListener(observer);update();weather.refresh(false);}
    @Override protected void onDetachedFromWindow(){active=false;generation++;pending=false;weather.prefs.unregisterOnSharedPreferenceChangeListener(observer);cancel();super.onDetachedFromWindow();}
    private void update(){
        snapshot=weather.snapshot();forecast=weather.forecast();describe();
        configureNumberWidth();
        int code=snapshot==null?0:WeatherCodes.htcScene(snapshot.code,snapshot.day);
        if((layout==null||code!=requestedCode)&&!pending)load(code);
        invalidate();schedule();
    }
    private void describe(){setContentDescription("HTC Weather "+style+", "+weather.city()+", "+(snapshot==null?weather.status():degrees(snapshot.temperature)+", "+WeatherCodes.description(snapshot.code)));}
    private void load(int code){
        pending=true;int token=++generation;Context context=getContext().getApplicationContext();
        WeatherWidgetAssets.WORKER.execute(()->{
            try {
                var loaded=WeatherWidgetAssets.layout(context,style);var animation=WeatherWidgetAssets.condition(context,code,!style.equals("4x4"));
                Map<Integer,Bitmap> forecastIcons=new HashMap<>();
                for(int htc:new int[]{1,2,3,7,11,12,15,18,19,22,26,33,34,35}){
                    int id=context.getResources().getIdentifier(String.format(Locale.ROOT,"sense_widget_weather_forecast_%02d",htc),"drawable",context.getPackageName());
                    if(id!=0){var options=new BitmapFactory.Options();options.inScaled=false;forecastIcons.put(htc,BitmapFactory.decodeResource(context.getResources(),id,options));}
                }
                main.post(()->{
                    if(!active||token!=generation)return;pending=false;error=null;layout=loaded;condition=animation;requestedCode=code;
                    layoutState=new FxSceneInstance(layout.scene());configureLayout();
                    icons.clear();icons.putAll(forecastIcons);
                    conditionState=condition==null?null:new FxSceneInstance(condition.asset().scene);
                    playback=condition==null?null:new FxPlayback(condition.start(),condition.end(),condition.fps(),false);
                    if(playback!=null)playback.play();
                    invalidate();update();
                });
            }catch(Exception failure){
                android.util.Log.e("SenseWeatherWidget","Cannot prepare stock widget "+style,failure);
                main.post(()->{if(token!=generation||!active)return;pending=false;requestedCode=code;error=failure.toString();invalidate();});
            }
        });
    }
    private void configureLayout(){
        for(var node:layout.scene().nodes)if("preview_Weather_2x2.png".equals(node.name))layoutState.setVisible(node,false);
        if(!style.equals("4x4")){layoutState.evaluate(20);return;}
        boolean second=false;
        for(var node:layout.scene().nodes){
            if("Weather_composition_first".equals(node.name)){
                if(second){layoutState.setTimelineFrame(node,-1d);continue;}
                second=true;layoutState.setTimelineFrame(node,92d);
            }
            // Only the first composition is active; the second is stock's city-transition buffer.
            if(node.name!=null && node.name.matches("Forecast_0[1-4]")){layoutState.setTimelineFrame(node,30d);layoutState.setChildFrame(node,30d);}
            if("dynamicimage.weather".equals(node.name))layoutState.setTimelineFrame(node,30d);
            if("Main_Weather_state".equals(node.name))layoutState.setTimelineFrame(node,79d);
            if("timeline.weather_bg_inout".equals(node.name))layoutState.setTimelineFrame(node,20d);
            if("button.footer".equals(node.name))layoutState.setChildFrame(node,5d);
            if("timeline.common_footer_update".equals(node.name))layoutState.setTimelineFrame(node,5d);
        }
        configureNumberWidth();
        layoutState.evaluate(90);
    }
    private void configureNumberWidth(){
        if(layoutState==null||!style.equals("4x4"))return;
        boolean wide=snapshot!=null&&Long.toString(Math.round(snapshot.temperature)).length()>2;
        for(var node:layout.scene().nodes)if("CityTemp 2".equals(node.name)||"CityTemp".equals(node.name))layoutState.setVisible(node,"CityTemp 2".equals(node.name)?wide:!wide);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        if(layout==null){paint.setColor(0xa0000000);canvas.drawRoundRect(0,0,getWidth(),getHeight(),12,12,paint);paint.setColor(Color.WHITE);paint.setTextSize(14*getResources().getDisplayMetrics().scaledDensity);paint.setTextAlign(Paint.Align.CENTER);canvas.drawText(error==null?"Loading weather…":"Weather artwork unavailable",getWidth()/2f,getHeight()/2f,paint);return;}
        scale=Math.min(getWidth()/(float)layout.width(),getHeight()/(float)layout.height());
        if(scale<=0)return;offsetX=(getWidth()-layout.width()*scale)/2;offsetY=(getHeight()-layout.height()*scale)/2;
        int save=canvas.save();canvas.translate(offsetX,offsetY);canvas.scale(scale,scale);
        layoutState.evaluate(style.equals("4x4")?90:20);
        layout.renderer().draw(canvas,layoutState,FxCamera.orthographic(0,layout.width(),layout.height(),0,1,5000),view(1000),layout.width(),layout.height(),this::bind);
        canvas.restoreToCount(save);
    }
    private static float[] view(float distance){float[] view=FxTransform.identity();view[14]=-distance;return view;}
    private void bind(Canvas canvas,FxSceneInstance.State state){
        String name=state.node.name;if(name==null)return;
        if(name.equals("preview_Weather_2x2.png"))return;
        if(name.equals("common_panel.9.png")||name.equals("common_divider_double.9.png")){
            var size=state.node.property("Size").bytes();float w=size.getInt(),h=size.getInt();float[] world=state.world();
            boolean panel=name.startsWith("common_panel");
            layout.renderer().image(canvas,Mode10.hash(name),w,h,state.opacity(),panel?12/Math.max(.01f,Math.abs(world[0])):0,panel?12/Math.max(.01f,Math.abs(world[5])):0);return;
        }
        if(name.equals("Forecast_Weather_state")||name.equals("Forecast_Weather_state_2x2")||name.equals("Main_Weather_state")){
            if(conditionState==null)return;
            double frame=playback==null?condition.end():playback.frame();conditionState.evaluate(frame);
            int layer=canvas.saveLayerAlpha(0,0,condition.width(),condition.height(),Math.round(state.opacity()*255));
            float[] cameraView=view(1000);cameraView[0]=1;cameraView[5]=-1;cameraView[12]=-condition.width()/2f;cameraView[13]=condition.height()/2f;
            float fov=(float)Math.toDegrees(2*Math.atan(condition.height()/2000d));
            condition.renderer().draw(canvas,conditionState,FxCamera.perspective(fov,condition.width()/(float)condition.height(),1,5000),cameraView,condition.width(),condition.height(),null);
            canvas.restoreToCount(layer);return;
        }
        if(name.equals("dynamicimage_weather_s")){
            int day=forecastIndex(state.node);int code=forecastCode(day);Bitmap bitmap=icons.get(WeatherCodes.htcScene(code,true));
            if(bitmap!=null){paint.setAlpha(Math.round(state.opacity()*255));paint.setColorFilter(null);canvas.drawBitmap(bitmap,null,new RectF(0,0,127,130),paint);}return;
        }
        WeatherWidgetAssets.Label label=layout.labels().get(state.node.offset);if(label==null)return;
        String value=value(state.node);if(value==null||value.isEmpty())return;
        paint.setTypeface(label.font().contains("Helvetica")?layout.thin():layout.normal());paint.setTextSize(label.height());
        paint.setColor(label.color());paint.setAlpha(Math.round(Color.alpha(label.color())*state.opacity()));paint.setColorFilter(null);
        boolean left=name.endsWith("_01")||name.endsWith("_02")||name.startsWith("CityTemp");
        paint.setTextAlign(name.equals("CityTempHi")||name.equals("CityTempLow")?Paint.Align.RIGHT:left?Paint.Align.LEFT:Paint.Align.CENTER);
        float max=label.width();
        if(max>0&&paint.measureText(value)>max){int end=paint.breakText(value,0,value.length(),true,Math.max(0,max-paint.measureText("…")),null);value=value.substring(0,end)+"…";}
        canvas.drawText(value,0,0,paint);
    }
    private String value(FxScene.Node node){
        String name=node.name;
        if(name.equals("textlabel.empty_page")||name.equals("warning"))return "";
        if(name.endsWith("_01")||name.equals("CityTemp")||name.equals("CityTemp 2"))return snapshot==null?"—°":degrees(snapshot.temperature);
        if(name.endsWith("_02"))return snapshot==null?"— / —":degrees(snapshot.high)+" / "+degrees(snapshot.low);
        if(name.endsWith("_03")||name.equals("CityName"))return weather.city();
        if(name.endsWith("_04"))return style.equals("2x1")?weather.city():snapshot==null?weather.status():WeatherCodes.description(snapshot.code);
        if(name.equals("CityTempHi"))return snapshot==null?"—":degrees(snapshot.high);
        if(name.equals("CityTempLow"))return snapshot==null?"—":degrees(snapshot.low);
        if(name.equals("Condition"))return snapshot==null?weather.status():WeatherCodes.description(snapshot.code);
        if(name.equals("Temp"))return dayLabel(forecastIndex(node));
        if(name.equals("Hi/Low")){int day=forecastIndex(node);return daily("temperature_2m_max",day)+" / "+daily("temperature_2m_min",day);}
        if(name.equals("textlabel.footer_update_time"))return "↻  "+(snapshot==null?weather.status():DateTimeFormatter.ofPattern("HH:mm",Locale.getDefault()).format(Instant.ofEpochMilli(snapshot.fetched).atZone(zone())));
        if(name.equals("PageNumber")){var cities=weather.cities();int index=1;if(!weather.isCurrentLocation())for(int i=0;i<cities.size();i++)if(cities.get(i).key().equals(weather.selectedCityKey()))index=i+1;int count=cities.size()+(weather.isCurrentLocation()?1:0);return index+" / "+Math.max(1,count);}
        return "";
    }
    private static int forecastIndex(FxScene.Node node){for(var parent=node.parent;parent!=null;parent=parent.parent)if(parent.name!=null&&parent.name.matches("Forecast_0[1-4]"))return Integer.parseInt(parent.name.substring(parent.name.length()-1));return 1;}
    private int forecastCode(int day){try{return forecast.getJSONObject("daily").getJSONArray("weather_code").getInt(day);}catch(Exception ignored){return -1;}}
    private String daily(String key,int day){try{double number=forecast.getJSONObject("daily").getJSONArray(key).getDouble(day);return Double.isFinite(number)?degrees(number):"—";}catch(Exception ignored){return "—";}}
    private String dayLabel(int day){try{long time=forecast.getJSONObject("daily").getJSONArray("time").getLong(day);return DateTimeFormatter.ofPattern("EEE",Locale.getDefault()).format(Instant.ofEpochSecond(time).atZone(zone()));}catch(Exception ignored){return "—";}}
    private ZoneId zone(){try{return ZoneId.of(forecast.optString("timezone","UTC"));}catch(Exception ignored){return ZoneOffset.UTC;}}
    private static String degrees(double value){return Math.round(value)+"°";}
    @Override public boolean onTouchEvent(MotionEvent event){if(event.getActionMasked()==MotionEvent.ACTION_DOWN)tapY=(event.getY()-offsetY)/scale;return super.onTouchEvent(event);}
    private void schedule(){if(!posted&&active&&isShown()&&getWindowVisibility()==VISIBLE&&playback!=null&&playback.isRunning()){posted=true;Choreographer.getInstance().postFrameCallback(this);}}
    private void cancel(){if(posted)Choreographer.getInstance().removeFrameCallback(this);posted=false;if(playback!=null)playback.rebase();}
    @Override public void doFrame(long nanos){posted=false;if(!active||!isShown()||getWindowVisibility()!=VISIBLE){cancel();return;}if(playback!=null)playback.advance(nanos);invalidate();schedule();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(visibility==VISIBLE)schedule();else cancel();}
    @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);if(isShown())schedule();else cancel();}
    boolean readyForTests(){return layout!=null&&!pending&&error==null;}
    String errorForTests(){return error;}
    int conditionCodeForTests(){return requestedCode;}
    void restForTests(){if(playback!=null)playback.seek(condition.end());invalidate();}
}
