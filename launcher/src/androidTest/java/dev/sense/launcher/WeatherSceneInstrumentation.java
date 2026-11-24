package dev.sense.launcher;

import android.app.Instrumentation;
import android.app.Activity;
import android.os.Bundle;
import android.graphics.*;
import org.json.*;
import java.io.File;
import java.io.FileOutputStream;

/** Render every recovered scene on Android and export held poses for visual review. */
public final class WeatherSceneInstrumentation extends Instrumentation {
    private boolean weatherWidgets;
    private boolean drawerActions;
    private boolean widgetPicker;
    private boolean weatherWidth;
    private boolean fullscreen;
    private boolean frequent;
    private boolean settings;
    private boolean search;
    private boolean music;
    private boolean navigation;
    private boolean leap;
    private boolean widgets;
    private boolean discover;
    private boolean remove;
    private boolean drawer;
    private boolean catalog;
    private boolean rainCoverage;
    private boolean weatherCoverage;
    private boolean widgetWeatherReturn;
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);widgetWeatherReturn=arguments!=null && "widget_weather_return".equals(arguments.getString("suite"));weatherCoverage=arguments!=null && "weather_coverage".equals(arguments.getString("suite"));rainCoverage=arguments!=null && "rain_coverage".equals(arguments.getString("suite"));drawerActions=arguments!=null && "drawer_actions".equals(arguments.getString("suite"));weatherWidgets=arguments!=null && "weather_widgets".equals(arguments.getString("suite"));widgetPicker=arguments!=null && "widget_picker".equals(arguments.getString("suite"));frequent=arguments!=null && "frequent".equals(arguments.getString("suite"));fullscreen=arguments!=null && "fullscreen_weather".equals(arguments.getString("suite"));weatherWidth=arguments!=null && "weather_width".equals(arguments.getString("suite"));settings=arguments!=null && "settings".equals(arguments.getString("suite"));search=arguments!=null && "search".equals(arguments.getString("suite"));music=arguments!=null && "music".equals(arguments.getString("suite"));navigation=arguments!=null && "navigation".equals(arguments.getString("suite"));leap=arguments!=null && "leap".equals(arguments.getString("suite"));catalog=arguments!=null && "catalog".equals(arguments.getString("suite"));drawer=arguments!=null && "drawer".equals(arguments.getString("suite"));remove=arguments!=null && "remove".equals(arguments.getString("suite"));discover=arguments!=null && "discover".equals(arguments.getString("suite"));widgets=arguments!=null && "widgets".equals(arguments.getString("suite"));start();}
    @Override public void onStart(){
        if(widgetWeatherReturn){new WidgetWeatherReturnInstrumentation(this).run();return;}
        if(weatherCoverage){new WeatherCoverageAuditInstrumentation(this).run();return;}
        if(rainCoverage){new RainCoverageInstrumentation(this).run();return;}
        if(drawerActions){new DrawerActionsInstrumentation(this).run();return;}
        if(weatherWidgets){new WeatherWidgetInstrumentation(this).run();return;}
        if(widgetPicker){new WidgetPickerInstrumentation(this).run();return;}
        if(frequent){new FrequentDrawerInstrumentation(this).run();return;}
        if(fullscreen){new FullscreenWeatherInstrumentation(this).run();return;}
        if(weatherWidth){new WeatherWidthInstrumentation(this).run();return;}
        if(settings){new SettingsInstrumentation(this).run();return;}
        if(search){new SearchInstrumentation(this).run();return;}
        if(music){new MusicInstrumentation(this).run();return;}
        if(navigation){new NavigationBackgroundInstrumentation(this).run();return;}
        if(leap){new LeapInstrumentation(this).run();return;}
        if(catalog){new AppCatalogInstrumentation(this).run();return;}
        if(drawer){new DrawerWorkspaceInstrumentation(this).run();return;}
        if(remove){new RemoveInstrumentation(this).run();return;}
        if(discover){new DiscoverInstrumentation(this).run();return;}
        if(widgets){new WidgetGestureInstrumentation(this).run();return;}
        Bundle report=new Bundle();
        try {
            final Throwable[] failure={null};final int[] checked={0};
            runOnMainSync(()->{
                try {
                    JSONObject catalog;
                    try(var input=getTargetContext().getResources().openRawResource(R.raw.htc_weather_catalog)){
                        catalog=new JSONObject(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
                    }
                    File output=new File(getTargetContext().getFilesDir(),"weather-scene-checks");
                    if(!output.isDirectory()&&!output.mkdirs())throw new IllegalStateException("Cannot create preview output");
                    JSONArray entries=catalog.getJSONArray("scenes");
                    Bitmap bitmap=Bitmap.createBitmap(480,480,Bitmap.Config.ARGB_8888);
                    Canvas canvas=new Canvas(bitmap);RectF bounds=new RectF(0,0,480,480);
                    for(int i=0;i<entries.length();i++){
                        int code=entries.getJSONObject(i).getInt("code");HtcWeatherScene scene=new HtcWeatherScene(getTargetContext(),code);
                        for(float frame:new float[]{scene.start,(scene.start+scene.entranceEnd)/2,scene.entranceEnd,(scene.entranceEnd+scene.end)/2,scene.end}) {
                            bitmap.eraseColor(Color.TRANSPARENT);scene.draw(canvas,bounds,frame);
                        }
                        int visible=0;
                        for(int y=0;y<480;y+=3)for(int x=0;x<480;x+=3)if(Color.alpha(bitmap.getPixel(x,y))>20)visible++;
                        if(visible<4)throw new AssertionError("Blank original weather scene "+code);
                        try(var stream=new FileOutputStream(new File(output,String.format(java.util.Locale.ROOT,"scene_%02d.png",code)))){bitmap.compress(Bitmap.CompressFormat.PNG,100,stream);}
                        checked[0]++;
                    }
                    bitmap.recycle();
                }catch(Throwable error){failure[0]=error;}
            });
            if(failure[0]!=null)throw new IllegalStateException("Weather scene render failed",failure[0]);
            report.putString("result","Rendered "+checked[0]+" original scenes across entrance and settled-phase samples");
            finish(Activity.RESULT_OK,report);
        }catch(Throwable error){report.putString("error",android.util.Log.getStackTraceString(error));finish(Activity.RESULT_CANCELED,report);}
    }
}
