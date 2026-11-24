package dev.sense.lockscreen;

import android.animation.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import java.util.*;
import java.util.function.IntConsumer;

/** Native accessible controls with a finger-following ring and shortcut-to-ring drag. */
final class LockSurface extends FrameLayout {
    final ImageButton ring;final ImageButton[] shortcuts=new ImageButton[4];private final TextView[] shortcutLabels=new TextView[4];
    private final ImageView high,wave;private final TextClock clock,date,ampm;private final TextView hint,operator;private final ImageView navbar;
    private final IntConsumer unlock;
    private float scale=1,baseX,baseY,ringX,ringY,downX,downY,startX,startY;private int active=-2,pointer=-1;private boolean authenticating;
    private int safeTop,safeBottom,safeLeft,safeRight;private ValueAnimator motion;
    LockSurface(Context context,IntConsumer unlock){super(context);this.unlock=unlock;setClipChildren(true);setFocusableInTouchMode(true);setBackgroundColor(Color.TRANSPARENT);
        navbar=new ImageView(context);navbar.setImageResource(R.drawable.ls_navbar);navbar.setScaleType(ImageView.ScaleType.FIT_XY);navbar.setScaleY(-1f);navbar.setTag("lockscreen_navbar");navbar.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);addView(navbar);
        operator=new TextView(context);operator.setTextColor(Color.WHITE);operator.setGravity(Gravity.CENTER);operator.setShadowLayer(2,0,1,Color.BLACK);String network="";try{var telephony=context.getSystemService(android.telephony.TelephonyManager.class);if(telephony!=null)network=telephony.getNetworkOperatorName();}catch(SecurityException ignored){}operator.setText(network);operator.setVisibility(network.isBlank()?GONE:VISIBLE);addView(operator);
        clock=new TextClock(context);clock.setFormat12Hour("h:mm");clock.setFormat24Hour("HH:mm");clock.setGravity(Gravity.CENTER);clock.setTextColor(Color.WHITE);clock.setTypeface(getResources().getFont(R.font.ls_clock));clock.setShadowLayer(3,0,2,0xaa000000);clock.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);addView(clock);
        ampm=new TextClock(context);ampm.setFormat12Hour("a");ampm.setFormat24Hour("");ampm.setTextColor(Color.WHITE);ampm.setGravity(Gravity.CENTER_VERTICAL);ampm.setShadowLayer(2,0,1,Color.BLACK);addView(ampm);
        date=new TextClock(context);String pattern="EEE, d MMM yyyy";date.setFormat12Hour(pattern);date.setFormat24Hour(pattern);date.setGravity(Gravity.CENTER);date.setTextColor(Color.WHITE);date.setShadowLayer(2,0,1,Color.BLACK);addView(date);
        hint=new TextView(context);hint.setText("Pull the ring to unlock");hint.setVisibility(INVISIBLE);hint.setGravity(Gravity.CENTER);hint.setTextColor(Color.WHITE);hint.setShadowLayer(2,0,1,Color.BLACK);addView(hint);
        for(int i=0;i<4;i++){final int slot=i;ImageButton button=new ImageButton(context);button.setImageDrawable(LockShortcuts.icon(context,i));button.setScaleType(ImageView.ScaleType.FIT_CENTER);button.setBackgroundResource(R.drawable.ls_shortcutpanel_b);button.setContentDescription("Unlock and open "+LockShortcuts.name(context,i));button.setTooltipText(button.getContentDescription());button.setOnClickListener(v->{if(!authenticating)unlock.accept(slot);});button.setOnTouchListener((v,e)->drag(e,slot));shortcuts[i]=button;addView(button);TextView label=new TextView(context);label.setText(LockShortcuts.name(context,i));label.setTextColor(Color.WHITE);label.setGravity(Gravity.CENTER);label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);label.setShadowLayer(2,0,1,Color.BLACK);label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);shortcutLabels[i]=label;addView(label);}
        wave=new ImageView(context);wave.setImageResource(R.drawable.ls_ring_wave);wave.setAlpha(0f);wave.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);addView(wave);
        ring=new ImageButton(context);ring.setImageResource(R.drawable.ls_ring);ring.setBackgroundResource(R.drawable.ls_ring_cover);ring.setScaleType(ImageView.ScaleType.FIT_CENTER);ring.setPadding(0,0,0,0);ring.setContentDescription("Unlock");ring.setTooltipText("Pull up to unlock");ring.setOnClickListener(v->{if(!authenticating)unlock.accept(-1);});ring.setOnTouchListener((v,e)->drag(e,-1));addView(ring);
        high=new ImageView(context);high.setImageResource(R.drawable.ls_ring_high);high.setAlpha(0f);high.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);addView(high);high.setClickable(false);
        setOnApplyWindowInsetsListener((v,insets)->{var safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());safeTop=safe.top;safeBottom=safe.bottom;safeLeft=safe.left;safeRight=safe.right;requestLayout();return insets;});
    }
    private void box(View view,int width,int height,float cx,float cy){view.measure(MeasureSpec.makeMeasureSpec(Math.max(1,width),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.max(1,height),MeasureSpec.EXACTLY));int l=Math.round(cx-width/2f),t=Math.round(cy-height/2f);view.layout(l,t,l+width,t+height);}
    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){
        int w=getWidth(),h=getHeight();for(int i=0;i<getChildCount();i++){View child=getChildAt(i);if(child.getLayoutParams().width==-1&&child.getLayoutParams().height==-1)child.layout(0,0,w,h);}
        float available=w-safeLeft-safeRight;scale=Math.min(available/540f,(h-safeTop-safeBottom)/700f);baseX=safeLeft+available/2;float dockAnchor=h-safeBottom-88*scale;baseY=h-safeBottom+48*scale;
        if(active==-2&&motion==null){ringX=baseX;ringY=baseY;}
        // Stock navbar: center (270,53), pivot (-270,-47), giving top y=6
        // relative to the resting ring container. Extend the base under gesture navigation.
        int navbarTop=Math.round(dockAnchor+6*scale);int navbarHeight=Math.max(1,h-navbarTop);
        box(navbar,w,navbarHeight,w/2f,navbarTop+navbarHeight/2f);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX,82*scale);ampm.setTextSize(TypedValue.COMPLEX_UNIT_PX,30*scale);operator.setTextSize(TypedValue.COMPLEX_UNIT_PX,22*scale);box(operator,Math.round(available),Math.round(36*scale),baseX,safeTop+20*scale);ampm.setVisibility(DateFormat.is24HourFormat(getContext())?GONE:VISIBLE);box(ampm,Math.round(70*scale),Math.round(50*scale),baseX+135*scale,safeTop+88*scale);date.setTextSize(TypedValue.COMPLEX_UNIT_PX,23*scale);hint.setTextSize(TypedValue.COMPLEX_UNIT_PX,22*scale);
        box(clock,Math.round(available),Math.round(140*scale),baseX,safeTop+76*scale);box(date,Math.round(available),Math.round(45*scale),baseX,safeTop+132*scale);box(hint,Math.round(available),Math.round(48*scale),baseX,safeTop+260*scale);
        float row=h-safeBottom-200*scale;float[] xs={67,203,338,473};for(int i=0;i<4;i++){int padding=Math.round(7*scale);shortcuts[i].setPadding(padding,padding,padding,padding);box(shortcuts[i],Math.max(Math.round(48*getResources().getDisplayMetrics().density),Math.round(83*scale)),Math.max(Math.round(48*getResources().getDisplayMetrics().density),Math.round(83*scale)),safeLeft+xs[i]*available/540f,row+(i==0||i==3?12*scale:0));shortcutLabels[i].setTextSize(TypedValue.COMPLEX_UNIT_PX,20*scale);box(shortcutLabels[i],Math.round(125*scale),Math.round(35*scale),shortcuts[i].getLeft()+shortcuts[i].getWidth()/2f,shortcuts[i].getBottom()+22*scale);}
        box(ring,Math.round(271*scale),Math.round(270*scale),baseX,baseY);box(high,Math.round(271*scale),Math.round(270*scale),baseX,baseY);box(wave,Math.round(360*scale),Math.round(360*scale),baseX,baseY);positionRing();
    }
    private void positionRing(){ring.setTranslationX(ringX-baseX);ring.setTranslationY(ringY-baseY);high.setTranslationX(ringX-baseX);high.setTranslationY(ringY-baseY);}
    private boolean drag(MotionEvent e,int slot){if(authenticating)return true;if(e.getActionMasked()!=MotionEvent.ACTION_DOWN&&active!=slot)return true;
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN->{stopMotion();active=slot;pointer=e.getPointerId(0);downX=e.getRawX();downY=e.getRawY();startX=ringX;startY=ringY;if(slot>=0){shortcuts[slot].setTranslationZ(8*getResources().getDisplayMetrics().density);shortcutLabels[slot].setVisibility(INVISIBLE);liftRing();}hint.setVisibility(VISIBLE);high.setAlpha(.6f);wave.setAlpha(.5f);wave.setScaleX(.6f);wave.setScaleY(.6f);wave.animate().alpha(0).scaleX(1.15f).scaleY(1.15f).setDuration(1168).start();hint.setText(slot<0?"Pull up to unlock":"Drag the shortcut into the ring");performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);getParent().requestDisallowInterceptTouchEvent(true);return true;}
            case MotionEvent.ACTION_MOVE->{int index=e.findPointerIndex(pointer);if(index<0){reset(true);return true;}float dx=e.getRawX(index)-downX,dy=e.getRawY(index)-downY;
                if(slot<0){ringX=Math.max(safeLeft+60*scale,Math.min(getWidth()-safeRight-60*scale,startX+dx));ringY=Math.max(safeTop+120*scale,Math.min(baseY+35*scale,startY+dy));positionRing();}
                else{shortcuts[slot].setTranslationX(dx);shortcuts[slot].setTranslationY(dy);float cx=shortcuts[slot].getLeft()+shortcuts[slot].getWidth()/2f+dx,cy=shortcuts[slot].getTop()+shortcuts[slot].getHeight()/2f+dy;high.setAlpha(Math.hypot(cx-ringX,cy-ringY)<110*scale?1:.35f);}
                return true;}
            case MotionEvent.ACTION_POINTER_UP->{if(e.getPointerId(e.getActionIndex())==pointer)reset(true);return true;}
            case MotionEvent.ACTION_UP->{float distance=(float)Math.hypot(e.getRawX()-downX,e.getRawY()-downY);boolean success;
                if(slot<0)success=baseY-ringY>=140*scale;else{ImageButton button=shortcuts[slot];float cx=button.getLeft()+button.getWidth()/2f+button.getTranslationX(),cy=button.getTop()+button.getHeight()/2f+button.getTranslationY();success=Math.hypot(cx-ringX,cy-ringY)<110*scale;}
                stopMotion();active=-2;pointer=-1;getParent().requestDisallowInterceptTouchEvent(false);
                if(success){performHapticFeedback(HapticFeedbackConstants.CONFIRM);unlock.accept(slot);}else if(distance<android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop()){reset(true);if(slot<0)hint.setText("Pull the ring upward to unlock");else hint.setText("Drag "+LockShortcuts.name(getContext(),slot)+" into the ring");}else reset(true);return true;}
            case MotionEvent.ACTION_CANCEL->{reset(true);return true;}
        }return true;
    }
    private void liftRing(){float from=ringY,target=baseY-150*scale;if(!ValueAnimator.areAnimatorsEnabled()){ringY=target;positionRing();return;}motion=ValueAnimator.ofFloat(from,target);motion.setDuration(334);motion.setInterpolator(new DecelerateInterpolator());motion.addUpdateListener(a->{ringY=(float)a.getAnimatedValue();positionRing();});motion.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator animation){motion=null;}});motion.start();}
    void setAuthenticating(boolean value){authenticating=value;hint.setVisibility(value?VISIBLE:INVISIBLE);for(var button:shortcuts)button.setEnabled(!value);ring.setEnabled(!value);hint.setText(value?"Unlock with Android to continue":"Pull the ring to unlock");}
    void reset(boolean animate){stopMotion();active=-2;pointer=-1;high.setAlpha(0f);hint.setText("Pull the ring to unlock");hint.setVisibility(INVISIBLE);for(var label:shortcutLabels)label.setVisibility(VISIBLE);for(var button:shortcuts){button.setTranslationX(0);button.setTranslationY(0);button.setTranslationZ(0);}if(!animate||!ValueAnimator.areAnimatorsEnabled()){ringX=baseX;ringY=baseY;positionRing();return;}float fromX=ringX,fromY=ringY;motion=ValueAnimator.ofFloat(0,1);motion.setDuration(1134);motion.setInterpolator(new DecelerateInterpolator(2));motion.addUpdateListener(a->{float fraction=(float)a.getAnimatedValue();ringX=fromX+(baseX-fromX)*fraction;ringY=fromY+(baseY-fromY)*fraction;positionRing();});motion.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator animation){motion=null;}});motion.start();}
    void stopMotion(){if(motion!=null){var animation=motion;motion=null;animation.removeAllListeners();animation.cancel();}wave.animate().cancel();wave.setAlpha(0f);}
    @Override protected void onDetachedFromWindow(){stopMotion();super.onDetachedFromWindow();}
}
