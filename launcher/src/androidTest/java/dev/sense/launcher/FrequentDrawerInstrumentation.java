package dev.sense.launcher;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import dev.sense.launcher.ui.*;

/** Verify initial Frequent cells actually have visible geometry, not merely bound models. */
final class FrequentDrawerInstrumentation {
    private final Instrumentation test;
    FrequentDrawerInstrumentation(Instrumentation test){this.test=test;}
    void run(){
        Bundle result=new Bundle();int status=Activity.RESULT_OK;
        var history=test.getTargetContext().getSharedPreferences("app_launches",0);var drawer=test.getTargetContext().getSharedPreferences("drawer",0);
        var recency=test.getTargetContext().getSharedPreferences("app_recency",0);
        Map<String,?> oldHistory=history.getAll(),oldDrawer=drawer.getAll(),oldRecency=recency.getAll();
        try{
            recency.edit().clear().commit();
            String component=new ComponentName(test.getTargetContext(),MainActivity.class).flattenToString();
            history.edit().clear().putLong(component,5).commit();
            for(boolean list:new boolean[]{false,true}){
                drawer.edit().putBoolean("listMode",list).commit();
                MainActivity activity=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
                if(list)await(activity::drawerPrepared,"Prewarmed drawer");
                main(()->{try{var field=MainActivity.class.getDeclaredField("frequentApps");field.setAccessible(true);field.setBoolean(activity,true);}catch(Exception e){throw new AssertionError(e);}activity.showApps();});
                await(()->{var panel=find(activity.getWindow().getDecorView(),SenseDrawerLayout.class);return panel!=null && !panel.isTransitioning() && find(panel,DrawerAppViewport.class)!=null;},"First Frequent load");
                test.waitForIdleSync();
                main(()->assertVisible(activity,list?"Warm list":"Cold grid"));
                main(()->activity.closeDrawer());
                await(()->find(activity.getWindow().getDecorView(),SenseDrawerLayout.class)==null,"Drawer closes");
                main(activity::showApps);
                await(()->{var panel=find(activity.getWindow().getDecorView(),SenseDrawerLayout.class);return panel!=null && !panel.isTransitioning();},"Reopened Frequent");
                main(()->assertVisible(activity,"Reopened "+list));
            }
            main(()->{
                var context=test.getTargetContext();var catalog=new ArrayList<AppCatalog.Entry>();
                for(int i=0;i<60;i++){
                    var app=new AppCatalog.Entry(new ComponentName("test.lru","App"+i),"App "+i,null,"A");catalog.add(app);AppRecency.record(context,app.component());
                }
                AppRecency.record(context,catalog.get(0).component());
                var ordered=AppRecency.ordered(context,catalog);
                if(ordered.size()!=60 || ordered.get(0)!=catalog.get(0) || ordered.get(1)!=catalog.get(59) || ordered.get(59)!=catalog.get(1))throw new AssertionError("LRU promotion/order failed");
                for(boolean listMode:new boolean[]{false,true}){
                    var viewport=new DrawerAppViewport(context,ordered,listMode,true,()->listMode?new SenseAppRow(context):new SenseAppIcon(context),(icon,app)->{icon.setApp(app.label(),app.drawable(context));icon.setTag(app);});
                    int cell=Math.round((listMode?72:104)*context.getResources().getDisplayMetrics().density),columns=listMode?1:4;
                    for(int height:new int[]{cell*2+cell/2,cell*3,cell/2}){
                        int width=context.getResources().getDisplayMetrics().widthPixels;
                        viewport.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));viewport.layout(0,0,width,height);
                        int expected=columns*Math.max(1,height/cell);
                        if(viewport.appCount()!=expected || viewport.boundCount()!=expected)throw new AssertionError("Frequent exceeds one page: "+viewport.appCount()+" / "+expected);
                        viewport.jumpTo(59);if(viewport.getScrollY()!=0)throw new AssertionError("Frequent still scrolls");
                        ViewGroup cells=(ViewGroup)viewport.getChildAt(0);
                        for(int i=0;i<cells.getChildCount();i++){
                            View icon=cells.getChildAt(i);int position=viewport.positionOf(icon);
                            if(icon.getTag()!=ordered.get(position) || icon.getBottom()>height || icon.getWidth()<=0)throw new AssertionError("LRU cell order/geometry failed");
                        }
                    }
                }
            });
            result.putString("result","Frequent cold/warm/reopen geometry, LRU promotion and eviction, one-page limits and resizing in grid/list modes passed");
        }catch(Throwable error){status=Activity.RESULT_CANCELED;result.putString("error",android.util.Log.getStackTraceString(error));}
        finally{restore(history,oldHistory);restore(drawer,oldDrawer);restore(recency,oldRecency);}
        test.finish(status,result);
    }
    private void assertVisible(MainActivity activity,String label){
        var viewport=find(activity.getWindow().getDecorView(),DrawerAppViewport.class);SenseAppIcon icon=find(viewport,SenseAppIcon.class);
        if(viewport==null || viewport.appCount()!=1 || icon==null)throw new AssertionError(label+": Frequent app missing");
        android.graphics.Rect visible=new android.graphics.Rect();
        if(icon.getWidth()==0 || icon.getHeight()==0 || !icon.getGlobalVisibleRect(visible))throw new AssertionError(label+": app is bound but blank ("+icon.getWidth()+"x"+icon.getHeight()+")");
    }
    private void main(Runnable work){Throwable[] error={null};test.runOnMainSync(()->{try{work.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError(error[0]);}
    private void await(BooleanSupplier condition,String label)throws Exception{long until=SystemClock.uptimeMillis()+10000;while(SystemClock.uptimeMillis()<until){boolean[] ready={false};main(()->ready[0]=condition.getAsBoolean());if(ready[0])return;Thread.sleep(30);}throw new AssertionError(label+" timed out");}
    private static <T>T find(View view,Class<T> type){if(view==null)return null;if(type.isInstance(view))return type.cast(view);if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){T result=find(group.getChildAt(i),type);if(result!=null)return result;}return null;}
    private static void restore(android.content.SharedPreferences prefs,Map<String,?> values){var edit=prefs.edit().clear();for(var entry:values.entrySet()){Object v=entry.getValue();String k=entry.getKey();if(v instanceof Long n)edit.putLong(k,n);else if(v instanceof Boolean b)edit.putBoolean(k,b);else if(v instanceof String s)edit.putString(k,s);else if(v instanceof Integer n)edit.putInt(k,n);}edit.commit();}
}
