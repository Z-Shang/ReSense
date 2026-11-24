package dev.sense.launcher;

import android.app.*;
import android.appwidget.AppWidgetHost;
import android.content.*;
import android.os.*;
import android.view.*;
import org.json.*;

/** Real system drag/drop against an isolated workspace, plus widget-ID cleanup. */
final class RemoveInstrumentation {
    private final Instrumentation test;
    private Workspace workspace;
    private MainActivity activity;
    private Workspace original;
    private ViewGroup homeParent;
    private int homeIndex;
    private final int[] deleted={-1};
    RemoveInstrumentation(Instrumentation test){this.test=test;}
    void run(){
        Bundle result=new Bundle();
        try{
            activity=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            main(()->{
                var prefs=activity.getSharedPreferences("remove_instrumentation",0);
                prefs.edit().clear().putString("items","[]").putInt("pages",1).commit();
                workspace=new Workspace(activity,new AppWidgetHost(activity,9002){
                    @Override public void deleteAppWidgetId(int id){deleted[0]=id;}
                },prefs);
                JSONObject icon=new JSONObject();Workspace.put(icon,"type","app");Workspace.put(icon,"component",new ComponentName(activity,MainActivity.class).flattenToString());
                Workspace.put(icon,"page",1);Workspace.put(icon,"x",0);Workspace.put(icon,"y",1);Workspace.put(icon,"w",1);Workspace.put(icon,"h",1);
                workspace.items.put(icon);workspace.save();workspace.render();
                original=findWorkspace(activity.getWindow().getDecorView());
                homeParent=(ViewGroup)original.getParent();homeIndex=homeParent.indexOfChild(original);
                ViewGroup.LayoutParams params=original.getLayoutParams();
                homeParent.removeView(original);homeParent.addView(workspace,homeIndex,params);
            });
            test.waitForIdleSync();
            Thread.sleep(400);
            drag(true);
            main(()->{if(workspace.items.length()!=1)throw new AssertionError("Cancelled drag removed an item");});
            drag(false);
            main(()->{
                if(workspace.items.length()!=0 || !"[]".equals(workspace.prefs.getString("items","")))throw new AssertionError("Remove drop did not persist shortcut deletion");
                if(deleted[0]!=-1)throw new AssertionError("Shortcut removal released a widget ID");
                JSONObject widget=new JSONObject();Workspace.put(widget,"type","widget");Workspace.put(widget,"id",123456);
                workspace.items.put(widget);workspace.remove(widget);
                if(deleted[0]!=123456 || workspace.items.length()!=0)throw new AssertionError("Widget ID not released");
                deleted[0]=-1;workspace.remove(widget);
                if(deleted[0]!=-1)throw new AssertionError("Widget ID released twice");
                workspace.prefs.edit().clear().commit();restoreHome();
            });
            result.putString("result","Real drag cancellation and remove drop, saved shortcut removal, widget ID release and duplicate-removal guard passed");
            test.finish(Activity.RESULT_OK,result);
        }catch(Throwable error){
            main(()->{restoreHome();if(workspace!=null)workspace.prefs.edit().clear().commit();});
            result.putString("error",android.util.Log.getStackTraceString(error));test.finish(Activity.RESULT_CANCELED,result);
        }
    }
    private void drag(boolean cancel)throws Exception{
        int[] origin=new int[2];float[] points=new float[4];
        main(()->{
            workspace.getLocationOnScreen(origin);
            points[0]=origin[0]+workspace.getWidth()/(2f*workspace.columns);
            points[1]=origin[1]+workspace.getHeight()*1.5f/workspace.rows;
            points[2]=origin[0]+workspace.getWidth()/2f;points[3]=origin[1]+workspace.dp(32);
        });
        long down=SystemClock.uptimeMillis();
        send(down,MotionEvent.ACTION_DOWN,points[0],points[1]);
        Thread.sleep(ViewConfiguration.getLongPressTimeout()+200);
        long introDeadline=SystemClock.uptimeMillis()+3000;
        while(SystemClock.uptimeMillis()<introDeadline){
            boolean[] ready={false};main(()->{View dock=findDock(activity.getWindow().getDecorView());ready[0]=dock!=null && dock.getVisibility()==View.INVISIBLE;});
            if(ready[0])break;Thread.sleep(30);
        }
        main(()->{
            View target=findRemove(activity.getWindow().getDecorView());
            if(target==null || target.getVisibility()!=View.VISIBLE)throw new AssertionError("Bottom Remove target missing during drag");
            if(original.getParent()!=null)throw new AssertionError("Test is not isolated");
            int[] location=new int[2];target.getLocationOnScreen(location);
            points[2]=location[0]+target.getWidth()/2f;points[3]=location[1]+target.getHeight()/2f;
            View dock=findDock(activity.getWindow().getDecorView());
            if(dock==null || dock.getVisibility()!=View.INVISIBLE)throw new AssertionError("Dock did not hide during drag");
        });
        for(int step=1;step<=12;step++){
            float amount=step/12f;
            send(down,MotionEvent.ACTION_MOVE,points[0]+(points[2]-points[0])*amount,points[1]+(points[3]-points[1])*amount);
            Thread.sleep(20);
        }
        long hoverDeadline=SystemClock.uptimeMillis()+2000;
        while(SystemClock.uptimeMillis()<hoverDeadline){
            boolean[] hovered={false};
            main(()->{View target=findRemove(activity.getWindow().getDecorView());hovered[0]=target!=null && target.isActivated();});
            if(hovered[0])break;
            Thread.sleep(30);
        }
        main(()->{
            View target=findRemove(activity.getWindow().getDecorView());
            if(target==null || !target.isActivated())throw new AssertionError("Bottom Remove target did not highlight under drag");
        });
        send(down,cancel?MotionEvent.ACTION_CANCEL:MotionEvent.ACTION_UP,points[2],points[3]);
        long deadline=SystemClock.uptimeMillis()+3000;
        while(SystemClock.uptimeMillis()<deadline){
            boolean[] restored={false};
            main(()->{
                View dock=findDock(activity.getWindow().getDecorView());
                View target=findRemove(activity.getWindow().getDecorView());
                restored[0]=dock!=null && dock.getVisibility()==View.VISIBLE && target!=null && target.getVisibility()==View.INVISIBLE;
            });
            if(restored[0])return;
            Thread.sleep(50);
        }
        throw new AssertionError("Dock did not restore after dragging");
    }
    private void main(Runnable action){
        Throwable[] failure={null};test.runOnMainSync(()->{try{action.run();}catch(Throwable error){failure[0]=error;}});
        if(failure[0]!=null)throw new AssertionError(failure[0]);
    }
    private void restoreHome(){
        if(homeParent!=null && workspace!=null && workspace.getParent()==homeParent){
            ViewGroup.LayoutParams params=workspace.getLayoutParams();homeParent.removeView(workspace);homeParent.addView(original,homeIndex,params);
        }
    }
    private Workspace findWorkspace(View view){return (Workspace)find(view,0);}
    private View findRemove(View view){return find(view,1);}
    private View findDock(View view){return find(view,2);}
    private View find(View view,int kind){
        if(kind==0 && view instanceof Workspace || kind==2 && view instanceof BentDockLayout)return view;
        if(kind==1 && view.getContentDescription()!=null && view.getContentDescription().toString().startsWith("Remove from home screen"))return view;
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View result=find(group.getChildAt(i),kind);if(result!=null)return result;}
        return null;
    }
    private void send(long down,int action,float x,float y){
        MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        if(!test.getUiAutomation().injectInputEvent(event,true))throw new AssertionError("Input injection failed");
        event.recycle();
    }
}
