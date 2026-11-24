package dev.sense.launcher;

import dev.sense.framework.SenseButton;
import android.app.Activity;
import android.app.Dialog;
import android.animation.ValueAnimator;
import android.graphics.*;
import android.view.View;
import android.widget.*;
import org.json.*;


/** Preview original conditions without altering the live forecast or saved city. */
final class WeatherPreview {
    static void show(Activity activity) {
        try {
            JSONObject catalog;
            try(var input=activity.getResources().openRawResource(R.raw.htc_weather_catalog)) {
                catalog=new JSONObject(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
            }
            JSONArray scenes=catalog.getJSONArray("scenes");
            String[] labels=new String[scenes.length()];int[] codes=new int[scenes.length()];
            for(int i=0;i<codes.length;i++) {
                JSONObject scene=scenes.getJSONObject(i);codes[i]=scene.getInt("code");
                labels[i]=String.format(java.util.Locale.ROOT,"%02d · %s",codes[i],scene.getString("label"));
            }
            Dialog dialog=new Dialog(activity,android.R.style.Theme_Material_Dialog_Alert);android.content.Context controls=dialog.getContext();dialog.setTitle("HTC animation preview");
            LinearLayout layout=new LinearLayout(controls);layout.setOrientation(LinearLayout.VERTICAL);
            int padding=Math.round(12*activity.getResources().getDisplayMetrics().density);
            layout.setPadding(padding,padding,padding,padding);layout.setBackgroundColor(0xff25302f);
            Spinner picker=new Spinner(controls);
            ArrayAdapter<String> adapter=new ArrayAdapter<>(controls,android.R.layout.simple_spinner_item,labels);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);picker.setAdapter(adapter);layout.addView(picker);
            SceneView preview=new SceneView(controls);
            layout.addView(preview,new LinearLayout.LayoutParams(-1,padding*22));
            picker.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
                @Override public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){preview.select(codes[position]);}
                @Override public void onNothingSelected(android.widget.AdapterView<?> parent){ }
            });
            SenseButton replay=new SenseButton(controls);replay.setText("Replay weather animation");
            replay.setOnClickListener(v->preview.replay());layout.addView(replay);
            TextView note=new TextView(controls);note.setText("Original HTC tracks · adapted camera\nPreview only; live weather is unchanged");note.setTextColor(Color.LTGRAY);note.setPadding(0,padding,0,padding);layout.addView(note);
            SenseButton done=new SenseButton(controls);done.setText("Done");done.setOnClickListener(v->dialog.dismiss());layout.addView(done);
            dialog.setContentView(layout);dialog.show();dialog.getWindow().setLayout(-1,-2);
        } catch(java.io.IOException|JSONException error){throw new IllegalStateException("Invalid generated weather catalogue",error);}
    }
    private static final class SceneView extends View {
        private HtcWeatherScene scene;
        private float frame;
        private ValueAnimator animator;
        private final RectF viewport=new RectF();
        SceneView(android.content.Context context){super(context);setContentDescription("Original HTC weather animation preview");}
        void select(int code){if(animator!=null)animator.cancel();scene=new HtcWeatherScene(getContext(),code);replay();}
        void replay() {
            if(scene==null)return;
            if(animator!=null)animator.cancel();
            frame=scene.end;
            if(isAttachedToWindow()&&ValueAnimator.areAnimatorsEnabled()) {
                animator=ValueAnimator.ofFloat(scene.start,scene.end);animator.setDuration(scene.duration);
                animator.setInterpolator(new android.view.animation.LinearInterpolator());
                animator.addUpdateListener(a->{frame=(float)a.getAnimatedValue();invalidate();});animator.start();
            }
            invalidate();
        }
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);viewport.set(0,0,getWidth(),getHeight());if(scene!=null)scene.draw(canvas,viewport,frame);}
        @Override protected void onDetachedFromWindow(){if(animator!=null)animator.cancel();super.onDetachedFromWindow();}
    }
    private WeatherPreview() { }
}
