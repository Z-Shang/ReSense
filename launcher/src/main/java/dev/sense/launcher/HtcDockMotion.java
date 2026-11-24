package dev.sense.launcher;

import android.animation.*;
import android.content.Context;
import android.view.View;
import org.json.*;
import java.util.*;

/** Recovered stock samples; authoring FPS controls duration, never display cadence. */
final class HtcDockMotion {
    private record Motion(long duration,Map<String,float[]> channels){}
    private final Map<String,Motion> motions=new HashMap<>();
    HtcDockMotion(Context context){
        try(var input=context.getResources().openRawResource(R.raw.sense_dock_motion)){
            JSONObject data=new JSONObject(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getJSONObject("motions");
            for(var names=data.keys();names.hasNext();){
                String name=names.next();JSONObject source=data.getJSONObject(name),channels=source.getJSONObject("channels");
                Map<String,float[]> samples=new HashMap<>();
                for(var keys=channels.keys();keys.hasNext();){String key=keys.next();JSONArray values=channels.getJSONArray(key);float[] array=new float[values.length()];for(int i=0;i<array.length;i++)array[i]=(float)values.getDouble(i);samples.put(key,array);}
                motions.put(name,new Motion(source.getLong("durationMs"),samples));
            }
        }catch(java.io.IOException|JSONException error){throw new IllegalStateException("Invalid dock motion assets",error);}
    }
    long duration(String name){return motions.get(name).duration();}
    private float sample(float[] values,float progress){
        float frame=Math.max(0,Math.min(1,progress))*(values.length-1);int index=(int)frame;
        float a=values[index],b=values[Math.min(index+1,values.length-1)];return a+(b-a)*(frame-index);
    }
    float sample(String name,String channel,float progress){return sample(motions.get(name).channels().get(channel),progress);}
    float railForBody(String name,float body){
        Motion curve=motions.get(name);float[] position=curve.channels().get("body"),rail=curve.channels().get("rail");
        if(body<=0)return 0;if(body>=1)return 1;
        float low=0,high=1;
        for(int i=0;i<14;i++){float middle=(low+high)/2;if(sample(position,middle)<body)low=middle;else high=middle;}
        return sample(rail,(low+high)/2);
    }
    ValueAnimator header(View header,boolean show,Runnable finished){
        String name=show?"edit_header_on":"edit_header_off";
        float distance=64*header.getResources().getDisplayMetrics().widthPixels/540f;
        float startY=show?-distance:header.getTranslationY(),startAlpha=show?0:header.getAlpha();
        ValueAnimator animator=ValueAnimator.ofFloat(0,1);animator.setDuration(duration(name));animator.setInterpolator(t->t);
        animator.addUpdateListener(value->{float t=(float)value.getAnimatedValue();float position=sample(name,"position",t),opacity=sample(name,"opacity",t);header.setTranslationY(startY+((show?0:-distance)-startY)*position);header.setAlpha(startAlpha+((show?1:0)-startAlpha)*opacity);});
        animator.addListener(new AnimatorListenerAdapter(){private boolean cancelled;@Override public void onAnimationCancel(Animator animation){cancelled=true;}@Override public void onAnimationEnd(Animator animation){if(!cancelled)finished.run();}});
        if(!ValueAnimator.areAnimatorsEnabled()){header.setTranslationY(0);header.setAlpha(show?1:0);finished.run();return animator;}
        header.setTranslationY(startY);header.setAlpha(startAlpha);animator.start();return animator;
    }
}
