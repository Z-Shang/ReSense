package dev.sense.framework;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Title-only header modeled on Rosie's HeaderBarShort + centered HeaderBarText. */
public class SenseHeaderBar extends LinearLayout {
    private final TextView title;
    private android.view.View leading;
    private int actionWidthDp=76;
    public SenseHeaderBar(Context context) {
        super(context);
        setGravity(Gravity.CENTER);
        setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xff646964,0xff353b36,0xff242a25}));
        title=new TextView(context);
        title.setTextColor(Color.WHITE);title.setTextSize(22);
        title.setGravity(Gravity.CENTER);title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setAccessibilityHeading(true);
        int padding=Math.round(12*getResources().getDisplayMetrics().density);
        title.setPadding(padding,0,padding,0);
        addView(title,new LayoutParams(-1,-1));
    }
    public void setAction(String label, String description, Runnable action) {
        int width=Math.round(76*getResources().getDisplayMetrics().density);
        android.view.View balance=new android.view.View(getContext());
        leading=balance;
        addView(balance,0,new LayoutParams(width,-1));
        title.setLayoutParams(new LayoutParams(0,-1,1));
        SenseButton button=new SenseButton(getContext());
        button.setText(label);button.setContentDescription(description);
        button.setOnClickListener(v->action.run());
        int margin=Math.round(4*getResources().getDisplayMetrics().density);
        LayoutParams params=new LayoutParams(width-margin*2,-1);
        params.setMargins(margin,margin,margin,margin);
        addView(button,params);
    }
    /** Icon variant preserves the balanced title and shares the native HTC touch control. */
    public void setIconAction(int resting,int active,String description,Runnable action){
        actionWidthDp=48;int width=SenseStyle.dp(getContext(),actionWidthDp);
        leading=new android.view.View(getContext());addView(leading,0,new LayoutParams(width,-1));title.setLayoutParams(new LayoutParams(0,-1,1));
        SenseHeaderIconButton button=new SenseHeaderIconButton(getContext(),resting,active,description);button.setOnClickListener(v->action.run());addView(button,new LayoutParams(width,-1));
    }
    /** Replace the balancing slot with a full-size, accessible close target. */
    public void setNavigationAction(String description,Runnable action) {
        int width=SenseStyle.dp(getContext(),actionWidthDp);
        if(leading!=null)removeView(leading);
        android.widget.ImageButton close=new SenseHeaderIconButton(getContext(),R.drawable.sense_common_icon_glance_down_rest,R.drawable.sense_common_icon_glance_down_on,description);
        close.setContentDescription(description);
        close.setTooltipText(description);
        close.setOnClickListener(v->action.run());
        leading=close;
        title.setLayoutParams(new LayoutParams(0,-1,1));
        addView(close,0,new LayoutParams(width,-1));
    }
    /** Extend the header background behind status icons without covering its controls. */
    public void setTopInset(int inset){
        setPadding(getPaddingLeft(),inset,getPaddingRight(),getPaddingBottom());
        android.view.ViewGroup.LayoutParams params=getLayoutParams();
        if(params!=null){
            int height=Math.round(56*getResources().getDisplayMetrics().density)+inset;
            if(params.height!=height){params.height=height;setLayoutParams(params);}
        }
    }
    public void setTitle(CharSequence value){title.setText(value);}
}
