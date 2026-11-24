package dev.sense.launcher.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.TextView;

/** Standard TextView subclass preserving native click, drag and accessibility behavior. */
public class SenseAppIcon extends TextView {
    public SenseAppIcon(Context context) {
        super(context);
        setTextSize(12);setTextColor(Color.WHITE);
        setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        setMaxLines(2);setEllipsize(android.text.TextUtils.TruncateAt.END);
        setCompoundDrawablePadding(dp(6));setPadding(dp(4),dp(12),dp(4),dp(8));
        setShadowLayer(dp(1),0,dp(1),Color.BLACK);
        GradientDrawable mask=new GradientDrawable();mask.setColor(Color.WHITE);mask.setCornerRadius(dp(7));
        setBackground(new RippleDrawable(ColorStateList.valueOf(0x448ab648),null,mask));
    }
    public void setApp(CharSequence label,Drawable icon) {
        setText(label);setContentDescription(label);
        icon.setBounds(0,0,dp(44),dp(44));
        setCompoundDrawables(null,icon,null,null);
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
