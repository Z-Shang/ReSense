package dev.sense.framework;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.view.Gravity;
import android.widget.*;


/** Native preference controls using the original HTC framework artwork. */
public final class SensePreferences {
    private SensePreferences() {}
    private static int dp(Context c,int value){return Math.round(value*c.getResources().getDisplayMetrics().density);}
    public static Drawable row(Context c){
        StateListDrawable states=new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed},c.getDrawable(R.drawable.sense_list_selector_background_pressed));
        states.addState(new int[]{android.R.attr.state_focused},c.getDrawable(R.drawable.sense_list_selector_background_pressed));
        states.addState(new int[]{},new ColorDrawable(Color.WHITE));return states;
    }
    public static void text(TextView view){view.setTextColor(0xff444444);view.setPadding(dp(view.getContext(),16),dp(view.getContext(),10),dp(view.getContext(),16),dp(view.getContext(),10));}
    public static void section(TextView view){
        view.setTextSize(14);view.setTextColor(Color.WHITE);view.setTypeface(null,Typeface.BOLD);
        view.setPadding(dp(view.getContext(),16),dp(view.getContext(),7),dp(view.getContext(),16),dp(view.getContext(),7));
        view.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xff676767,0xff414141}));
    }
    public static void input(EditText view){
        SenseStyle.input(view);view.setPadding(dp(view.getContext(),12),dp(view.getContext(),10),dp(view.getContext(),12),dp(view.getContext(),10));view.setMinimumHeight(dp(view.getContext(),48));
    }
    private static Drawable checkbox(Context c){
        StateListDrawable states=new StateListDrawable();
        states.addState(new int[]{-android.R.attr.state_enabled,android.R.attr.state_checked},c.getDrawable(R.drawable.sense_common_checkbox_on_disable));
        states.addState(new int[]{-android.R.attr.state_enabled},c.getDrawable(R.drawable.sense_common_checkbox_off_disable));
        states.addState(new int[]{android.R.attr.state_pressed,android.R.attr.state_checked},c.getDrawable(R.drawable.sense_common_checkbox_on_pressed));
        states.addState(new int[]{android.R.attr.state_pressed},c.getDrawable(R.drawable.sense_common_checkbox_off_pressed));
        states.addState(new int[]{android.R.attr.state_checked},c.getDrawable(R.drawable.sense_common_checkbox_on));states.addState(new int[]{},c.getDrawable(R.drawable.sense_common_checkbox_off));
        states.setBounds(0,0,dp(c,32),dp(c,32));return states;
    }
    public static final class Toggle extends Switch {
        public Toggle(Context c){
            super(c);setTextColor(0xff222222);setTextSize(16);setMinHeight(dp(c,64));setGravity(Gravity.CENTER_VERTICAL);setPadding(dp(c,16),dp(c,12),dp(c,16),dp(c,12));
            // Preserve Switch state, click and accessibility behavior; use HTC's
            // checked indicator in the trailing text drawable slot.
            setThumbDrawable(null);setTrackDrawable(null);setShowText(false);setSwitchMinWidth(0);setSwitchPadding(0);
            setCompoundDrawablesRelative(null,null,checkbox(c),null);setCompoundDrawablePadding(dp(c,16));setBackground(row(c));
        }
    }
    public static final class Choice extends RadioButton {
        public Choice(Context c){
            super(c);setTextSize(16);setTextColor(0xff222222);setMinHeight(dp(c,52));setGravity(Gravity.CENTER_VERTICAL);setPadding(dp(c,16),dp(c,8),dp(c,16),dp(c,8));setCompoundDrawablePadding(dp(c,12));setButtonTintList(null);
            StateListDrawable states=new StateListDrawable();
            states.addState(new int[]{-android.R.attr.state_enabled,android.R.attr.state_checked},c.getDrawable(R.drawable.sense_common_radiobutton_on_disable));states.addState(new int[]{-android.R.attr.state_enabled},c.getDrawable(R.drawable.sense_common_radiobutton_off_disable));
            states.addState(new int[]{android.R.attr.state_pressed,android.R.attr.state_checked},c.getDrawable(R.drawable.sense_common_radiobutton_selected));states.addState(new int[]{android.R.attr.state_pressed},c.getDrawable(R.drawable.sense_common_radiobutton_off_selected));
            states.addState(new int[]{android.R.attr.state_checked},c.getDrawable(R.drawable.sense_common_radiobutton_on));states.addState(new int[]{},c.getDrawable(R.drawable.sense_common_radiobutton_off));setButtonDrawable(new InsetDrawable(states,dp(c,16),0,dp(c,12),0));setPadding(0,dp(c,8),dp(c,16),dp(c,8));setBackground(row(c));
        }
    }
    public static class Row extends Button {
        public Row(Context c){
            super(c);setAllCaps(false);setTextColor(new ColorStateList(new int[][]{{-android.R.attr.state_enabled},{}},new int[]{0xff999999,0xff222222}));setTextSize(17);setGravity(Gravity.CENTER_VERTICAL|Gravity.START);setMinHeight(dp(c,56));setMinimumHeight(dp(c,56));setMinWidth(0);setMinimumWidth(0);setPadding(dp(c,16),dp(c,12),dp(c,16),dp(c,12));setBackgroundTintList(null);setStateListAnimator(null);setBackground(row(c));setCompoundDrawablesRelativeWithIntrinsicBounds(null,null,c.getDrawable(R.drawable.sense_row_arrow),null);setCompoundDrawablePadding(dp(c,12));
        }
    }
    public static final class ButtonControl extends Button {
        public ButtonControl(Context c){super(c);setAllCaps(false);setTextColor(0xff222222);setTextSize(16);setMinHeight(dp(c,48));setMinimumHeight(dp(c,48));setMinWidth(0);setMinimumWidth(0);setBackgroundTintList(null);setStateListAnimator(null);setBackgroundResource(R.drawable.sense_button_background);setPadding(dp(c,12),dp(c,8),dp(c,12),dp(c,8));}
    }
}
