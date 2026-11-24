package dev.sense.framework;

import android.animation.*;
import android.content.Context;
import android.view.*;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;

/** ROM activity fades: reveal the next screen on open, fade away the top screen on close. */
public final class SenseScreenHost extends FrameLayout {
    private ObjectAnimator transition;
    private View current;
    public SenseScreenHost(Context context){super(context);}
    public void show(View screen,boolean animate,boolean forward){
        settle();View previous=current;current=screen;
        if(previous==null||!animate||!isLaidOut()||!ValueAnimator.areAnimatorsEnabled()){
            removeAllViews();addView(screen,new LayoutParams(-1,-1));return;
        }
        previous.clearFocus();previous.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        addView(screen,forward?getChildCount():0,new LayoutParams(-1,-1));
        View front=forward?screen:previous;front.setAlpha(forward?0:1);
        transition=ObjectAnimator.ofFloat(front,View.ALPHA,forward?0:1,forward?1:0);
        transition.setDuration(200);transition.setInterpolator(new AccelerateDecelerateInterpolator());
        transition.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator animation){settle();}});
        transition.start();
    }
    private void settle(){
        if(transition!=null){ObjectAnimator old=transition;transition=null;old.removeAllListeners();old.cancel();}
        for(int i=getChildCount()-1;i>=0;i--)if(getChildAt(i)!=current)removeViewAt(i);
        if(current!=null){current.setAlpha(1);current.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_AUTO);}
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent event){return transition!=null||super.onInterceptTouchEvent(event);}
    @Override protected void onDetachedFromWindow(){settle();super.onDetachedFromWindow();}
}
