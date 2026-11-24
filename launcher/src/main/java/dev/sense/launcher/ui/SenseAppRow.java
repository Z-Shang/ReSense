package dev.sense.launcher.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.Gravity;

/** Sense-styled list alternative sharing app-cell interaction behavior. */
public class SenseAppRow extends SenseAppIcon {
    private final Paint divider=new Paint(Paint.ANTI_ALIAS_FLAG);
    public SenseAppRow(Context context) {
        super(context);
        setGravity(Gravity.CENTER_VERTICAL|Gravity.START);
        setTextSize(17);setSingleLine(true);
        setPadding(dp(16),dp(8),dp(16),dp(8));
        setCompoundDrawablePadding(dp(16));
        divider.setColor(0xff424742);
    }
    @Override public void setApp(CharSequence label,Drawable icon) {
        setText(label);setContentDescription(label);
        icon.setBounds(0,0,dp(44),dp(44));
        setCompoundDrawablesRelative(icon,null,null,null);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawRect(dp(16),getHeight()-dp(1),getWidth()-dp(16),getHeight(),divider);
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
