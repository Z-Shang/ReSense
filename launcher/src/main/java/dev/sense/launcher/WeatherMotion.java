package dev.sense.launcher;

import android.content.Context;
import org.json.*;
import java.util.HashMap;

/** Samples recovered Mode10 vectors; timing and final placement belong to the native host. */
final class WeatherMotion {
    private final HashMap<String, JSONObject> nodes = new HashMap<>();
    final float entranceStart,entranceEnd;
    final long entranceDurationMs;
    private final float[] rotationStart=new float[4],rotationEnd=new float[4];
    WeatherMotion(Context context) {
        try (var input=context.getResources().openRawResource(R.raw.sense_weather_animation)) {
            JSONObject data=new JSONObject(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
            JSONObject timeline=data.getJSONArray("timelines").getJSONObject(0);
            JSONObject fields=timeline.getJSONObject("fields");
            double fps=fields.getJSONObject("FramesPerSecond").getDouble("value");
            double speed=fields.getJSONObject("PlaybackSpeed").getDouble("value");
            JSONObject entrance=null;
            JSONArray markers=timeline.getJSONArray("markers");
            for(int i=0;i<markers.length();i++)if("In".equals(markers.getJSONObject(i).getString("name")))entrance=markers.getJSONObject(i);
            if(entrance==null || fps<=0 || speed<=0)throw new JSONException("Missing weather entrance timing");
            entranceStart=(float)entrance.getDouble("start");
            entranceEnd=(float)entrance.getDouble("last_frame");
            entranceDurationMs=Math.round((entranceEnd-entranceStart)*1000/(fps*speed));
            JSONArray list=data.getJSONArray("nodes");
            for(int i=0;i<list.length();i++) {
                JSONObject node=list.getJSONObject(i);
                nodes.put(node.getString("name"),node.getJSONObject("properties"));
            }
        } catch(java.io.IOException | JSONException error) { throw new IllegalStateException("Invalid generated weather tracks",error); }
    }
    float sample(String node,String property,float frame,int component,float fallback) {
        JSONObject props=nodes.get(node);
        JSONObject track=props==null ? null : props.optJSONObject(property);
        if(track==null)return fallback;
        JSONArray fixed=track.optJSONArray("value");
        if(fixed!=null)return (float)fixed.optDouble(component,fallback);
        JSONArray samples=track.optJSONArray("samples");
        if(samples==null || samples.length()==0)return fallback;
        JSONObject left=samples.optJSONObject(0);
        for(int i=1;i<samples.length();i++) {
            JSONObject right=samples.optJSONObject(i);
            if(frame<=right.optDouble("frame")) {
                float start=(float)left.optDouble("frame"),end=(float)right.optDouble("frame");
                float t=end==start ? 0 : Math.max(0,Math.min(1,(frame-start)/(end-start)));
                float a=(float)left.optJSONArray("value").optDouble(component,fallback);
                float b=(float)right.optJSONArray("value").optDouble(component,fallback);
                return a+(b-a)*t;
            }
            left=right;
        }
        return (float)left.optJSONArray("value").optDouble(component,fallback);
    }
    void rotation(String node,float frame,float[] result) {
        JSONObject props=nodes.get(node);
        JSONObject track=props==null ? null : props.optJSONObject("Rotation");
        JSONArray fixed=track==null ? null : track.optJSONArray("value");
        if(fixed!=null) {
            readQuaternion(fixed,rotationStart);
            WeatherProjection.interpolate(rotationStart,rotationStart,0,result);
            return;
        }
        JSONArray samples=track==null ? null : track.optJSONArray("samples");
        if(samples==null || samples.length()==0) {result[0]=result[1]=result[2]=0;result[3]=1;return;}
        JSONObject left=samples.optJSONObject(0);
        for(int i=1;i<samples.length();i++) {
            JSONObject right=samples.optJSONObject(i);
            if(frame<=right.optDouble("frame")) {
                float start=(float)left.optDouble("frame"),end=(float)right.optDouble("frame");
                float t=end==start ? 0 : Math.max(0,Math.min(1,(frame-start)/(end-start)));
                readQuaternion(left.optJSONArray("value"),rotationStart);
                readQuaternion(right.optJSONArray("value"),rotationEnd);
                WeatherProjection.interpolate(rotationStart,rotationEnd,t,result);
                return;
            }
            left=right;
        }
        readQuaternion(left.optJSONArray("value"),rotationStart);
        WeatherProjection.interpolate(rotationStart,rotationStart,0,result);
    }

    private void readQuaternion(JSONArray source,float[] result) {
        for(int i=0;i<4;i++)result[i]=(float)source.optDouble(i,i==3 ? 1 : 0);
    }
}
