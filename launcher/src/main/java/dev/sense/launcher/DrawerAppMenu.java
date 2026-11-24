package dev.sense.launcher;

import android.content.*;
import android.content.pm.ApplicationInfo;
import android.graphics.Rect;
import android.net.Uri;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

/** Icon-anchored actions. Keep the initial hold stream so moving can still start a home drag. */
final class DrawerAppMenu {
    private final MainActivity activity;
    private PopupWindow popup;
    private View anchor;
    private ViewTreeObserver observer;
    private ViewTreeObserver.OnPreDrawListener watch;
    private boolean touching;
    private float downX,downY;
    DrawerAppMenu(MainActivity activity){this.activity=activity;}
    void bind(View icon,AppCatalog.Entry app,Runnable drag,Runnable add){
        icon.setTag(app.component());
        icon.setOnLongClickListener(v->{show(icon,app,add);return true;});
        icon.setOnTouchListener((v,event)->{
            switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN -> {touching=true;downX=event.getRawX();downY=event.getRawY();}
                case MotionEvent.ACTION_MOVE -> {
                    if(anchor==icon&&isShowing()&&touching&&Math.hypot(event.getRawX()-downX,event.getRawY()-downY)>ViewConfiguration.get(activity).getScaledTouchSlop()){
                        touching=false;dismiss();drag.run();return true;
                    }
                }
                case MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL -> {
                    touching=false;
                    if(anchor==icon&&isShowing())icon.post(()->{
                        if(anchor==icon&&isShowing()&&!popup.isFocusable()){popup.setFocusable(true);popup.update();}
                    });
                }
            }
            return false;
        });
    }
    private int dp(int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    void show(View icon,AppCatalog.Entry app,Runnable add){
        dismiss();if(!icon.isAttachedToWindow()||!icon.isShown())return;
        Rect visible=new Rect();if(!icon.getGlobalVisibleRect(visible))return;
        LinearLayout content=new LinearLayout(activity);content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(4),dp(5),dp(4),dp(5));
        action(content,"App info",()->activity.open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.fromParts("package",app.component().getPackageName(),null))));
        if(canUninstall(app.component().getPackageName()))action(content,"Uninstall",()->activity.open(new Intent(Intent.ACTION_DELETE,Uri.fromParts("package",app.component().getPackageName(),null))));
        action(content,"Add to Home screen",add);
        Rect frame=new Rect();icon.getWindowVisibleDisplayFrame(frame);
        int width=Math.min(dp(232),Math.max(dp(120),frame.width()-dp(8)));
        content.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        var background=activity.getDrawable(dev.sense.framework.R.drawable.sense_common_popupmenu);
        Rect padding=new Rect();background.getPadding(padding);
        int height=content.getMeasuredHeight()+padding.top+padding.bottom;
        PopupWindow window=new PopupWindow(content,width,height,!touching);
        window.setBackgroundDrawable(background);
        window.setOutsideTouchable(true);window.setElevation(dp(8));window.setIsLaidOutInScreen(true);window.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        int[] location=new int[2];icon.getLocationOnScreen(location);
        Rect bounds=new Rect(location[0],location[1],location[0]+icon.getWidth(),location[1]+icon.getHeight());
        if(icon instanceof TextView text){
            var images=text.getCompoundDrawablesRelative();var start=images[0];var above=images[1];
            if(start!=null){int w=start.getBounds().width(),h=start.getBounds().height();int x=icon.getLayoutDirection()==View.LAYOUT_DIRECTION_RTL?bounds.right-icon.getPaddingRight()-w:bounds.left+icon.getPaddingLeft();int y=bounds.centerY()-h/2;bounds.set(x,y,x+w,y+h);}
            else if(above!=null){int w=above.getBounds().width(),h=above.getBounds().height();int x=bounds.centerX()-w/2,y=bounds.top+icon.getPaddingTop();bounds.set(x,y,x+w,y+h);}
        }
        int left=bounds.right+dp(4);
        if(left+width>frame.right)left=bounds.left-width-dp(4);
        if(left<frame.left)left=Math.max(frame.left+dp(4),Math.min(bounds.centerX()-width/2,frame.right-width-dp(4)));
        int top=Math.max(frame.top+dp(4),Math.min(bounds.centerY()-height/2,frame.bottom-height-dp(4)));
        popup=window;anchor=icon;
        window.setOnDismissListener(()->{if(popup==window)clear();});
        watch=()->{
            if(anchor==icon){int[] now=new int[2];icon.getLocationOnScreen(now);
                if(!icon.isAttachedToWindow()||!icon.isShown()||!app.component().equals(icon.getTag())||now[0]!=location[0]||now[1]!=location[1])dismiss();}
            return true;
        };
        observer=icon.getViewTreeObserver();observer.addOnPreDrawListener(watch);
        icon.getParent().requestDisallowInterceptTouchEvent(touching);
        window.showAtLocation(icon,Gravity.TOP|Gravity.LEFT,left,top);
    }
    private void action(LinearLayout content,String label,Runnable action){
        TextView row=new TextView(activity);row.setText(label);row.setTextSize(17);row.setTextColor(0xfff5f5f5);
        row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(16),0,dp(12),0);row.setFocusable(true);row.setClickable(true);
        var background=new android.graphics.drawable.StateListDrawable();background.addState(new int[]{android.R.attr.state_pressed},activity.getDrawable(dev.sense.framework.R.drawable.sense_list_selector_background_pressed));
        background.addState(new int[]{android.R.attr.state_focused},activity.getDrawable(dev.sense.framework.R.drawable.sense_list_selector_background_pressed));row.setBackground(background);
        row.setOnClickListener(v->{dismiss();action.run();});content.addView(row,new LinearLayout.LayoutParams(-1,dp(48)));
    }
    private boolean canUninstall(String packageName){
        try{var info=activity.getPackageManager().getApplicationInfo(packageName,0);return (info.flags&ApplicationInfo.FLAG_SYSTEM)==0||(info.flags&ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)!=0;}
        catch(android.content.pm.PackageManager.NameNotFoundException ignored){return false;}
    }
    boolean isShowing(){return popup!=null&&popup.isShowing();}
    boolean dismiss(){if(popup==null)return false;popup.dismiss();return true;}
    private void clear(){
        if(observer!=null&&observer.isAlive()&&watch!=null)observer.removeOnPreDrawListener(watch);
        if(anchor!=null&&anchor.getParent()!=null)anchor.getParent().requestDisallowInterceptTouchEvent(false);
        observer=null;watch=null;popup=null;anchor=null;
    }
    View contentForTests(){return popup==null?null:popup.getContentView();}
}
