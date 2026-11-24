package dev.sense.framework;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.widget.ImageButton;
import android.widget.ImageView;

/** Original HTC header artwork over a native, accessible 48 dp touch target. */
public final class SenseHeaderIconButton extends ImageButton {
    public SenseHeaderIconButton(Context context,int resting,int active,String description){
        super(context);
        StateListDrawable icon=new StateListDrawable();
        icon.addState(new int[]{android.R.attr.state_pressed},context.getDrawable(active));
        icon.addState(new int[]{android.R.attr.state_focused},context.getDrawable(active));
        icon.addState(new int[]{},context.getDrawable(resting));setImageDrawable(icon);
        setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        setBackground(new RippleDrawable(ColorStateList.valueOf(0x44ffffff),null,new ColorDrawable(Color.WHITE)));
        int padding=SenseStyle.dp(context,10);setPadding(padding,padding,padding,padding);
        setMinimumWidth(SenseStyle.dp(context,48));setMinimumHeight(SenseStyle.dp(context,48));
        setContentDescription(description);setTooltipText(description);
    }
    public static SenseHeaderIconButton overflow(Context c,String description){return new SenseHeaderIconButton(c,R.drawable.sense_common_icon_glance_more_rest,R.drawable.sense_common_icon_glance_more_on,description);}
    public static SenseHeaderIconButton back(Context c,String description){return new SenseHeaderIconButton(c,R.drawable.sense_common_icon_glance_previous_rest,R.drawable.sense_common_icon_glance_previous_on,description);}
}
