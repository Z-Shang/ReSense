package dev.sense.launcher;

import android.content.Context;
import android.graphics.RectF;
import org.json.*;

/** Embedding size and anchor recovered from the clock parent, independent of screen pixel density. */
final class WeatherWidgetGeometry {
    private final float widgetWidth,widgetHeight,sceneWidth,sceneHeight,x,y,scaleX,scaleY;
    WeatherWidgetGeometry(Context context) {
        try(var input=context.getResources().openRawResource(R.raw.htc_weather_widget_geometry)) {
            JSONObject data=new JSONObject(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
            widgetWidth=(float)data.getJSONArray("widgetDimensions").getDouble(0);
            widgetHeight=(float)data.getJSONArray("widgetDimensions").getDouble(1);
            sceneWidth=(float)data.getJSONArray("weatherDimensions").getDouble(0);
            sceneHeight=(float)data.getJSONArray("weatherDimensions").getDouble(1);
            x=(float)data.getJSONArray("weatherPosition").getDouble(0);
            y=(float)data.getJSONArray("weatherPosition").getDouble(1);
            scaleX=(float)data.getJSONArray("weatherScale").getDouble(0);
            scaleY=(float)data.getJSONArray("weatherScale").getDouble(1);
        } catch(java.io.IOException|JSONException error){throw new IllegalStateException("Invalid clock embedding geometry",error);}
    }
    void fit(float width,float height,RectF output) {
        // Preserve the weather scene's aspect even though the reconstructed clock uses the preview's proportions.
        float scale=Math.min(width/widgetWidth*scaleX,height/widgetHeight*scaleY);
        float cx=width*x/widgetWidth,cy=height*y/widgetHeight;
        float halfWidth=sceneWidth*scale/2,halfHeight=sceneHeight*scale/2;
        output.set(cx-halfWidth,cy-halfHeight,cx+halfWidth,cy+halfHeight);
    }
}
