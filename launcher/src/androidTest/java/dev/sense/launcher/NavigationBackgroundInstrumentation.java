package dev.sense.launcher;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;

/** Actual settings toggle, system-bar visibility and dock geometry in both modes. */
final class NavigationBackgroundInstrumentation {
    private final Instrumentation test;
    NavigationBackgroundInstrumentation(Instrumentation test){this.test=test;}
    private void main(Runnable action){Throwable[] failure={null};test.runOnMainSync(()->{try{action.run();}catch(Throwable error){failure[0]=error;}});if(failure[0]!=null)throw new AssertionError(failure[0]);}
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    void run(){Bundle result=new Bundle();Context c=test.getTargetContext();var prefs=c.getSharedPreferences("workspace",0);boolean had=prefs.contains(WorkspaceSettings.HIDE_NAVIGATION_BAR),before=WorkspaceSettings.hideNavigationBar(prefs);MainActivity activity=null;int code=Activity.RESULT_CANCELED;
        try{
            prefs.edit().putBoolean(WorkspaceSettings.HIDE_NAVIGATION_BAR,true).commit();
            activity=(MainActivity)test.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));test.waitForIdleSync();checkNavigation(activity,true);final MainActivity home=activity;
            int[] hiddenHeight={0};main(()->{BentDockLayout dock=findDock(home.getWindow().getDecorView());check(dock!=null,"Home dock missing");int[] origin=new int[2];dock.getLocationOnScreen(origin);int bottom=home.getWindowManager().getCurrentWindowMetrics().getBounds().bottom;check(origin[1]+dock.getHeight()==bottom,"Dock does not reach screen edge");hiddenHeight[0]=dock.getHeight();});
            setFromSettings(false);checkNavigation(activity,false);awaitHeight(activity,hiddenHeight[0],true);
            setFromSettings(true);checkNavigation(activity,true);awaitHeight(activity,hiddenHeight[0],false);
            main(activity::showApps);test.waitForIdleSync();checkNavigation(activity,true);
            setFromSettings(false);checkNavigation(activity,false);
            result.putString("result","PASS: settings navigation toggle persists, home/drawer show and hide navigation with status bar visible, hidden dock removes gesture spacer and reaches bottom edge");code=Activity.RESULT_OK;
        }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));}finally{
            var restore=prefs.edit();if(had)restore.putBoolean(WorkspaceSettings.HIDE_NAVIGATION_BAR,before);else restore.remove(WorkspaceSettings.HIDE_NAVIGATION_BAR);restore.commit();if(activity!=null){final MainActivity home=activity;main(home::finish);}
        }test.finish(code,result);
    }
    private void setFromSettings(boolean hide){LauncherSettingsActivity settings=(LauncherSettingsActivity)test.startActivitySync(new Intent(test.getTargetContext(),LauncherSettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));try{main(()->{View found=findText(settings.getWindow().getDecorView(),"Hide system navigation bar");check(found instanceof Switch,"Navigation visibility setting is missing");Switch toggle=(Switch)found;if(toggle.isChecked()!=hide)toggle.performClick();check(WorkspaceSettings.hideNavigationBar(settings.getSharedPreferences("workspace",0))==hide,"Navigation setting was not saved");});}finally{main(settings::finishAndRemoveTask);}test.waitForIdleSync();}
    private void checkNavigation(MainActivity activity,boolean hidden)throws Exception{boolean[] matches={false};long end=SystemClock.uptimeMillis()+5000;while(!matches[0]&&SystemClock.uptimeMillis()<end){main(()->{WindowInsets insets=activity.getWindow().getDecorView().getRootWindowInsets();matches[0]=insets!=null&&insets.isVisible(WindowInsets.Type.navigationBars())!=hidden;});Thread.sleep(40);}check(matches[0],"Launcher navigation visibility did not follow its setting");main(()->check(activity.getWindow().getDecorView().getRootWindowInsets().isVisible(WindowInsets.Type.statusBars()),"Launcher status bar was hidden"));}
    private void awaitHeight(MainActivity activity,int hiddenHeight,boolean shown)throws Exception{boolean[] matches={false};long end=SystemClock.uptimeMillis()+4000;while(!matches[0]&&SystemClock.uptimeMillis()<end){main(()->{BentDockLayout dock=findDock(activity.getWindow().getDecorView());matches[0]=dock!=null&&(shown?dock.getHeight()>hiddenHeight:dock.getHeight()==hiddenHeight);});Thread.sleep(40);}check(matches[0],shown?"Visible navigation did not restore safe spacing":"Hidden navigation left a gap under the dock");}
    private View findText(View view,String label){if(view instanceof TextView text&&label.contentEquals(text.getText()))return view;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=findText(group.getChildAt(i),label);if(found!=null)return found;}return null;}
    private BentDockLayout findDock(View view){if(view instanceof BentDockLayout dock)return dock;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){BentDockLayout found=findDock(group.getChildAt(i));if(found!=null)return found;}return null;}
}
