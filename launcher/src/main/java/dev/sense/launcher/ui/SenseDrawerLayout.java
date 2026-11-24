package dev.sense.launcher.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.widget.LinearLayout;

/** Vsync-driven bottom drawer. A downward pull at the top follows the finger. */
public class SenseDrawerLayout extends LinearLayout {
    private Runnable dismiss;
    private java.util.function.Consumer<Float> visibilityProgress;
    private android.animation.TimeInterpolator openingCurve=new DecelerateInterpolator(1.5f),closingCurve=new AccelerateInterpolator(1.2f);
    private long openingDuration=320,closingDuration=260;
    private Runnable idleWork;
    private ViewTreeObserver revealObserver;
    private ViewTreeObserver.OnPreDrawListener revealStart;
    private View scrollingContent;
    private float downX,downY,backStartY;
    private boolean pullEligible,pulling,transitioning;
    private VelocityTracker velocity;
    private final int touchSlop;
    public SenseDrawerLayout(Context context){
        super(context);touchSlop=ViewConfiguration.get(context).getScaledTouchSlop();
        animate().setUpdateListener(value->reportProgress());
        setOrientation(VERTICAL);setBackgroundColor(0xff1e1e1e);setClickable(true);
    }
    public void setMotion(android.animation.TimeInterpolator opening,android.animation.TimeInterpolator closing,long duration,java.util.function.Consumer<Float> progress){
        openingCurve=opening;closingCurve=closing;openingDuration=closingDuration=duration;visibilityProgress=progress;
    }
    @Override public void setTranslationY(float value){
        super.setTranslationY(value);
        reportProgress();
    }
    private void reportProgress(){
        if(visibilityProgress!=null && getHeight()>0)visibilityProgress.accept(Math.max(0,Math.min(1,1-getTranslationY()/getHeight())));
    }
    public boolean isTransitioning(){return transitioning || pulling;}
    public void runWhenIdle(Runnable work){
        idleWork=work;
        if(!isTransitioning())drainIdleWork();
    }
    private void drainIdleWork(){
        Runnable work=idleWork;idleWork=null;
        if(work!=null)postOnAnimation(work);
    }
    private void finishedTransition(){transitioning=false;drainIdleWork();}
    public void setOnPullDown(Runnable action){dismiss=action;}
    public void setScrollingContent(View content){scrollingContent=content;}
    private void releaseTracker(){if(velocity!=null){velocity.recycle();velocity=null;}}
    @Override public boolean onInterceptTouchEvent(MotionEvent event){
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN -> {
                downX=event.getRawX();downY=event.getRawY();pulling=false;
                pullEligible=dismiss!=null && !transitioning && getTranslationY()==0 && (scrollingContent==null || !scrollingContent.canScrollVertically(-1));
                releaseTracker();velocity=VelocityTracker.obtain();velocity.addMovement(event);
            }
            case MotionEvent.ACTION_MOVE -> {
                if(velocity!=null)velocity.addMovement(event);
                float dx=event.getRawX()-downX,dy=event.getRawY()-downY;
                if(pullEligible && dy>touchSlop*2 && dy>Math.abs(dx)*1.3f){pulling=true;return true;}
                if(Math.abs(dx)>touchSlop*2 || dy < -touchSlop*2)pullEligible=false;
            }
            case MotionEvent.ACTION_POINTER_DOWN,MotionEvent.ACTION_CANCEL,MotionEvent.ACTION_UP -> {if(!pulling){pullEligible=false;releaseTracker();}}
        }
        return super.onInterceptTouchEvent(event);
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(!pulling)return super.onTouchEvent(event);
        if(velocity!=null)velocity.addMovement(event);
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_MOVE -> {setTranslationY(Math.max(0,Math.min(getHeight(),event.getRawY()-downY)));return true;}
            case MotionEvent.ACTION_UP -> {
                float speed=0;if(velocity!=null){velocity.computeCurrentVelocity(1000);speed=velocity.getYVelocity();}
                boolean close=getTranslationY()>getHeight()*.25f || speed>600*getResources().getDisplayMetrics().density;
                pulling=false;pullEligible=false;releaseTracker();
                if(close && dismiss!=null)dismiss.run();else restore();return true;
            }
            case MotionEvent.ACTION_POINTER_DOWN,MotionEvent.ACTION_CANCEL -> {pulling=false;pullEligible=false;releaseTracker();restore();return true;}
            default -> {return true;}
        }
    }
    private void restore(){
        animate().cancel();
        if(!ValueAnimator.areAnimatorsEnabled()){setTranslationY(0);finishedTransition();return;}
        transitioning=true;animate().translationY(0).setDuration(180).setInterpolator(new DecelerateInterpolator()).withEndAction(this::finishedTransition).start();
    }
    public void reveal(Runnable finished){
        if(!ValueAnimator.areAnimatorsEnabled()){setTranslationY(0);if(visibilityProgress!=null)visibilityProgress.accept(1f);finished.run();return;}
        transitioning=true;
        revealObserver=getViewTreeObserver();
        revealStart=new ViewTreeObserver.OnPreDrawListener(){
            @Override public boolean onPreDraw(){
                if(revealObserver!=null && revealObserver.isAlive())revealObserver.removeOnPreDrawListener(this);
                revealObserver=null;revealStart=null;setTranslationY(getHeight());
                animate().translationY(0).setDuration(openingDuration).setInterpolator(openingCurve).withEndAction(()->{finishedTransition();finished.run();}).start();return true;
            }
        };
        revealObserver.addOnPreDrawListener(revealStart);
    }
    private void cancelPendingReveal(){
        if(revealObserver!=null && revealObserver.isAlive() && revealStart!=null)revealObserver.removeOnPreDrawListener(revealStart);
        revealObserver=null;revealStart=null;
    }
    public void beginBack(){
        cancelPendingReveal();animate().cancel();pulling=false;pullEligible=false;
        backStartY=getTranslationY();transitioning=true;
    }
    public void setBackProgress(float progress){
        setTranslationY(backStartY+(getHeight()-backStartY)*Math.max(0,Math.min(1,progress)));
    }
    public void cancelBack(){restore();}
    public void conceal(Runnable finished){
        cancelPendingReveal();animate().cancel();
        if(!ValueAnimator.areAnimatorsEnabled()){if(visibilityProgress!=null)visibilityProgress.accept(0f);finished.run();return;}
        transitioning=true;
        long duration=Math.max(80,Math.round(closingDuration*(1-getTranslationY()/Math.max(1,getHeight()))));
        animate().translationY(getHeight()).setDuration(duration).setInterpolator(closingCurve).withEndAction(()->{finishedTransition();finished.run();}).start();
    }
    @Override protected void onDetachedFromWindow(){
        cancelPendingReveal();idleWork=null;releaseTracker();animate().cancel();super.onDetachedFromWindow();}
}
