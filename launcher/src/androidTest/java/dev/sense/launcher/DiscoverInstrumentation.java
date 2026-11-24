package dev.sense.launcher;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import java.util.function.BooleanSupplier;

/** Exercises the real Google service and the workspace gesture path on a Google APIs image. */
final class DiscoverInstrumentation {
    private final Instrumentation instrumentation;
    private MainActivity activity;
    private Workspace workspace;
    DiscoverInstrumentation(Instrumentation instrumentation){this.instrumentation=instrumentation;}
    void run(){
        Bundle report=new Bundle();
        int[] originalPage={1};
        try{
            activity=(MainActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            // CLEAR_TASK can briefly overlap the old and new host windows. Google
            // supports one host per caller; let the old window detach before retrying.
            instrumentation.waitForIdleSync();
            Thread.sleep(500);
            instrumentation.runOnMainSync(()->activity.discover().retry());
            await(()->activity.discover().isReady(),"Google service handshake");
            instrumentation.runOnMainSync(()->{
                workspace=findWorkspace(activity.getWindow().getDecorView());
                if(workspace==null)throw new AssertionError("No home workspace");
                originalPage[0]=workspace.page;
                workspace.page=1;workspace.render();
                activity.discover().open();
            });
            await(()->activity.discover().isVisible(),"Google overlay open callback");
            instrumentation.runOnMainSync(()->activity.discover().close());
            await(()->!activity.discover().isVisible(),"Overlay close callback");
            // Dispatch locally to keep the complete gesture in the launcher even as
            // Google's separate window appears. Input events carry fixed raw coordinates.
            instrumentation.runOnMainSync(()->{
                long start=SystemClock.uptimeMillis();
                dispatch(start,MotionEvent.ACTION_DOWN,30);
                dispatch(start,MotionEvent.ACTION_MOVE,workspace.getWidth()*.35f);
                dispatch(start,MotionEvent.ACTION_CANCEL,workspace.getWidth()*.35f);
                if(workspace.page!=1)throw new AssertionError("Cancelled feed gesture changed desktop page");
            });
            await(()->!activity.discover().isVisible(),"Cancelled overlay gesture");
            instrumentation.runOnMainSync(()->{
                long start=SystemClock.uptimeMillis();
                dispatch(start,MotionEvent.ACTION_DOWN,30);
                dispatch(start,MotionEvent.ACTION_MOVE,workspace.getWidth()*.4f);
                dispatch(start,MotionEvent.ACTION_MOVE,workspace.getWidth()*.95f);
                dispatch(start,MotionEvent.ACTION_UP,workspace.getWidth()*.95f);
                if(workspace.page!=1)throw new AssertionError("Discover occupied a desktop page");
            });
            await(()->activity.discover().isVisible(),"Workspace swipe opens Discover");
            instrumentation.runOnMainSync(()->{activity.discover().close();activity.discover().stop();activity.discover().start();activity.discover().resume();});
            await(()->activity.discover().isReady(),"Lifecycle reconnect");
            instrumentation.runOnMainSync(()->{
                activity.discover().close();workspace.page=originalPage[0];workspace.render();
            });
            report.putString("result","Real Google overlay handshake, open/close, cancelled swipe, workspace swipe, desktop page preservation and reconnect passed");
            instrumentation.finish(Activity.RESULT_OK,report);
        }catch(Throwable error){
            if(activity!=null)instrumentation.runOnMainSync(()->{activity.discover().close();if(workspace!=null){workspace.page=originalPage[0];workspace.render();}});
            report.putString("error",android.util.Log.getStackTraceString(error));instrumentation.finish(Activity.RESULT_CANCELED,report);
        }
    }
    private void dispatch(long down,int action,float x){
        MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,workspace.getHeight()*.7f,0);
        workspace.dispatchTouchEvent(event);event.recycle();
    }
    private void await(BooleanSupplier condition,String name)throws InterruptedException{
        long deadline=SystemClock.uptimeMillis()+10000;
        while(SystemClock.uptimeMillis()<deadline){
            boolean[] passed={false};instrumentation.runOnMainSync(()->passed[0]=condition.getAsBoolean());
            if(passed[0])return;
            Thread.sleep(50);
        }
        throw new AssertionError(name+" timed out");
    }
    private Workspace findWorkspace(View view){
        if(view instanceof Workspace result)return result;
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){Workspace result=findWorkspace(group.getChildAt(i));if(result!=null)return result;}
        return null;
    }
}
