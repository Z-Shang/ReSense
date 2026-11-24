package dev.sense.launcher;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;

/** Browse/cancel only: no provider binding or persistent workspace edits. */
final class WidgetPickerInstrumentation {
    private final Instrumentation test;
    WidgetPickerInstrumentation(Instrumentation test){this.test=test;}
    private TextView find(View view,String text){if(view instanceof TextView t&&text.contentEquals(t.getText()))return t;if(view instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){TextView found=find(g.getChildAt(i),text);if(found!=null)return found;}return null;}
    private View description(View view,String text){if(text.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){View found=description(g.getChildAt(i),text);if(found!=null)return found;}return null;}
    void run(){Bundle report=new Bundle();WidgetPickerActivity activity=null;try{
        var context=test.getTargetContext();int[] before=new AppWidgetHost(context,1024).getAppWidgetIds();
        var families=WidgetPicker.families(context,AppWidgetManager.getInstance(context).getInstalledProviders());
        var music=families.stream().filter(f->f.key().equals("sense:music")).findFirst().orElseThrow();
        if(music.styles().size()!=3||music.styles().get(0).musicRows()!=1||music.styles().get(2).musicRows()!=4)throw new AssertionError("Music styles missing or unordered");
        var providers=WidgetPicker.providerGroups(context,families);
        var sense=providers.stream().filter(p->p.families().stream().anyMatch(f->f.key().equals("sense:music"))).findFirst().orElseThrow();
        if(sense.families().stream().noneMatch(f->f.key().equals("sense:search")))throw new AssertionError("Sense widgets split across provider groups");
        if(providers.stream().mapToInt(p->p.families().size()).sum()!=families.size())throw new AssertionError("Grouping lost or duplicated widget families");
        for(var provider:providers)for(var item:provider.families())for(var style:item.styles())if(style.info()!=null && !provider.key().equals(style.info().provider.getPackageName()+"/"+style.info().getProfile()))throw new AssertionError("Provider/profile widgets mixed");
        activity=(WidgetPickerActivity)test.startActivitySync(new Intent(context,WidgetPickerActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));final var screen=activity;
        long deadline=SystemClock.uptimeMillis()+8000;boolean[] ready={false};while(!ready[0]&&SystemClock.uptimeMillis()<deadline){test.runOnMainSync(()->ready[0]=find(screen.getWindow().getDecorView(),"HTC Sense Music")!=null);SystemClock.sleep(50);}if(!ready[0])throw new AssertionError("Catalogue never loaded");
        test.runOnMainSync(()->{
            View root=screen.getWindow().getDecorView();View heading=root.findViewWithTag("widget-provider:"+sense.key());
            if(heading==null)throw new AssertionError("Provider section missing");heading.performClick();
            if(find(root,"HTC Sense Music")!=null || find(root,"HTC Sense Search")!=null)throw new AssertionError("Provider collapse left widgets visible");
            root.findViewWithTag("widget-provider:"+sense.key()).performClick();
            if(find(root,"HTC Sense Music")==null || find(root,"HTC Sense Search")==null)throw new AssertionError("Provider expansion did not restore widgets");
        });
        test.runOnMainSync(()->find(screen.getWindow().getDecorView(),"HTC Sense Music").getParent().requestLayout());
        test.runOnMainSync(()->((View)find(screen.getWindow().getDecorView(),"HTC Sense Music").getParent().getParent()).performClick());test.waitForIdleSync();SystemClock.sleep(250);
        test.runOnMainSync(()->{View root=screen.getWindow().getDecorView();if(find(root,"1 / 3")==null||find(root,"Details")==null||find(root,"Select")==null)throw new AssertionError("Style chooser incomplete");description(root,"Next style").performClick();});
        test.waitForIdleSync();SystemClock.sleep(1000);
        try(var output=new java.io.FileOutputStream(new java.io.File(context.getFilesDir(),"widget-picker-layout.png"))){var bitmap=test.getUiAutomation().takeScreenshot();if(bitmap!=null){int visible=0;for(int y=bitmap.getHeight()/4;y<bitmap.getHeight()*3/4;y+=8)for(int x=0;x<bitmap.getWidth();x+=8){int pixel=bitmap.getPixel(x,y);if(android.graphics.Color.red(pixel)>40||android.graphics.Color.green(pixel)>40||android.graphics.Color.blue(pixel)>40)visible++;}if(visible<100)throw new AssertionError("Blank widget preview");bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);bitmap.recycle();}}

        test.runOnMainSync(()->{View root=screen.getWindow().getDecorView();if(find(root,"2 / 3")==null)throw new AssertionError("Next style failed");screen.onBackPressed();});test.waitForIdleSync();SystemClock.sleep(250);
        test.runOnMainSync(()->{if(find(screen.getWindow().getDecorView(),"Widgets")==null)throw new AssertionError("Back did not return to catalogue");screen.finish();});
        if(!java.util.Arrays.equals(before,new AppWidgetHost(context,1024).getAppWidgetIds()))throw new AssertionError("Browsing allocated widget IDs");
        report.putString("result","PASS: provider/profile grouping, collapsible provider sections, Music sizes, preview navigation, Back, passive ID lifecycle");test.finish(Activity.RESULT_OK,report);
    }catch(Throwable failure){report.putString("error",android.util.Log.getStackTraceString(failure));test.finish(Activity.RESULT_CANCELED,report);}finally{if(activity!=null){final var screen=activity;test.runOnMainSync(screen::finish);}}}
}
