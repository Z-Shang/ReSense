package dev.sense.launcher;

import android.content.ComponentName;
import android.content.Context;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Successful launches ordered newest first; one-screen eviction happens in the viewport. */
final class AppRecency {
    private AppRecency() {}
    static void record(Context context,ComponentName component) {
        var prefs=context.getSharedPreferences("app_recency",0);
        long previous=prefs.getLong("_clock",0);
        long next=Math.max(System.currentTimeMillis(),previous==Long.MAX_VALUE?previous:previous+1);
        prefs.edit().putLong(component.flattenToString(),next).putLong("_clock",next).apply();
    }
    static List<AppCatalog.Entry> ordered(Context context,List<AppCatalog.Entry> catalog) {
        Map<String,?> recent=context.getSharedPreferences("app_recency",0).getAll();
        Map<String,?> legacy=context.getSharedPreferences("app_launches",0).getAll();
        ArrayList<AppCatalog.Entry> result=new ArrayList<>();
        for(var app:catalog)if(value(recent,app)>0 || value(legacy,app)>0)result.add(app);
        // Old counts contain no chronology. Keep those entries in catalog order,
        // behind apps with known recency, until subsequent launches establish order.
        result.sort(Comparator.comparingLong((AppCatalog.Entry app)->value(recent,app)).reversed());
        return result;
    }
    private static long value(Map<String,?> values,AppCatalog.Entry app) {
        Object value=values.get(app.component().flattenToString());return value instanceof Number n?n.longValue():0;
    }
}
