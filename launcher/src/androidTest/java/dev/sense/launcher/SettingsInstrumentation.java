package dev.sense.launcher;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.json.*;

/** Full-screen settings, live preferences and return-to-desktop actions; user state restored. */
final class SettingsInstrumentation {
    private final Instrumentation test;
    private MainActivity home;
    private LauncherSettingsActivity settings;
    private Instrumentation.ActivityMonitor monitor;
    private final Map<String,Map<String,?>> backups=new HashMap<>();
    SettingsInstrumentation(Instrumentation test){this.test=test;}
    void run(){
        Bundle report=new Bundle();
        try{
            for(String name:new String[]{"workspace","gestures","drawer",BrowserSearch.PREFERENCES})backups.put(name,new HashMap<>(test.getTargetContext().getSharedPreferences(name,0).getAll()));
            String fixture="[{\"type\":\"clock\",\"page\":1,\"x\":0,\"y\":0,\"w\":4,\"h\":2},{\"type\":\"search\",\"page\":1,\"x\":0,\"y\":2,\"w\":4,\"h\":1},{\"type\":\"music\",\"musicRows\":4,\"page\":2,\"x\":0,\"y\":0,\"w\":4,\"h\":4}]";
            test.getTargetContext().getSharedPreferences("workspace",0).edit().clear().putString("items",fixture).putInt("columns",4).putInt("rows",6).putInt("pages",2).putInt("currentPage",1).commit();
            home=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            Intent[] weatherLaunch={null};
            monitor=new Instrumentation.ActivityMonitor(){
                @Override public Instrumentation.ActivityResult onStartActivity(Intent intent){
                    if(intent.getComponent()!=null && "dev.sense.weather".equals(intent.getComponent().getPackageName())){
                        weatherLaunch[0]=new Intent(intent);return new Instrumentation.ActivityResult(Activity.RESULT_OK,null);
                    }return null;
                }
            };test.addMonitor(monitor);
            main(()->{
                SenseClockView clock=find(home.getWindow().getDecorView(),SenseClockView.class);
                if(clock==null || !clock.performClick())throw new AssertionError("Weather widget is not tappable");
                Intent opened=weatherLaunch[0];
                if(opened==null || !"dev.sense.weather.WeatherActivity".equals(opened.getComponent().getClassName()))throw new AssertionError("Widget did not open Sense Weather");
                var weather=WeatherRepository.get(home);
                if(!weather.city().equals(opened.getStringExtra("city"))
                        || opened.getDoubleExtra("latitude",Double.NaN)!=Double.parseDouble(weather.prefs.getString("latitude","-33.8688"))
                        || opened.getDoubleExtra("longitude",Double.NaN)!=Double.parseDouble(weather.prefs.getString("longitude","151.2093")))
                    throw new AssertionError("Widget did not pass the selected weather location");
            });
            test.removeMonitor(monitor);monitor=null;
            settings=(LauncherSettingsActivity)test.startActivitySync(new Intent(test.getTargetContext(),LauncherSettingsActivity.class).putExtra("section","desktop").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            main(()->{
                View root=settings.getWindow().getDecorView();
                for(String title:new String[]{"Sense settings","Desktop","App drawer","Desktop gestures","Search","Weather","Integrations","Placed items","System"})if(text(root,title)==null)throw new AssertionError("Missing settings section: "+title);
                ((RadioButton)text(root,"DuckDuckGo")).performClick();
                if(BrowserSearch.engine(home)!=BrowserSearch.Engine.DUCKDUCKGO)throw new AssertionError("Inline search setting not saved");
                ((RadioButton)text(root,"List")).performClick();
                if(!home.getSharedPreferences("drawer",0).getBoolean("listMode",false))throw new AssertionError("Drawer setting not saved");
                NumberPicker hold=(NumberPicker)description(root,"Alphabet hold delay");hold.setValue(8);
                // NumberPicker's programmatic setter does not notify its listener;
                // exercise the native accessibility increment used by users.
                hold.requestRectangleOnScreen(new android.graphics.Rect(0,0,hold.getWidth(),hold.getHeight()),true);
                if(!hold.getAccessibilityNodeProvider().performAction(android.view.accessibility.AccessibilityNodeProvider.HOST_VIEW_ID,
                        android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,null))throw new AssertionError("Hold delay picker did not accept increment");
            });
            await(()->dev.sense.launcher.ui.AlphabetIndex.holdDelayMillis(home)==550,"Alphabet hold setting saved");
            main(()->{
                View root=settings.getWindow().getDecorView();
                ArrayList<RadioGroup> groups=new ArrayList<>();collect(root,RadioGroup.class,groups);
                ((RadioButton)groups.get(1).getChildAt(0)).performClick();((RadioButton)groups.get(2).getChildAt(1)).performClick();
                if(DesktopGestures.action(home,true)!=0 || DesktopGestures.action(home,false)!=1)throw new AssertionError("Gesture settings not saved");
                ((NumberPicker)description(root,"Desktop columns")).setValue(3);((NumberPicker)description(root,"Desktop rows")).setValue(4);text(root,"Apply grid").performClick();
                var prefs=home.getSharedPreferences("workspace",0);JSONArray items=WorkspaceSettings.items(prefs);
                if(prefs.getInt("columns",0)!=3 || prefs.getInt("rows",0)!=4 || items.length()!=3)throw new AssertionError("Grid change lost items or dimensions");
                verifyGrid(items,3,4);
                Switch clock=(Switch)text(root,"Show Sense clock and weather");clock.setChecked(false);if(WorkspaceSettings.hasClock(prefs))throw new AssertionError("Clock toggle did not remove clock");
                clock.setChecked(true);if(!WorkspaceSettings.hasClock(prefs))throw new AssertionError("Clock toggle did not restore clock");verifyGrid(WorkspaceSettings.items(prefs),3,4);
                View searchLabel=contains(root,"· Sense Search");ViewGroup itemRows=(ViewGroup)searchLabel.getParent();View actions=itemRows.getChildAt(itemRows.indexOfChild(searchLabel)+1);text(actions,"Remove").performClick();
                if(WorkspaceSettings.items(prefs).length()!=2)throw new AssertionError("Inline remove did not persist");
                settings.finish();
            });
            await(()->{Workspace workspace=find(home.getWindow().getDecorView(),Workspace.class);return workspace!=null && workspace.columns==3 && workspace.rows==4 && workspace.items.length()==2;},"Apply settings on returning home");
            monitor=test.addMonitor(LauncherSettingsActivity.class.getName(),null,false);
            main(()->{
                AlertDialog menu=find(home.getWindow().getDecorView(),Workspace.class).menu();
                String[] expected={"Add Widget","Manage Pages","Choose Wallpaper","Sense Settings","Android Settings"};
                var adapter=menu.getListView().getAdapter();
                if(adapter.getCount()!=expected.length)throw new AssertionError("Quick menu has wrong actions");
                for(int i=0;i<expected.length;i++)if(!expected[i].equals(adapter.getItem(i)))throw new AssertionError("Quick menu action/order mismatch");
                menu.getListView().performItemClick(null,3,3);
            });
            settings=(LauncherSettingsActivity)test.waitForMonitorWithTimeout(monitor,5000);test.removeMonitor(monitor);monitor=null;
            if(settings==null)throw new AssertionError("Dock menu did not open settings activity");
            main(()->text(settings.getWindow().getDecorView(),"Manage pages").performClick());
            PageOverview[] overview={null};
            await(()->{try{Workspace workspace=find(home.getWindow().getDecorView(),Workspace.class);var field=Workspace.class.getDeclaredField("pageOverview");field.setAccessible(true);overview[0]=(PageOverview)field.get(workspace);return overview[0]!=null && overview[0].isShowing();}catch(Exception error){throw new AssertionError(error);}},"Settings page-management shortcut");
            main(()->overview[0].dismiss());await(()->!overview[0].isShowing(),"Page overview closes");
            monitor=test.addMonitor(LauncherSettingsActivity.class.getName(),null,false);
            main(()->BrowserSearch.showSettings(home));settings=(LauncherSettingsActivity)test.waitForMonitorWithTimeout(monitor,5000);test.removeMonitor(monitor);monitor=null;
            if(settings==null)throw new AssertionError("Search gear did not open unified settings");
            await(()->find(settings.getWindow().getDecorView(),ScrollView.class).getScrollY()>0,"Search settings section scroll");
            main(()->settings.finish());test.waitForIdleSync();
            monitor=test.addMonitor(LauncherSettingsActivity.class.getName(),null,false);
            main(()->WeatherSettings.show(home));settings=(LauncherSettingsActivity)test.waitForMonitorWithTimeout(monitor,5000);test.removeMonitor(monitor);monitor=null;
            if(settings==null)throw new AssertionError("Clock did not open unified weather settings");
            await(()->find(settings.getWindow().getDecorView(),ScrollView.class).getScrollY()>0,"Weather settings section scroll");
            cleanup();report.putString("result","Full-screen settings sections, inline search/drawer/gesture preferences, non-overlapping grid reflow, clock toggle, item removal, home reload, dock/search/weather entry points and page-management shortcut passed");test.finish(Activity.RESULT_OK,report);
        }catch(Throwable error){try{cleanup();}catch(Throwable ignored){}report.putString("error",android.util.Log.getStackTraceString(error));test.finish(Activity.RESULT_CANCELED,report);}
    }
    private void verifyGrid(JSONArray items,int columns,int rows){
        for(int i=0;i<items.length();i++){
            JSONObject item=items.optJSONObject(i);int x=item.optInt("x"),y=item.optInt("y"),w=item.optInt("w"),h=item.optInt("h");
            if(x<0 || y<0 || w<1 || h<1 || x+w>columns || y+h>rows)throw new AssertionError("Item outside configured grid");
            for(int j=0;j<i;j++){JSONObject other=items.optJSONObject(j);if(item.optInt("page")==other.optInt("page") && x<other.optInt("x")+other.optInt("w") && x+w>other.optInt("x") && y<other.optInt("y")+other.optInt("h") && y+h>other.optInt("y"))throw new AssertionError("Grid settings overlapped items");}
        }
    }
    private void cleanup(){if(monitor!=null)test.removeMonitor(monitor);main(()->{
        if(settings!=null && !settings.isFinishing())settings.finish();
        for(var entry:backups.entrySet()){
            var edit=test.getTargetContext().getSharedPreferences(entry.getKey(),0).edit().clear();
            for(var value:entry.getValue().entrySet()){
                Object data=value.getValue();String key=value.getKey();
                if(data instanceof String text)edit.putString(key,text);else if(data instanceof Integer number)edit.putInt(key,number);else if(data instanceof Boolean flag)edit.putBoolean(key,flag);else if(data instanceof Long number)edit.putLong(key,number);else if(data instanceof Float number)edit.putFloat(key,number);else if(data instanceof Set<?> set){Set<String> values=new HashSet<>();for(Object item:set)values.add((String)item);edit.putStringSet(key,values);}
            }edit.commit();
        }
        if(home!=null){Workspace workspace=find(home.getWindow().getDecorView(),Workspace.class);if(workspace!=null)workspace.reloadSavedSettings();}
    });}
    private void main(Runnable action){Throwable[] error={null};test.runOnMainSync(()->{try{action.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError(error[0]);}
    private void await(BooleanSupplier condition,String message)throws Exception{long end=SystemClock.uptimeMillis()+6000;while(SystemClock.uptimeMillis()<end){boolean[] result={false};main(()->result[0]=condition.getAsBoolean());if(result[0])return;Thread.sleep(40);}throw new AssertionError(message);}
    private static <T extends View>T find(View root,Class<T> type){if(type.isInstance(root))return type.cast(root);if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){T found=find(group.getChildAt(i),type);if(found!=null)return found;}return null;}
    private static <T extends View>void collect(View root,Class<T> type,List<T> found){if(type.isInstance(root))found.add(type.cast(root));if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)collect(group.getChildAt(i),type,found);}
    private static View text(View root,String value){if(root instanceof TextView label && value.contentEquals(label.getText()))return root;if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=text(group.getChildAt(i),value);if(found!=null)return found;}return null;}
    private static View contains(View root,String value){if(root instanceof TextView label && label.getText().toString().contains(value))return root;if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=contains(group.getChildAt(i),value);if(found!=null)return found;}return null;}
    private static View description(View root,String value){if(value.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))return root;if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=description(group.getChildAt(i),value);if(found!=null)return found;}return null;}
}
