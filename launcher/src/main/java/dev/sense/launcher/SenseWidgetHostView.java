package dev.sense.launcher;

import android.appwidget.AppWidgetHostView;
import android.content.Context;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

/** Observes the gesture before its RemoteViews descendants, even if they disallow interception. */
final class SenseWidgetHostView extends AppWidgetHostView {
    private final int touchSlop;
    private float downX,downY;
    private long downTime;
    private boolean pending,claimed;
    private final Runnable hold=()->{
        if(!pending || !isAttachedToWindow() || !isShown())return;
        pending=false;
        if(performLongClick()) {
            claimed=true;
            // Clear pressed state and pending clicks/long presses inside the widget.
            MotionEvent cancel=MotionEvent.obtain(downTime,SystemClock.uptimeMillis(),MotionEvent.ACTION_CANCEL,downX,downY,0);
            super.dispatchTouchEvent(cancel);
            cancel.recycle();
        }
    };
    SenseWidgetHostView(Context context){super(context);touchSlop=ViewConfiguration.get(context).getScaledTouchSlop();}
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        switch(event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                clearHold();claimed=false;
                downX=event.getX();downY=event.getY();downTime=event.getDownTime();
                pending=true;
                // Queue before forwarding DOWN, so the launcher timer precedes child timers.
                postDelayed(hold,ViewConfiguration.getLongPressTimeout());
                break;
            case MotionEvent.ACTION_MOVE:
                if(Math.abs(event.getX()-downX)>touchSlop || Math.abs(event.getY()-downY)>touchSlop)clearHold();
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                clearHold();break;
        }
        return claimed || super.dispatchTouchEvent(event);
    }
    @Override protected void onSizeChanged(int width,int height,int oldWidth,int oldHeight) {
        super.onSizeChanged(width,height,oldWidth,oldHeight);
        if(width>0 && height>0 && getAppWidgetInfo()!=null) {
            float density=getResources().getDisplayMetrics().density;
            updateAppWidgetSize(new android.os.Bundle(),java.util.List.of(new android.util.SizeF(width/density,height/density)));
        }
    }
    private void clearHold(){pending=false;removeCallbacks(hold);}
    @Override public void cancelLongPress(){super.cancelLongPress();clearHold();}
    @Override protected void onDetachedFromWindow(){clearHold();super.onDetachedFromWindow();}
}
