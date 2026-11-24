package dev.sense.launcher.ui;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.*;
import java.util.function.IntConsumer;

/** Alphabet scrubber: only sections actually present are shown, in catalog order. */
public final class AlphabetIndex extends View {
    public static int holdDelayMillis(Context context){return Math.max(100,Math.min(1000,context.getSharedPreferences("drawer",0).getInt("alphabetHoldMs",300)));}
    public static void setHoldDelayMillis(Context context,int delay){context.getSharedPreferences("drawer",0).edit().putInt("alphabetHoldMs",Math.max(100,Math.min(1000,delay))).apply();}
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final java.util.List<String> sections;
    private final IntConsumer select;
    private int active=-1;
    private boolean pending,scrubbing;
    private float downX,downY,lastY;
    private final int touchSlop;
    private View overlayHost;
    private String previewLetter;
    private final android.graphics.drawable.Drawable preview=new android.graphics.drawable.Drawable(){
        private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override public void draw(Canvas canvas){
            if(previewLetter==null)return;
            float density=getResources().getDisplayMetrics().density;
            float cx=getBounds().exactCenterX(),cy=getBounds().exactCenterY(),half=44*density;
            ink.setColor(0xe6222924);canvas.drawRoundRect(cx-half,cy-half,cx+half,cy+half,14*density,14*density,ink);
            ink.setColor(0xffb4e477);ink.setTypeface(Typeface.DEFAULT_BOLD);ink.setTextAlign(Paint.Align.CENTER);
            ink.setTextSize(42*getResources().getDisplayMetrics().scaledDensity);
            canvas.drawText(previewLetter,cx,cy-(ink.ascent()+ink.descent())/2,ink);
        }
        @Override public void setAlpha(int alpha){}
        @Override public void setColorFilter(ColorFilter filter){}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    };
    private final Runnable hidePreview=this::hidePreview;
    private final Runnable hold=()->{
        if(!pending || !isAttachedToWindow())return;
        pending=false;scrubbing=true;setActivated(true);
        getParent().requestDisallowInterceptTouchEvent(true);
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        selectAt(lastY);
    };
    public AlphabetIndex(Context context,java.util.List<String> sections,IntConsumer select){
        super(context);this.sections=List.copyOf(sections);this.select=select;
        touchSlop=ViewConfiguration.get(context).getScaledTouchSlop();
        setContentDescription("Alphabetical app index. Hold, then drag to a letter.");setFocusable(true);
    }
    private float step(){return getHeight()/(float)Math.max(1,sections.size());}
    @Override protected void onDraw(Canvas canvas){
        paint.setTextAlign(Paint.Align.CENTER);paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(Math.min(12*getResources().getDisplayMetrics().scaledDensity,step()*.7f));
        for(int i=0;i<sections.size();i++){
            paint.setColor(i==active?0xffb4e477:0xffeeeeee);
            canvas.drawText(sections.get(i),getWidth()/2f,step()*(i+.5f)-(paint.ascent()+paint.descent())/2,paint);
        }
    }
    private void showPreview(){
        removeCallbacks(hidePreview);
        previewLetter=sections.get(active);
        if(overlayHost==null){overlayHost=getRootView();overlayHost.getOverlay().add(preview);}
        preview.setBounds(0,0,overlayHost.getWidth(),overlayHost.getHeight());preview.invalidateSelf();
    }
    private void hidePreview(){
        removeCallbacks(hidePreview);
        if(overlayHost!=null)overlayHost.getOverlay().remove(preview);
        overlayHost=null;previewLetter=null;
    }
    private void selectAt(float y){
        if(scrubbing){
            int radius=Math.round(48*getResources().getDisplayMetrics().density);
            setSystemGestureExclusionRects(List.of(new Rect(0,Math.max(0,(int)y-radius),getWidth(),Math.min(getHeight(),(int)y+radius))));
        }
        int index=Math.max(0,Math.min(sections.size()-1,(int)(y/step())));
        if(active!=index){active=index;select.accept(index);setContentDescription("Jump to "+sections.get(index));invalidate();}
        showPreview();
    }
    private void endGesture(){
        pending=false;scrubbing=false;removeCallbacks(hold);setActivated(false);
        setSystemGestureExclusionRects(List.of());
        if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);
        active=-1;invalidate();hidePreview();
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(sections.isEmpty())return false;
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN -> {
                endGesture();pending=true;downX=event.getX();downY=lastY=event.getY();
                postDelayed(hold,holdDelayMillis(getContext()));return true;
            }
            case MotionEvent.ACTION_MOVE -> {
                lastY=event.getY();
                if(scrubbing)selectAt(lastY);
                else if(pending && (Math.abs(event.getX()-downX)>touchSlop || Math.abs(lastY-downY)>touchSlop)){
                    pending=false;removeCallbacks(hold);
                }
                return true;
            }
            case MotionEvent.ACTION_UP -> {boolean selected=scrubbing;endGesture();if(selected)performClick();return true;}
            case MotionEvent.ACTION_POINTER_DOWN,MotionEvent.ACTION_CANCEL -> {endGesture();return true;}
            default -> {return true;}
        }
    }
    @Override protected void onDetachedFromWindow(){endGesture();super.onDetachedFromWindow();}
    @Override public boolean performClick(){super.performClick();return true;}
    @Override public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info){
        super.onInitializeAccessibilityNodeInfo(info);info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
    }
    @Override public boolean performAccessibilityAction(int action,android.os.Bundle arguments){
        if(!sections.isEmpty() && (action==android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD || action==android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)){
            active=Math.max(0,Math.min(sections.size()-1,active+(action==android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD?1:-1)));select.accept(active);announceForAccessibility(sections.get(active));showPreview();postDelayed(hidePreview,800);invalidate();return true;
        }return super.performAccessibilityAction(action,arguments);
    }
}
