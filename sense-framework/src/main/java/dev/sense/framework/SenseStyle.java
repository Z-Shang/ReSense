package dev.sense.framework;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.view.Gravity;
import android.widget.*;

/** Native controls styled with the original People/framework drawables. */
public final class SenseStyle {
    public static int dp(Context c,float value){return Math.round(value*c.getResources().getDisplayMetrics().density);}
    public static TextView text(Context c,String value,int size,int color){TextView v=new TextView(c);v.setText(value);v.setTextSize(size);v.setTextColor(color);return v;}
    public static GradientDrawable bar(){return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xff646964,0xff353b36,0xff242a25});}
    public static TextView section(Context c,String value){TextView v=text(c,value,13,0xffffffff);v.setTypeface(null,Typeface.BOLD);v.setPadding(dp(c,14),dp(c,5),dp(c,14),dp(c,5));v.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xff676767,0xff414141}));return v;}
    public static TextView action(Context c,String label,Runnable action){
        TextView v=text(c,label,15,0xffffffff);v.setGravity(Gravity.CENTER);v.setFocusable(true);v.setBackground(background(c,R.drawable.sense_common_tab_button_center_rest,R.drawable.sense_common_tab_button_center_selected,R.drawable.sense_common_tab_button_center_press));v.setPadding(dp(c,12),0,dp(c,12),0);v.setOnClickListener(view->action.run());return v;
    }
    public static StateListDrawable background(Context c,int rest,int selected,int pressed){StateListDrawable d=new StateListDrawable();d.addState(new int[]{android.R.attr.state_pressed},c.getDrawable(pressed));d.addState(new int[]{android.R.attr.state_selected},c.getDrawable(selected));d.addState(new int[]{},c.getDrawable(rest));return d;}
    public static ImageButton icon(Context c,int resource,String description,Runnable action){ImageButton b=new ImageButton(c);b.setImageResource(resource);b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);b.setPadding(dp(c,10),dp(c,10),dp(c,10),dp(c,10));b.setContentDescription(description);b.setTooltipText(description);b.setBackground(background(c,R.drawable.sense_common_titlebar_btn_rest,R.drawable.sense_common_titlebar_btn_selected,R.drawable.sense_common_titlebar_btn_press));b.setOnClickListener(v->action.run());return b;}
    public static StateListDrawable tabBackground(Context c,int position){
        return switch(position){
            case 0 -> background(c,R.drawable.sense_common_tab_button_left_rest,R.drawable.sense_common_tab_button_left_selected,R.drawable.sense_common_tab_button_left_press);
            case 2 -> background(c,R.drawable.sense_common_tab_button_right_rest,R.drawable.sense_common_tab_button_right_selected,R.drawable.sense_common_tab_button_right_press);
            default -> background(c,R.drawable.sense_common_tab_button_center_rest,R.drawable.sense_common_tab_button_center_selected,R.drawable.sense_common_tab_button_center_press);
        };
    }
    public static TextView tab(Context c,String label,int position){
        TextView view=text(c,label,15,0xffffffff);view.setGravity(Gravity.CENTER);view.setFocusable(true);view.setBackground(tabBackground(c,position));view.setPadding(dp(c,8),0,dp(c,8),0);return view;
    }
    public static void list(ListView list){
        Context c=list.getContext();list.setDivider(c.getDrawable(R.drawable.sense_common_list_divider));list.setDividerHeight(dp(c,1));list.setSelector(R.drawable.sense_list_selector_background_pressed);list.setDrawSelectorOnTop(true);list.setCacheColorHint(0x00000000);
    }
    public static void input(EditText view){
        Context c=view.getContext();StateListDrawable states=new StateListDrawable();states.addState(new int[]{android.R.attr.state_focused},c.getDrawable(R.drawable.sense_common_inputfield_selected));states.addState(new int[]{},c.getDrawable(R.drawable.sense_common_inputfield_rest));view.setBackgroundTintList(null);view.setBackground(states);view.setTextColor(0xff222222);view.setHintTextColor(0xff777777);view.setTextSize(16);view.setPadding(dp(c,12),0,dp(c,12),0);
    }
    private SenseStyle(){}
}
