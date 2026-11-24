package dev.sense.framework;

import android.content.Context;
import android.graphics.Shader;
import android.graphics.drawable.*;
import android.view.Gravity;
import android.widget.*;

/** Stock textured icon-and-label navigation, as used by HTC's People/Phone carousel. */
public final class SenseCarouselTabs extends LinearLayout {
    public SenseCarouselTabs(Context context){
        super(context);setOrientation(HORIZONTAL);
        BitmapDrawable texture=(BitmapDrawable)context.getDrawable(R.drawable.sense_common_app_bkg_src).mutate();texture.setTileModeXY(Shader.TileMode.REPEAT,Shader.TileMode.REPEAT);texture.setFilterBitmap(false);texture.setColorFilter(new android.graphics.LightingColorFilter(0xff444444,0x001a1a1a));setBackground(texture);
        setDividerDrawable(context.getDrawable(R.drawable.sense_common_list_divider_vertical));setShowDividers(SHOW_DIVIDER_MIDDLE);
    }
    public TextView addTab(String label,int image,boolean selected,Runnable action){
        return addTab(label,image,image,selected,action);
    }
    public TextView addTab(String label,int image,int activeImage,boolean selected,Runnable action){
        Context c=getContext();TextView tab=SenseStyle.text(c,label,11,0xffffffff);tab.setGravity(Gravity.CENTER);tab.setSingleLine();tab.setShadowLayer(1,0,1,0xff000000);tab.setPadding(SenseStyle.dp(c,2),SenseStyle.dp(c,4),SenseStyle.dp(c,2),SenseStyle.dp(c,3));
        StateListDrawable icon=new StateListDrawable();icon.addState(new int[]{android.R.attr.state_selected},c.getDrawable(activeImage).mutate());icon.addState(new int[]{android.R.attr.state_pressed},c.getDrawable(activeImage).mutate());icon.addState(new int[]{},c.getDrawable(image).mutate());icon.setBounds(0,0,SenseStyle.dp(c,36),SenseStyle.dp(c,36));tab.setCompoundDrawables(null,icon,null,null);tab.setCompoundDrawablePadding(SenseStyle.dp(c,2));
        StateListDrawable states=new StateListDrawable();states.addState(new int[]{android.R.attr.state_pressed},c.getDrawable(R.drawable.sense_common_subnav_selector_tab));states.addState(new int[]{android.R.attr.state_selected},c.getDrawable(R.drawable.sense_common_subnav_selector_tab));states.addState(new int[]{},new ColorDrawable(0x00000000));tab.setBackground(states);tab.setSelected(selected);tab.setFocusable(true);tab.setContentDescription(label);tab.setOnClickListener(v->action.run());
        tab.setAccessibilityDelegate(new android.view.View.AccessibilityDelegate(){@Override public void onInitializeAccessibilityNodeInfo(android.view.View view,android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(view,info);info.setClassName("android.widget.TabWidget");info.setSelected(view.isSelected());}});
        addView(tab,new LayoutParams(0,-1,1));return tab;
    }
}
