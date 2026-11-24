package dev.sense.launcher;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;

/** Browser routing is intercepted: tests never submit queries to an external service. */
final class SearchInstrumentation {
    private final Instrumentation test;
    private MainActivity activity;
    private Workspace workspace,original;
    private ViewGroup parent;
    private int index;
    private AlertDialog queryDialog;
    private Instrumentation.ActivityMonitor monitor;
    private SharedPreferences preferences;
    private String originalEngine;
    private boolean hadEngine;
    private Intent launched;
    private int held,clicked;
    SearchInstrumentation(Instrumentation test){this.test=test;}
    void run(){
        Bundle report=new Bundle();
        try{
            activity=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            main(()->{
                preferences=activity.getSharedPreferences(BrowserSearch.PREFERENCES,0);hadEngine=preferences.contains("engine");originalEngine=preferences.getString("engine",null);preferences.edit().remove("engine").commit();
                if(BrowserSearch.engine(activity)!=BrowserSearch.Engine.GOOGLE)throw new AssertionError("Search does not default to Google");
                String text="cats & dogs + 日本語 / ? # 😀";
                for(var engine:BrowserSearch.Engine.values()){
                    var uri=BrowserSearch.uri(engine,"  "+text+"  ");
                    if(!text.equals(uri.getQueryParameter("q")) || uri.getQueryParameterNames().size()!=1 || !"https".equals(uri.getScheme()))throw new AssertionError("Query encoding failed: "+engine);
                }
                try{BrowserSearch.uri(BrowserSearch.Engine.GOOGLE,"   ");throw new AssertionError("Empty query accepted");}catch(IllegalArgumentException expected){}
                if(!(activity.getDrawable(R.drawable.sense_search_field) instanceof android.graphics.drawable.NinePatchDrawable))throw new AssertionError("Search field lost its nine-patch metadata");
                Intent intent=BrowserSearch.intent(activity,BrowserSearch.uri(BrowserSearch.Engine.GOOGLE,"test"));
                var preferred=activity.getPackageManager().resolveActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://example.com/")).addCategory(Intent.CATEGORY_BROWSABLE),android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
                if(preferred!=null && preferred.activityInfo!=null && !"android".equals(preferred.activityInfo.packageName) && !preferred.activityInfo.packageName.equals(intent.getPackage()))throw new AssertionError("Search did not target the default browser");
                var providers=android.appwidget.AppWidgetManager.getInstance(activity).getInstalledProviders();long entries=WidgetPicker.groupedRows(activity,providers).stream().filter(WidgetPicker.Row::search).count();
                if(entries!=1)throw new AssertionError("Search picker entry missing or duplicated");
                original=find(activity.getWindow().getDecorView(),Workspace.class);parent=(ViewGroup)original.getParent();index=parent.indexOfChild(original);
                var prefs=activity.getSharedPreferences("search_instrumentation",0);prefs.edit().clear().putString("items","[]").putInt("pages",1).putInt("currentPage",1).commit();
                workspace=new Workspace(activity,new SenseWidgetHost(activity,9040),prefs);var params=original.getLayoutParams();parent.removeView(original);parent.addView(workspace,index,params);workspace.addSearch();
                if(workspace.items.optJSONObject(0).optInt("w")!=4 || workspace.items.optJSONObject(0).optInt("h")!=1)throw new AssertionError("Search widget has incorrect grid span");
                workspace.render();if(find(workspace,SenseSearchView.class)==null || !workspace.prefs.getString("items","").contains("search"))throw new AssertionError("Search placement did not persist");
                BrowserSearch.setEngine(activity,BrowserSearch.Engine.BING);
                if(BrowserSearch.engine(activity)!=BrowserSearch.Engine.BING || text(workspace,"Search with Bing")==null)throw new AssertionError("Search engine setting did not update widget");
            });
            monitor=new Instrumentation.ActivityMonitor(){@Override public Instrumentation.ActivityResult onStartActivity(Intent intent){if(Intent.ACTION_VIEW.equals(intent.getAction())){launched=new Intent(intent);return new Instrumentation.ActivityResult(Activity.RESULT_OK,null);}return null;}};test.addMonitor(monitor);
            main(()->{
                queryDialog=BrowserSearch.showQuery(activity);EditText field=find(queryDialog.getWindow().getDecorView(),EditText.class);
                if(queryDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled())throw new AssertionError("Empty search button enabled");
                field.setText("cats & dogs + 日本語");field.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
                if(launched==null || !"www.bing.com".equals(launched.getData().getHost()) || !"cats & dogs + 日本語".equals(launched.getData().getQueryParameter("q")))throw new AssertionError("IME search did not dispatch configured browser URL");
                if(queryDialog.isShowing())throw new AssertionError("Query dialog did not close on search");
            });
            test.waitForIdleSync();
            SenseSearchView[] widget={null};float[] point=new float[2];long down=SystemClock.uptimeMillis();
            main(()->{
                widget[0]=find(workspace,SenseSearchView.class);View field=text(widget[0],"Search with Bing");field.setOnClickListener(v->clicked++);widget[0].setOnLongClickListener(v->{held++;return true;});
                int[] child=new int[2],origin=new int[2];field.getLocationOnScreen(child);widget[0].getLocationOnScreen(origin);point[0]=child[0]-origin[0]+field.getWidth()/2f;point[1]=child[1]-origin[1]+field.getHeight()/2f;
                touch(widget[0],down,MotionEvent.ACTION_DOWN,point);
            });Thread.sleep(ViewConfiguration.getLongPressTimeout()+180);
            main(()->{
                touch(widget[0],down,MotionEvent.ACTION_UP,point);if(held!=1 || clicked!=0)throw new AssertionError("Search consumed launcher long press");
                Bitmap preview=workspace.pagePreview(workspace.page,308,460);boolean visible=false;for(int y=0;y<preview.getHeight();y+=4)for(int x=0;x<preview.getWidth();x+=4)if(Color.alpha(preview.getPixel(x,y))>0)visible=true;preview.recycle();if(!visible)throw new AssertionError("Search missing from Leap preview");
            });
            cleanup();report.putString("result","Google default, four engine URLs, Unicode/reserved-character encoding, default-browser routing, real nine-patch artwork, search settings, persisted 4x1 placement, intercepted IME launch, long-press priority and Leap preview passed");test.finish(Activity.RESULT_OK,report);
        }catch(Throwable error){try{cleanup();}catch(Throwable ignored){}report.putString("error",android.util.Log.getStackTraceString(error));test.finish(Activity.RESULT_CANCELED,report);}
    }
    private void cleanup(){if(monitor!=null)test.removeMonitor(monitor);main(()->{if(queryDialog!=null)queryDialog.dismiss();if(parent!=null && workspace!=null && workspace.getParent()==parent){var params=workspace.getLayoutParams();parent.removeView(workspace);parent.addView(original,index,params);workspace.prefs.edit().clear().commit();}if(preferences!=null){var edit=preferences.edit();if(hadEngine)edit.putString("engine",originalEngine);else edit.remove("engine");edit.commit();}});}
    private void touch(View view,long down,int action,float[] point){MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,point[0],point[1],0);view.dispatchTouchEvent(event);event.recycle();}
    private void main(Runnable action){Throwable[] error={null};test.runOnMainSync(()->{try{action.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError(error[0]);}
    private static <T extends View>T find(View root,Class<T> type){if(type.isInstance(root))return type.cast(root);if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){T found=find(group.getChildAt(i),type);if(found!=null)return found;}return null;}
    private static View text(View root,String value){if(root instanceof TextView label && value.contentEquals(label.getText()))return root;if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=text(group.getChildAt(i),value);if(found!=null)return found;}return null;}
}
