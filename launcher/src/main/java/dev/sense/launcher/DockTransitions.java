package dev.sense.launcher;

import android.animation.*;
import android.view.View;

/** Keeps normal dock/rail and bottom Remove laid out throughout mode transitions. */
final class DockTransitions {
    private final BentDockLayout dock;
    private final View remove,visual;
    private final HtcDockMotion motion;
    private ValueAnimator animator;
    private boolean removalDesired;
    private float body,rail,removePosition,removeAlpha;
    DockTransitions(BentDockLayout dock,View remove,View visual,HtcDockMotion motion){this.dock=dock;this.remove=remove;this.visual=visual;this.motion=motion;}
    private void cancel(){if(animator!=null){animator.cancel();animator=null;}}
    void drawerProgress(float hidden){
        cancel();hidden=Math.max(0,Math.min(1,hidden));boolean hiding=hidden>=body;
        body=hidden;rail=hiding?motion.railForBody("slide_off",body):1-motion.railForBody("slide_on",1-body);
        removalDesired=false;removePosition=removeAlpha=0;apply();
    }
    void mode(boolean hidden,boolean removal){
        cancel();removalDesired=removal;float startBody=body,startRail=rail,startPosition=removePosition,startAlpha=removeAlpha;
        String dockName=hidden?"slide_off":"slide_on",removeName=removal?"remove_header_on":"remove_header_off";
        float destination=hidden?1:0,show=removal?1:0;
        animator=ValueAnimator.ofFloat(0,1);animator.setDuration(motion.duration(dockName));animator.setInterpolator(t->t);
        animator.addUpdateListener(value->{
            float t=(float)value.getAnimatedValue();
            body=startBody+(destination-startBody)*motion.sample(dockName,"body",t);
            rail=startRail+(destination-startRail)*motion.sample(dockName,"rail",t);
            removePosition=startPosition+(show-startPosition)*motion.sample(removeName,"position",t);
            removeAlpha=startAlpha+(show-startAlpha)*motion.sample(removeName,"opacity",t);apply();
        });
        if(!ValueAnimator.areAnimatorsEnabled()){body=rail=destination;removePosition=removeAlpha=show;apply();animator=null;return;}
        // Visible immediately for drag dispatch; animation only changes its drawing.
        if(removal)remove.setVisibility(View.VISIBLE);
        if(!hidden)dock.setVisibility(View.VISIBLE);
        animator.start();
    }
    private void apply(){
        dock.setTransitionProgress(body,rail);dock.setVisibility(body>=.99999f?View.INVISIBLE:View.VISIBLE);
        visual.setAlpha(Math.max(0,Math.min(1,removeAlpha)));
        visual.setTranslationY((1-removePosition)*Math.max(1,visual.getHeight()));
        remove.setVisibility(removalDesired || removeAlpha>0?View.VISIBLE:View.INVISIBLE);
    }
    void destroy(){cancel();}
}
