package dev.sense.launcher;

import android.content.*;
import android.graphics.drawable.StateListDrawable;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;

/** Native Rosie's search field: browser query on tap, launcher dragging on hold. */
final class SenseSearchView extends FrameLayout {
    private final LinearLayout bar;
    private final TextView query;
    private final SharedPreferences preferences;
    private final SharedPreferences.OnSharedPreferenceChangeListener changed=(prefs,key)->updateHint();
    private boolean pending,claimed;
    private float downX,downY;
    private long downTime;
    private final Runnable hold=()->{
        if(!pending || !isAttachedToWindow() || !isShown())return;
        pending=false;
        if(performLongClick()){
            claimed=true;
            MotionEvent cancel=MotionEvent.obtain(downTime,SystemClock.uptimeMillis(),MotionEvent.ACTION_CANCEL,downX,downY,0);
            super.dispatchTouchEvent(cancel);cancel.recycle();
        }
    };
    SenseSearchView(Context context){
        super(context);preferences=context.getSharedPreferences(BrowserSearch.PREFERENCES,0);
        bar=new LinearLayout(context);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setBackgroundResource(R.drawable.sense_search_floater);bar.setPadding(dp(14),dp(13),dp(14),dp(12));
        addView(bar,new LayoutParams(-1,dp(67),Gravity.CENTER));
        query=new TextView(context);query.setSingleLine(true);query.setEllipsize(android.text.TextUtils.TruncateAt.END);query.setTextSize(18);query.setTextColor(0xff666666);query.setGravity(Gravity.CENTER_VERTICAL);
        StateListDrawable field=new StateListDrawable();field.addState(new int[]{android.R.attr.state_pressed},context.getDrawable(R.drawable.sense_search_field_pressed));field.addState(new int[]{android.R.attr.state_focused},context.getDrawable(R.drawable.sense_search_field_selected));field.addState(new int[]{},context.getDrawable(R.drawable.sense_search_field));query.setBackground(field);query.setPadding(dp(12),0,dp(48),0);
        query.setOnClickListener(v->BrowserSearch.showQuery(getContext()));bar.addView(query,new LinearLayout.LayoutParams(0,-1,1));
        ImageButton settings=new ImageButton(context);settings.setImageResource(R.drawable.sense_dock_settings);settings.setScaleType(ImageView.ScaleType.FIT_CENTER);settings.setImageTintList(android.content.res.ColorStateList.valueOf(0xff666666));settings.setPadding(dp(8),dp(8),dp(8),dp(8));settings.setBackgroundColor(android.graphics.Color.TRANSPARENT);settings.setContentDescription("Search engine settings");settings.setOnClickListener(v->BrowserSearch.showSettings(getContext()));
        LinearLayout.LayoutParams button=new LinearLayout.LayoutParams(dp(42),-1);button.leftMargin=dp(4);bar.addView(settings,button);
        setContentDescription("Sense search widget");updateHint();
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void updateHint(){query.setText("Search with "+BrowserSearch.engine(getContext()).label);query.setContentDescription(query.getText());}
    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){
        int height=Math.min(getHeight(),dp(67));bar.measure(MeasureSpec.makeMeasureSpec(Math.max(1,getWidth()),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(height,MeasureSpec.EXACTLY));bar.layout(0,(getHeight()-height)/2,getWidth(),(getHeight()+height)/2);
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();preferences.registerOnSharedPreferenceChangeListener(changed);updateHint();}
    @Override protected void onDetachedFromWindow(){clearHold();preferences.unregisterOnSharedPreferenceChangeListener(changed);super.onDetachedFromWindow();}
    private void clearHold(){pending=false;removeCallbacks(hold);}
    @Override public void cancelLongPress(){super.cancelLongPress();clearHold();}
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:clearHold();claimed=false;downX=event.getX();downY=event.getY();downTime=event.getDownTime();pending=true;postDelayed(hold,ViewConfiguration.getLongPressTimeout());break;
            case MotionEvent.ACTION_MOVE:if(Math.abs(event.getX()-downX)>ViewConfiguration.get(getContext()).getScaledTouchSlop() || Math.abs(event.getY()-downY)>ViewConfiguration.get(getContext()).getScaledTouchSlop())clearHold();break;
            case MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL,MotionEvent.ACTION_POINTER_DOWN:clearHold();break;
        }
        return claimed || super.dispatchTouchEvent(event);
    }
}
