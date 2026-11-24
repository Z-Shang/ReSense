package dev.sense.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.Button;

/** Real attached host view with a child that consumes long clicks and disallows interception. */
final class WidgetGestureInstrumentation {
    private final Instrumentation instrumentation;
    WidgetGestureInstrumentation(Instrumentation instrumentation){this.instrumentation=instrumentation;}
    private void runOnMainSync(Runnable action){instrumentation.runOnMainSync(action);}
    private void waitForIdleSync(){instrumentation.waitForIdleSync();}
    private android.content.Context getTargetContext(){return instrumentation.getTargetContext();}
    private Activity startActivitySync(Intent intent){return instrumentation.startActivitySync(intent);}
    private void finish(int result,Bundle data){instrumentation.finish(result,data);}
    private SenseWidgetHostView host;
    private AlertDialog dialog;
    private final int[] calls=new int[3]; // launcher hold, widget click, widget hold
    private float x,y;
    void run(){
        Bundle result=new Bundle();
        try {
            Activity activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            runOnMainSync(()->{
                host=new SenseWidgetHostView(activity);
                host.setOnLongClickListener(v->{calls[0]++;return true;});
                Button child=new Button(activity);child.setText("Interactive widget content");
                child.setOnClickListener(v->calls[1]++);
                child.setOnLongClickListener(v->{calls[2]++;return true;});
                child.setOnTouchListener((v,event)->{
                    if(event.getActionMasked()==MotionEvent.ACTION_DOWN)v.getParent().requestDisallowInterceptTouchEvent(true);
                    return false;
                });
                host.addView(child,new android.widget.FrameLayout.LayoutParams(-1,220));
                dialog=new AlertDialog.Builder(activity).setView(host).create();dialog.show();
            });
            waitForIdleSync();
            runOnMainSync(()->{x=host.getWidth()/2f;y=host.getHeight()/2f;if(x<=0||y<=0)throw new AssertionError("Unlaid widget host");});
            long down=SystemClock.uptimeMillis();send(MotionEvent.ACTION_DOWN,down,x,y);
            Thread.sleep(70);send(MotionEvent.ACTION_UP,down,x,y);waitForIdleSync();
            runOnMainSync(()->{if(calls[1]!=1||calls[0]!=0||calls[2]!=0)throw new AssertionError("Short tap not preserved");});
            down=SystemClock.uptimeMillis();send(MotionEvent.ACTION_DOWN,down,x,y);
            Thread.sleep(ViewConfiguration.getLongPressTimeout()+180);
            send(MotionEvent.ACTION_UP,down,x,y);waitForIdleSync();
            runOnMainSync(()->{if(calls[0]!=1||calls[1]!=1||calls[2]!=0)throw new AssertionError("Launcher did not take long-press priority");});
            down=SystemClock.uptimeMillis();send(MotionEvent.ACTION_DOWN,down,x,y);send(MotionEvent.ACTION_CANCEL,down,x,y);
            Thread.sleep(ViewConfiguration.getLongPressTimeout()+100);waitForIdleSync();
            runOnMainSync(()->{if(calls[0]!=1)throw new AssertionError("Cancelled gesture started dragging");});
            down=SystemClock.uptimeMillis();send(MotionEvent.ACTION_DOWN,down,x,y);
            float delta=ViewConfiguration.get(activity).getScaledTouchSlop()*3;
            send(MotionEvent.ACTION_MOVE,down,x+delta,y);
            Thread.sleep(ViewConfiguration.getLongPressTimeout()+100);send(MotionEvent.ACTION_UP,down,x+delta,y);
            runOnMainSync(()->{if(calls[0]!=1)throw new AssertionError("Moving gesture started launcher hold");dialog.dismiss();});
            runOnMainSync(()->resizeChecks((MainActivity)activity));
            result.putString("result","Widget tap/hold priority, cancellation, movement, snapped resize, collision rejection and saved size passed");
            finish(Activity.RESULT_OK,result);
        }catch(Throwable error){
            if(dialog!=null)runOnMainSync(()->dialog.dismiss());
            result.putString("error",android.util.Log.getStackTraceString(error));finish(Activity.RESULT_CANCELED,result);
        }
    }
    private void resizeChecks(MainActivity activity) {
        android.content.SharedPreferences preferences=activity.getSharedPreferences("widget_resize_instrumentation",0);
        preferences.edit().clear().putInt("columns",4).putInt("rows",6).putInt("pages",1).putString("items","[]").commit();
        Workspace workspace=new Workspace(activity,new SenseWidgetHost(activity,9001),preferences);
        org.json.JSONObject item=new org.json.JSONObject(),blocked=new org.json.JSONObject();
        Workspace.put(item,"type","clock");Workspace.put(item,"page",1);Workspace.put(item,"x",0);Workspace.put(item,"y",0);Workspace.put(item,"w",2);Workspace.put(item,"h",2);
        Workspace.put(blocked,"type","clock");Workspace.put(blocked,"page",1);Workspace.put(blocked,"x",3);Workspace.put(blocked,"y",0);Workspace.put(blocked,"w",1);Workspace.put(blocked,"h",2);
        workspace.items=new org.json.JSONArray().put(item).put(blocked);workspace.render();
        workspace.measure(android.view.View.MeasureSpec.makeMeasureSpec(800,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(1200,android.view.View.MeasureSpec.EXACTLY));
        workspace.layout(0,0,800,1200);workspace.beginResize(item);workspace.layout(0,0,800,1200);
        android.view.View overlay=workspace.getChildAt(workspace.getChildCount()-1);
        overlay.measure(android.view.View.MeasureSpec.makeMeasureSpec(800,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(1200,android.view.View.MeasureSpec.EXACTLY));overlay.layout(0,0,800,1200);
        resizeDrag(overlay,400-workspace.dp(3),200,200);
        if(item.optInt("w")!=3)throw new AssertionError("Resize did not snap to three cells");
        workspace.layout(0,0,800,1200);overlay=workspace.getChildAt(workspace.getChildCount()-1);
        overlay.measure(android.view.View.MeasureSpec.makeMeasureSpec(800,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(1200,android.view.View.MeasureSpec.EXACTLY));overlay.layout(0,0,800,1200);
        resizeDrag(overlay,600-workspace.dp(3),200,200);
        if(item.optInt("w")!=3)throw new AssertionError("Resize overwrote occupied space");
        try {
            if(new org.json.JSONArray(preferences.getString("items","[]")).getJSONObject(0).getInt("w")!=3)throw new AssertionError("Resize was not saved");
        }catch(org.json.JSONException error){throw new AssertionError(error);}
        workspace.finishResize();preferences.edit().clear().commit();
    }
    private void resizeDrag(android.view.View view,float x,float y,float dx) {
        long time=SystemClock.uptimeMillis();
        int step=0;
        for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP}) {
            MotionEvent event=MotionEvent.obtain(time,time+16*step++,action,action==MotionEvent.ACTION_DOWN?x:x+dx,y,0);
            view.dispatchTouchEvent(event);event.recycle();
        }
    }
    private void send(int action,long down,float x,float y){runOnMainSync(()->{
        MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);
        host.dispatchTouchEvent(event);event.recycle();
    });}
}
