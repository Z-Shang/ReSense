package dev.sense.lockscreen;

import android.content.*;
import android.content.pm.*;
import android.graphics.drawable.Drawable;
import android.provider.MediaStore;
import java.util.*;

/** Only configured launcher components or fixed platform actions can run after authentication. */
final class LockShortcuts {
    static final String[] NAMES={"Phone","Messages","Camera","Browser"};
    static Intent intent(Context c,int slot){
        if(slot<0||slot>=4)return null;
        String value=c.getSharedPreferences("lockscreen",0).getString("shortcut:"+slot,"");
        if(!value.isBlank()){ComponentName component=ComponentName.unflattenFromString(value);return component==null?null:new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);}
        String own=slot==0?"dev.sense.phone":slot==1?"dev.sense.messages":null;
        if(own!=null){Intent launch=c.getPackageManager().getLaunchIntentForPackage(own);if(launch!=null)return launch;}
        return switch(slot){case 0->new Intent(Intent.ACTION_DIAL);case 1->Intent.makeMainSelectorActivity(Intent.ACTION_MAIN,Intent.CATEGORY_APP_MESSAGING);case 2->new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);default->Intent.makeMainSelectorActivity(Intent.ACTION_MAIN,Intent.CATEGORY_APP_BROWSER);};
    }
    static String name(Context c,int slot){Intent intent=intent(c,slot);String custom=c.getSharedPreferences("lockscreen",0).getString("shortcut:"+slot,"");if(!custom.isBlank()&&intent!=null){var info=c.getPackageManager().resolveActivity(intent,0);if(info!=null)return info.loadLabel(c.getPackageManager()).toString();}return NAMES[slot];}
    static Drawable icon(Context c,int slot){
        if(!c.getSharedPreferences("lockscreen",0).getString("shortcut:"+slot,"").isBlank())try{Intent target=intent(c,slot);if(target!=null)return c.getPackageManager().getActivityIcon(target);}catch(PackageManager.NameNotFoundException ignored){}
        int id=c.getResources().getIdentifier("ls_"+new String[]{"phone","messages","camera","browser"}[slot],"drawable",c.getPackageName());return id==0?c.getDrawable(android.R.drawable.sym_def_app_icon):c.getDrawable(id);
    }
    record App(String title,String component,Drawable icon){}
    static List<App> apps(Context c){var result=new ArrayList<App>();for(var info:c.getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0))if(info.activityInfo.exported)result.add(new App(info.loadLabel(c.getPackageManager()).toString(),new ComponentName(info.activityInfo.packageName,info.activityInfo.name).flattenToString(),info.loadIcon(c.getPackageManager())));result.sort(Comparator.comparing(App::title,String.CASE_INSENSITIVE_ORDER));return result;}
}
