package dev.sense.framework;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.widget.LinearLayout;

/** Reusable bottom tab strip; tab children share the available width. */
public class SenseTabBar extends LinearLayout {
    public SenseTabBar(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setPadding(dp(4),dp(4),dp(4),dp(4));
        setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xff505650,0xff171d18}));
    }
    public SenseTabButton addTab(String title,boolean selected,Runnable action) {
        SenseTabButton tab=new SenseTabButton(getContext());
        tab.setText(title);tab.setSelected(selected);
        tab.setContentDescription(title+(selected ? ", selected" : ""));
        tab.setOnClickListener(v->action.run());
        LayoutParams params=new LayoutParams(0,dp(48),1);
        params.setMargins(dp(2),0,dp(2),0);
        addView(tab,params);
        return tab;
    }
    public SenseTabButton addIconTab(String title,int resting,int active,boolean selected,Runnable action){
        SenseTabButton tab=addTab(title,selected,action);
        android.graphics.drawable.StateListDrawable icon=new android.graphics.drawable.StateListDrawable();
        icon.addState(new int[]{android.R.attr.state_selected},getContext().getDrawable(active));icon.addState(new int[]{android.R.attr.state_pressed},getContext().getDrawable(active));icon.addState(new int[]{},getContext().getDrawable(resting));icon.setBounds(0,0,dp(28),dp(28));
        tab.setCompoundDrawablesRelative(icon,null,null,null);tab.setCompoundDrawablePadding(dp(8));tab.setGravity(android.view.Gravity.CENTER);return tab;
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
