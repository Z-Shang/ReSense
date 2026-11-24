package dev.sense.launcher;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.provider.*;
import android.view.*;
import android.widget.*;
import android.view.accessibility.AccessibilityNodeInfo;
import dev.sense.launcher.ui.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Backs up home/drawer preferences. Never accepts the system uninstall confirmation. */
final class DrawerActionsInstrumentation {
    private final Instrumentation test;
    private Intent launched;
    DrawerActionsInstrumentation(Instrumentation test){this.test=test;}
    private void main(Runnable action){test.runOnMainSync(action);}
    private void await(BooleanSupplier condition,String message){long until=SystemClock.uptimeMillis()+10000;boolean[] ready={false};while(SystemClock.uptimeMillis()<until){main(()->ready[0]=condition.getAsBoolean());if(ready[0])return;SystemClock.sleep(30);}throw new AssertionError(message);}
    private static <T extends View>T find(View root,Class<T> type){if(type.isInstance(root))return type.cast(root);if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){T value=find(group.getChildAt(i),type);if(value!=null)return value;}return null;}
    private static View tagged(View root,Object tag){if(tag.equals(root.getTag()))return root;if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View value=tagged(group.getChildAt(i),tag);if(value!=null)return value;}return null;}
    private static TextView text(View root,String label){if(root==null)return null;if(root instanceof TextView view&&label.contentEquals(view.getText()))return view;if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){TextView value=text(group.getChildAt(i),label);if(value!=null)return value;}return null;}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private void touch(int action,long down,float x,float y){MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);check(test.getUiAutomation().injectInputEvent(event,true),"Input injection");event.recycle();}
    void run(){
        Bundle report=new Bundle();int status=Activity.RESULT_OK;MainActivity activity=null;
        var context=test.getTargetContext();var homePrefs=context.getSharedPreferences("workspace",0);var drawerPrefs=context.getSharedPreferences("drawer",0);var recency=context.getSharedPreferences("app_recency",0);
        Map<String,?> homeBackup=homePrefs.getAll(),drawerBackup=drawerPrefs.getAll(),recentBackup=recency.getAll();
        Instrumentation.ActivityMonitor monitor=null;
        try {
            homePrefs.edit().clear().putString("items","[{\"type\":\"clock\",\"page\":1,\"x\":0,\"y\":0,\"w\":4,\"h\":2}]").putInt("columns",4).putInt("rows",6).putInt("pages",2).putInt("currentPage",1).commit();
            drawerPrefs.edit().putBoolean("listMode",false).commit();
            activity=(MainActivity)test.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));MainActivity screen=activity;
            monitor=new Instrumentation.ActivityMonitor(){@Override public Instrumentation.ActivityResult onStartActivity(Intent intent){
                if(Settings.ACTION_APPLICATION_DETAILS_SETTINGS.equals(intent.getAction())||Intent.ACTION_DELETE.equals(intent.getAction())||android.provider.AlarmClock.ACTION_SHOW_ALARMS.equals(intent.getAction())||(intent.getComponent()!=null&&"dev.sense.weather".equals(intent.getComponent().getPackageName()))){launched=new Intent(intent);return new Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null);}return null;
            }};test.addMonitor(monitor);
            await(()->AppCatalog.get(screen).loaded(),"App catalog");
            AppCatalog.Entry app=AppCatalog.get(screen).entries().stream().filter(a->a.component().getPackageName().equals("dev.sense.weather")).findFirst().orElseThrow();
            for(boolean list: new boolean[]{false,true}){
                main(()->{drawerPrefs.edit().putBoolean("listMode",list).commit();screen.showApps();});
                await(()->{DrawerAppViewport viewport=find(screen.getWindow().getDecorView(),DrawerAppViewport.class);SenseDrawerLayout drawer=find(screen.getWindow().getDecorView(),SenseDrawerLayout.class);return screen.hasWindowFocus()&&viewport!=null&&viewport.getHeight()>0&&drawer!=null&&drawer.isLaidOut()&&!drawer.isTransitioning();},"Drawer ready");
                main(()->find(screen.getWindow().getDecorView(),DrawerAppViewport.class).jumpTo(AppCatalog.get(screen).entries().indexOf(app)));
                await(()->{View icon=tagged(screen.getWindow().getDecorView(),app.component());return icon!=null&&icon.getWidth()>0&&icon.getGlobalVisibleRect(new Rect());},"App icon");
                View[] icon={null};main(()->icon[0]=tagged(screen.getWindow().getDecorView(),app.component()));
                Rect visible=new Rect();main(()->{check(icon[0].getGlobalVisibleRect(visible),"Visible app bounds");Rect frame=new Rect();icon[0].getWindowVisibleDisplayFrame(frame);visible.intersect(frame);});
                float x=visible.centerX(),y=visible.centerY();
                long down=SystemClock.uptimeMillis();touch(MotionEvent.ACTION_DOWN,down,x,y);
                await(()->screen.drawerAppMenu().isShowing(),"Long press popup");touch(MotionEvent.ACTION_UP,down,x,y);test.waitForIdleSync();
                if(!list){Bitmap shot=test.getUiAutomation().takeScreenshot();if(shot!=null){try(var output=context.openFileOutput("drawer-app-actions.png",0)){shot.compress(Bitmap.CompressFormat.PNG,100,output);}shot.recycle();}}
                main(()->{
                    View menu=screen.drawerAppMenu().contentForTests();check(text(menu,"App info")!=null&&text(menu,"Uninstall")!=null&&text(menu,"Add to Home screen")!=null,"Missing app actions");
                    check(find(screen.getWindow().getDecorView(),SenseDrawerLayout.class)!=null,"Long press closed drawer");
                    Rect frame=new Rect();icon[0].getWindowVisibleDisplayFrame(frame);int[] position=new int[2];menu.getLocationOnScreen(position);
                    check(position[0]>=frame.left&&position[1]>=frame.top&&position[0]+menu.getWidth()<=frame.right&&position[1]+menu.getHeight()<=frame.bottom,"Popup outside visible screen");
                    launched=null;text(menu,"App info").performClick();check(launched!=null&&Settings.ACTION_APPLICATION_DETAILS_SETTINGS.equals(launched.getAction())&&("package:"+app.component().getPackageName()).equals(launched.getDataString()),"App info target");
                    icon[0].performLongClick();launched=null;text(screen.drawerAppMenu().contentForTests(),"Uninstall").performClick();
                    check(launched!=null&&Intent.ACTION_DELETE.equals(launched.getAction())&&("package:"+app.component().getPackageName()).equals(launched.getDataString()),"Uninstall target");
                    icon[0].performLongClick();screen.onBackPressed();check(!screen.drawerAppMenu().isShowing()&&find(screen.getWindow().getDecorView(),SenseDrawerLayout.class)!=null,"Back should dismiss only popup");
                    icon[0].performLongClick();DrawerAppViewport viewport=find(screen.getWindow().getDecorView(),DrawerAppViewport.class);
                    if(viewport.canScrollVertically(-1))viewport.scrollBy(0,-100);else if(viewport.canScrollVertically(1))viewport.scrollBy(0,100);else screen.showApps();
                });
                await(()->!screen.drawerAppMenu().isShowing(),"Scrolled/recycled anchor kept popup");
            }
            main(()->{screen.showApps();});await(()->find(screen.getWindow().getDecorView(),DrawerAppViewport.class)!=null,"Drawer restore");
            main(()->find(screen.getWindow().getDecorView(),DrawerAppViewport.class).jumpTo(AppCatalog.get(screen).entries().indexOf(app)));
            await(()->tagged(screen.getWindow().getDecorView(),app.component())!=null,"Add app icon");
            main(()->{View icon=tagged(screen.getWindow().getDecorView(),app.component());icon.performLongClick();text(screen.drawerAppMenu().contentForTests(),"Add to Home screen").performClick();});
            await(()->find(screen.getWindow().getDecorView(),Workspace.class)!=null,"Add to home");
            main(()->{Workspace workspace=find(screen.getWindow().getDecorView(),Workspace.class);check(workspace.items.length()==2,"Shortcut not placed");check(app.component().flattenToString().equals(workspace.items.optJSONObject(1).optString("component")),"Wrong home shortcut");});
            // Exercise hold-and-move handoff without dropping a shortcut into the user's real home.
            main(screen::showApps);await(()->{var drawer=find(screen.getWindow().getDecorView(),SenseDrawerLayout.class);return drawer!=null&&drawer.isLaidOut()&&!drawer.isTransitioning();},"Drag test drawer");
            main(()->find(screen.getWindow().getDecorView(),DrawerAppViewport.class).jumpTo(AppCatalog.get(screen).entries().indexOf(app)));
            int[] drag={0};Rect held=new Rect();
            await(()->{View icon=tagged(screen.getWindow().getDecorView(),app.component());return icon!=null&&icon.getWidth()>0&&icon.getGlobalVisibleRect(held);},"Drag icon");
            main(()->{View icon=tagged(screen.getWindow().getDecorView(),app.component());screen.drawerAppMenu().bind(icon,app,()->drag[0]++,()->{});launched=null;});
            long heldDown=SystemClock.uptimeMillis();touch(MotionEvent.ACTION_DOWN,heldDown,held.centerX(),held.centerY());
            await(()->screen.drawerAppMenu().isShowing(),"Held drag popup");
            touch(MotionEvent.ACTION_MOVE,heldDown,held.centerX()+40*screen.getResources().getDisplayMetrics().density,held.centerY());
            await(()->drag[0]==1&&!screen.drawerAppMenu().isShowing(),"Hold/move did not hand off drag");
            touch(MotionEvent.ACTION_UP,heldDown,held.centerX()+40*screen.getResources().getDisplayMetrics().density,held.centerY());
            check(launched==null,"Dragging launched app");
            test.removeMonitor(monitor);
            main(()->screen.open(new Intent(Intent.ACTION_DELETE,android.net.Uri.fromParts("package",app.component().getPackageName(),null))));
            boolean confirmation=false;long until=SystemClock.uptimeMillis()+10000;
            try {
                while(SystemClock.uptimeMillis()<until){
                    AccessibilityNodeInfo root=test.getUiAutomation().getRootInActiveWindow();
                    if(root!=null&&root.getPackageName()!=null&&root.getPackageName().toString().contains("packageinstaller")){
                        var cancel=root.findAccessibilityNodeInfosByText("Cancel");
                        if(!cancel.isEmpty()){confirmation=true;check(cancel.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK),"Cancel uninstall");break;}
                    }
                    SystemClock.sleep(50);
                }
            }finally{if(!confirmation)test.getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);}
            check(confirmation,"System uninstall confirmation did not open");test.addMonitor(monitor);
            await(screen::hasWindowFocus,"Home after uninstall cancel");
            // Clock and weather split at several view aspect ratios, including letterbox space.
            for(int[] size:new int[][]{{374,272},{600,600}}){
                SenseClockView[] clock={null};main(()->{FrameLayout root=new FrameLayout(screen);clock[0]=new SenseClockView(screen);root.addView(clock[0],new FrameLayout.LayoutParams(size[0],size[1]));screen.setContentView(root);});
                test.waitForIdleSync();main(()->{
                    SenseClockView view=clock[0];view.measure(View.MeasureSpec.makeMeasureSpec(size[0],1073741824),View.MeasureSpec.makeMeasureSpec(size[1],1073741824));view.layout(0,0,size[0],size[1]);
                    float fit=Math.min(size[0]/374f,size[1]/272f),ox=(size[0]-374*fit)/2,oy=(size[1]-272*fit)/2;
                    launched=null;tap(view,ox+80*fit,oy+80*fit);
                });
                await(()->launched!=null&&AlarmClock.ACTION_SHOW_ALARMS.equals(launched.getAction()),"Clock face did not open clock");
                main(()->{float fit=Math.min(size[0]/374f,size[1]/272f),ox=(size[0]-374*fit)/2,oy=(size[1]-272*fit)/2;launched=null;tap(clock[0],ox+100*fit,oy+230*fit);});
                await(()->launched!=null&&launched.getComponent()!=null&&"dev.sense.weather".equals(launched.getComponent().getPackageName()),"Weather region did not open Weather");
                main(()->{launched=null;clock[0].performAccessibilityAction(R.id.open_clock,null);check(launched!=null&&AlarmClock.ACTION_SHOW_ALARMS.equals(launched.getAction()),"Clock accessibility action");});
            }
            report.putString("result","PASS grid/list anchored app actions, held popup and hold/move drag handoff, correct info/uninstall intents, real uninstall confirmation cancelled, add-to-home, back/scroll dismissal, scaled clock/weather tap regions and accessibility");
        }catch(Throwable failure){status=Activity.RESULT_CANCELED;report.putString("error",android.util.Log.getStackTraceString(failure));}
        finally {
            if(monitor!=null)test.removeMonitor(monitor);
            if(activity!=null){MainActivity screen=activity;main(screen::finish);}test.waitForIdleSync();
            restore(homePrefs,homeBackup);restore(drawerPrefs,drawerBackup);restore(recency,recentBackup);
        }
        test.finish(status,report);
    }
    private static void tap(View view,float x,float y){long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0);view.dispatchTouchEvent(down);down.recycle();MotionEvent up=MotionEvent.obtain(now,now+20,MotionEvent.ACTION_UP,x,y,0);view.dispatchTouchEvent(up);up.recycle();}
    private static void restore(SharedPreferences prefs,Map<String,?> values){var edit=prefs.edit().clear();for(var entry:values.entrySet()){Object value=entry.getValue();if(value instanceof String s)edit.putString(entry.getKey(),s);else if(value instanceof Boolean b)edit.putBoolean(entry.getKey(),b);else if(value instanceof Integer i)edit.putInt(entry.getKey(),i);else if(value instanceof Long l)edit.putLong(entry.getKey(),l);else if(value instanceof Float f)edit.putFloat(entry.getKey(),f);else if(value instanceof Set<?> set){@SuppressWarnings("unchecked")var strings=(Set<String>)set;edit.putStringSet(entry.getKey(),strings);}}edit.commit();}
}
