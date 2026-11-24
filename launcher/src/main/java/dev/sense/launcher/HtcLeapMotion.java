package dev.sense.launcher;

import android.content.Context;
import org.json.*;

/** Stock scene samples, projected to the seven recovered hitbox positions. */
final class HtcLeapMotion {
    private final JSONArray slots;
    final long duration;
    private final JSONArray reorderCurve,dragScale,dragOpacity;
    HtcLeapMotion(Context context){
        try(var input=context.getResources().openRawResource(R.raw.sense_leap)){
            JSONObject root=new JSONObject(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
            slots=root.getJSONArray("slots");duration=root.getLong("durationMs");
            reorderCurve=root.getJSONArray("reorderCurve");dragScale=root.getJSONArray("dragScale");dragOpacity=root.getJSONArray("dragOpacity");
        }catch(Exception error){throw new IllegalStateException("Cannot load Rosie Leap tracks",error);}
    }
    private float scalar(JSONArray samples,float progress){
        float f=Math.max(0,Math.min(1,progress))*(samples.length()-1);int i=(int)f;
        return (float)(samples.optDouble(i)+(samples.optDouble(Math.min(i+1,samples.length()-1))-samples.optDouble(i))*(f-i));
    }
    float reorder(float progress){return scalar(reorderCurve,progress);}
    float dragScale(float progress){return scalar(dragScale,progress);}
    float dragOpacity(float progress){return scalar(dragOpacity,progress);}
    private float value(JSONArray samples,float progress,int axis)throws JSONException{
        float frame=Math.max(0,Math.min(1,progress))*(samples.length()-1);
        int lower=(int)frame,upper=Math.min(lower+1,samples.length()-1);float fraction=frame-lower;
        float a=(float)samples.getJSONArray(lower).getDouble(axis),b=(float)samples.getJSONArray(upper).getDouble(axis);
        return a+(b-a)*fraction;
    }
    float[] pose(int slot,int selected,float progress){
        try{
            JSONObject entry=slots.getJSONObject(slot);
            JSONObject track=entry.getJSONArray("tracks").getJSONObject(Math.max(0,Math.min(6,selected)));
            JSONArray positions=track.getJSONArray("Position"),rotation=track.getJSONArray("Rotation"),center=entry.getJSONArray("center");
            float x=value(positions,progress,0),y=value(positions,progress,1),z=value(positions,progress,2);
            // Projection is calibrated to the recovered 3.7 panel scale and settled hitboxes.
            // This is an adaptation, not a claim that the stock runtime camera is recovered.
            float focal=2000f/2.7f,factor=focal/(focal-z);
            float endX=value(positions,1,0),endY=value(positions,1,1);
            float px=270+(x-270)*factor,py=480+(y-480)*factor;
            px+=progress*((float)center.getDouble(0)-(270+(endX-270)/3.7f));
            py+=progress*((float)center.getDouble(1)-(480+(endY-480)/3.7f));
            float qy=value(rotation,progress,1),qw=value(rotation,progress,3);
            float degrees=(float)Math.toDegrees(2*Math.atan2(qy,qw));
            while(degrees>180)degrees-=360;while(degrees< -180)degrees+=360;
            return new float[]{px,py,3.7f*factor,degrees};
        }catch(JSONException error){throw new IllegalStateException(error);}
    }
}
