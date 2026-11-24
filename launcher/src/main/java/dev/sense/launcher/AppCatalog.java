package dev.sense.launcher;

import android.content.*;
import android.graphics.drawable.Drawable;
import android.os.*;
import java.text.Collator;
import java.util.*;
import java.util.concurrent.Executors;

/** Process cache; package/locale changes rebuild labels and icons off the UI thread. */
final class AppCatalog {
    record Entry(ComponentName component,String label,Drawable.ConstantState icon,String section) {
        Drawable drawable(Context context){return icon==null ? context.getPackageManager().getDefaultActivityIcon() : icon.newDrawable(context.getResources()).mutate();}
    }
    private static AppCatalog instance;
    static synchronized AppCatalog get(Context context){if(instance==null)instance=new AppCatalog(context.getApplicationContext());return instance;}
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Set<Runnable> listeners=new HashSet<>();
    private List<Entry> entries=List.of();
    private boolean loaded,loading,dirty=true;
    private int revision;
    private final Runnable refresh=this::ensureLoaded;
    private final BroadcastReceiver changes=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            // An update removes the old APK before adding its replacement. Keep
            // the last good catalog until ADDED/REPLACED, avoiding a transient gap.
            if(Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction()) && intent.getBooleanExtra(Intent.EXTRA_REPLACING,false))return;
            dirty=true;revision++;main.removeCallbacks(refresh);main.postDelayed(refresh,150);}
    };
    private AppCatalog(Context context){
        this.context=context;
        IntentFilter packages=new IntentFilter();packages.addDataScheme("package");
        for(String action:new String[]{Intent.ACTION_PACKAGE_ADDED,Intent.ACTION_PACKAGE_REMOVED,Intent.ACTION_PACKAGE_CHANGED,Intent.ACTION_PACKAGE_REPLACED})packages.addAction(action);
        context.registerReceiver(changes,packages,Context.RECEIVER_NOT_EXPORTED);
        IntentFilter configuration=new IntentFilter();configuration.addAction(Intent.ACTION_LOCALE_CHANGED);configuration.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);configuration.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE);
        context.registerReceiver(changes,configuration,Context.RECEIVER_NOT_EXPORTED);
    }
    List<Entry> entries(){return entries;}
    boolean loaded(){return loaded;}
    void addListener(Runnable listener){listeners.add(listener);ensureLoaded();}
    void removeListener(Runnable listener){listeners.remove(listener);}
    void ensureLoaded(){
        if(loading || !dirty)return;
        loading=true;dirty=false;int requestedRevision=revision;
        worker.execute(()->{
            List<Entry> result=new ArrayList<>();
            try{
                var pm=context.getPackageManager();
                var apps=pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0);
                Set<ComponentName> seen=new HashSet<>();
                for(var app:apps){
                    ComponentName component=new ComponentName(app.activityInfo.packageName,app.activityInfo.name);
                    if(!seen.add(component))continue;
                    String label=app.loadLabel(pm).toString();
                    Drawable icon=app.loadIcon(pm);
                    String section=label.isBlank()?"#":label.substring(0,label.offsetByCodePoints(0,1)).toUpperCase(Locale.getDefault());
                    if(!Character.isLetter(section.codePointAt(0)))section="#";
                    result.add(new Entry(component,label,icon.getConstantState(),section));
                }
                Collator alphabet=Collator.getInstance();result.sort((a,b)->{int compare=alphabet.compare(a.label(),b.label());return compare!=0?compare:a.component().flattenToString().compareTo(b.component().flattenToString());});
                main.post(()->{
                    loading=false;
                    if(requestedRevision!=revision){dirty=true;ensureLoaded();return;}
                    entries=List.copyOf(result);loaded=true;
                    for(Runnable listener:List.copyOf(listeners))listener.run();
                });
            }catch(RuntimeException exception){main.post(()->{loading=false;dirty=true;android.util.Log.w("SenseApps","Cannot refresh app catalog",exception);});}
        });
    }
}
