package dev.sense.launcher;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.function.BooleanSupplier;

/** Provider browsing, native previews and gestures; never binds or places a widget. */
final class WidgetPickerInstrumentation {
    private final Instrumentation test;
    private WidgetPickerActivity activity;
    WidgetPickerInstrumentation(Instrumentation test){this.test=test;}
    private void check(boolean condition,String reason){if(!condition)throw new AssertionError(reason);}
    private TextView find(View view,String text){if(view instanceof TextView t&&text.contentEquals(t.getText()))return t;if(view instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){TextView found=find(g.getChildAt(i),text);if(found!=null)return found;}return null;}
    private View description(View view,String text){if(text.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){View found=description(g.getChildAt(i),text);if(found!=null)return found;}return null;}
    private <T extends View>T type(View view,Class<T> type){if(type.isInstance(view))return type.cast(view);if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){T found=type(group.getChildAt(i),type);if(found!=null)return found;}return null;}
    private View root(){return activity.getWindow().getDecorView();}
    private void main(Runnable action){test.runOnMainSync(action);test.waitForIdleSync();}
    private void await(BooleanSupplier condition,String reason){long end=SystemClock.uptimeMillis()+10000;boolean ready=false;while(!ready&&SystemClock.uptimeMillis()<end){boolean[] value={false};main(()->value[0]=condition.getAsBoolean());ready=value[0];if(!ready)SystemClock.sleep(50);}check(ready,reason);}
    private void screenshot(String name)throws Exception{try(var output=test.getTargetContext().openFileOutput(name,0)){var bitmap=test.getUiAutomation().takeScreenshot();bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);bitmap.recycle();}}
    private void swipeLeft(){
        int[] xy=new int[2],size=new int[2];
        main(()->{HorizontalScrollView strip=type(root(),HorizontalScrollView.class);strip.getLocationOnScreen(xy);size[0]=strip.getWidth();size[1]=strip.getHeight();});
        long down=SystemClock.uptimeMillis();float start=xy[0]+size[0]*.82f,y=xy[1]+size[1]*.5f;
        MotionEvent event=MotionEvent.obtain(down,down,MotionEvent.ACTION_DOWN,start,y,0);test.sendPointerSync(event);event.recycle();
        for(int step=1;step<=10;step++){SystemClock.sleep(18);event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,start-size[0]*.64f*step/10,y,0);test.sendPointerSync(event);event.recycle();}
        main(()->check(type(root(),HorizontalScrollView.class).getScrollX()>size[0]*1.25f,"Preview did not follow the finger"));
        event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,start-size[0]*.64f,y,0);test.sendPointerSync(event);event.recycle();
    }
    void run(){
        Bundle report=new Bundle();
        try{
            var context=test.getTargetContext();int[] before=new AppWidgetHost(context,1024).getAppWidgetIds();
            var original=WidgetPicker.families(context,AppWidgetManager.getInstance(context).getInstalledProviders());
            var grouped=WidgetPicker.providerGroups(context,original);var providers=WidgetPicker.providerFamilies(context,original);
            check(providers.size()==grouped.size(),"Not one entry per provider/profile");
            check(providers.stream().mapToInt(p->p.styles().size()).sum()==original.stream().mapToInt(f->f.styles().size()).sum(),"Provider grouping lost widget styles");
            for(var provider:providers)for(var style:provider.styles())if(style.info()!=null)check(provider.key().equals(style.info().provider.getPackageName()+"/"+style.info().getProfile()),"Mixed provider/profile styles");
            var sense=providers.stream().filter(p->p.styles().stream().anyMatch(s->s.music())).findFirst().orElseThrow();int count=sense.styles().size();
            check(sense.styles().get(0).musicRows()==1&&sense.styles().get(2).musicRows()==4,"Music size ordering changed");
            activity=(WidgetPickerActivity)test.startActivitySync(new Intent(context,WidgetPickerActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await(()->root().findViewWithTag("widget-provider:"+sense.key())!=null,"Provider catalogue did not load");
            main(()->{check(find(root(),"HTC Sense Music")==null,"Catalogue still exposes intermediate family rows");root().findViewWithTag("widget-provider:"+sense.key()).performClick();});
            await(()->find(root(),"1 / "+count)!=null,"Provider did not open horizontal previews directly");
            main(()->{check(find(root(),"HTC Sense Music · 4 × 1")!=null,"Widget caption/size missing");description(root(),"Next style").performClick();});
            await(()->find(root(),"2 / "+count)!=null&&Math.abs(type(root(),HorizontalScrollView.class).getScrollX()-type(root(),HorizontalScrollView.class).getWidth())<3,"Arrow did not settle on second preview");
            swipeLeft();await(()->find(root(),"3 / "+count)!=null,"Swipe skipped a preview or did not advance");
            var monitor=test.addMonitor(WidgetPickerActivity.class.getName(),null,false);
            try{main(activity::recreate);Activity recreated=test.waitForMonitorWithTimeout(monitor,5000);check(recreated instanceof WidgetPickerActivity,"Picker recreation failed");activity=(WidgetPickerActivity)recreated;await(()->find(root(),"3 / "+count)!=null,"Provider/preview position lost during recreation");}finally{test.removeMonitor(monitor);}
            main(activity::onBackPressed);await(()->find(root(),"Widgets")!=null,"Back did not return to providers");
            main(()->type(root(),EditText.class).setText("Weather"));
            main(()->root().findViewWithTag("widget-provider:"+sense.key()).performClick());
            await(()->find(root(),"HTC Sense Weather · 2 × 1")!=null,"Search did not open a matching preview");
            main(activity::onBackPressed);await(()->type(root(),EditText.class)!=null,"Catalogue did not return");
            main(()->{check(type(root(),EditText.class).getText().toString().equals("Weather"),"Search lost on Back");type(root(),EditText.class).setText("");});
            var messages=providers.stream().filter(p->p.styles().stream().anyMatch(s->s.info()!=null&&s.info().provider.getPackageName().equals("dev.sense.messages"))).findFirst();
            if(messages.isPresent()){
                var provider=messages.get();check(provider.styles().size()==3,"Messages widget styles missing");
                main(()->root().findViewWithTag("widget-provider:"+provider.key()).performClick());await(()->find(root(),"1 / 3")!=null,"Messages previews missing");
                main(()->check(find(root(),"Messages · 4 × 4")!=null,"Declared widget cell dimensions ignored"));
                SystemClock.sleep(800);screenshot("widget-picker-layout.png");main(activity::onBackPressed);
            }
            check(java.util.Arrays.equals(before,new AppWidgetHost(context,1024).getAppWidgetIds()),"Browsing allocated widget IDs");
            report.putString("result","PASS: single provider/profile entries, all styles in horizontal previews, finger-following swipe without skipping, arrows, recreation, search/Back, native Messages previews and passive widget ID lifecycle");
        }catch(Throwable failure){report.putString("error",android.util.Log.getStackTraceString(failure));}
        finally{if(activity!=null)main(activity::finish);}
        test.finish(report.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,report);
    }
}
