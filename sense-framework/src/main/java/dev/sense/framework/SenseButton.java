package dev.sense.framework;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.widget.Button;

/** Modern Android button reproducing the dark Sense control treatment. */
public class SenseButton extends Button {
    public SenseButton(Context context) {
        super(context);
        setAllCaps(false);
        setTextColor(Color.WHITE);
        setTextSize(16);
        setMinWidth(0);setMinimumWidth(0);
        setMinHeight(dp(48));setMinimumHeight(dp(48));
        setPadding(dp(12),dp(6),dp(12),dp(6));
        setStateListAnimator(null);
        GradientDrawable face=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xff626765,0xff363c39,0xff202623});
        face.setCornerRadius(dp(5));face.setStroke(dp(1),0xff77817a);
        GradientDrawable mask=new GradientDrawable();mask.setColor(Color.WHITE);mask.setCornerRadius(dp(5));
        setBackground(new RippleDrawable(ColorStateList.valueOf(0x44c5e4ac),face,mask));
    }
    protected int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
