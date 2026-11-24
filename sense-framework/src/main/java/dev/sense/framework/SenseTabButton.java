package dev.sense.framework;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;

/** Accessible tab button with a persistent green selection rail. */
public class SenseTabButton extends SenseButton {
    private final Paint selection=new Paint(Paint.ANTI_ALIAS_FLAG);
    public SenseTabButton(Context context) {
        super(context);
        setTextSize(14);
        setSingleLine(true);
        selection.setColor(0xffa7d75d);
    }
    @Override public void setSelected(boolean selected) {
        super.setSelected(selected);
        setTextColor(selected ? 0xffd9f3b6 : 0xffe5e9e4);
        invalidate();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if(isSelected())canvas.drawRect(dp(8),getHeight()-dp(4),getWidth()-dp(8),getHeight(),selection);
    }
    @Override public CharSequence getAccessibilityClassName(){return "android.widget.TabWidget";}
}
