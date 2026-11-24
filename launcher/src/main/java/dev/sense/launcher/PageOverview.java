package dev.sense.launcher;

import dev.sense.framework.SenseButton;
import android.app.Dialog;
import android.animation.ValueAnimator;
import android.content.ClipData;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;

/** Rosie Leap: seven spatial thumbnails with sampled zoom/depth, selection and page dragging. */
final class PageOverview extends Dialog {
    private static final float ZOOM_DURATION_SCALE=.6f;
    private final Workspace workspace;
    private final LinearLayout content,header,actions;
    private final LeapStage leap;
    private final HtcLeapMotion motion;
    private final java.util.ArrayList<Bitmap> previews=new java.util.ArrayList<>();
    private ValueAnimator animator;
    private boolean dismissing,interactive;
    private float progress,dialogSpan;
    private int bank,selected,refreshGeneration;
    PageOverview(Workspace workspace){
        super(workspace.activity,R.style.AppTheme);this.workspace=workspace;motion=new HtcLeapMotion(getContext());
        bank=Math.max(0,(workspace.page-1)/7);selected=Math.max(0,(workspace.page-1)%7);
        requestWindowFeature(Window.FEATURE_NO_TITLE);getWindow().setWindowAnimations(0);
        // The shade covers the full window; only the controls receive safe insets.
        getWindow().setDecorFitsSystemWindows(false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().setStatusBarContrastEnforced(false);
        getWindow().setNavigationBarContrastEnforced(false);
        content=new LinearLayout(getContext());content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(0x33000000);
        header=new LinearLayout(getContext());header.setGravity(Gravity.CENTER);
        TextView title=label("Home pages",22);header.addView(title,new LinearLayout.LayoutParams(0,-1,1));
        content.addView(header,new LinearLayout.LayoutParams(-1,dp(48)));
        leap=new LeapStage();content.addView(leap,new LinearLayout.LayoutParams(-1,0,1));
        actions=new LinearLayout(getContext());actions.setGravity(Gravity.CENTER);
        content.addView(actions,new LinearLayout.LayoutParams(-1,dp(52)));
        content.setOnApplyWindowInsetsListener((v,insets)->{
            var bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            var gestures=insets.getInsets(WindowInsets.Type.mandatorySystemGestures());
            v.setPadding(bars.left,bars.top,bars.right,Math.max(bars.bottom,gestures.bottom));return insets;
        });
        setContentView(content);refresh();
    }
    @Override public void show(){show(false);}
    void show(boolean gesture){
        interactive=gesture;super.show();
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        getWindow().setLayout(-1,-1);getWindow().getDecorView().requestApplyInsets();
        workspace.activity.setPageOverviewVisible(true);setProgress(0);
        if(!gesture)animateTo(1,false);
    }
    float progress(){return progress;}
    void setGestureProgress(float value){if(animator!=null)animator.cancel();setProgress(value);}
    void finishGesture(boolean cancelled){interactive=false;if(cancelled || progress<.25f)animateTo(0,true);else animateTo(1,false);}
    private void setProgress(float value){
        progress=Math.max(0,Math.min(1,value));header.setAlpha(progress);actions.setAlpha(progress);
        leap.position();
    }
    private void animateTo(float target,boolean close){
        if(animator!=null)animator.cancel();
        for(int i=0;i<leap.getChildCount();i++)leap.getChildAt(i).animate().cancel();
        dismissing=close;
        if(!ValueAnimator.areAnimatorsEnabled()){setProgress(target);if(close)finishDismiss();return;}
        animator=ValueAnimator.ofFloat(progress,target);animator.setInterpolator(t->t);
        animator.setDuration(Math.max(80,Math.round(motion.duration*ZOOM_DURATION_SCALE*Math.abs(target-progress))));
        animator.addUpdateListener(a->setProgress((float)a.getAnimatedValue()));
        animator.addListener(new android.animation.AnimatorListenerAdapter(){
            boolean cancelled;
            @Override public void onAnimationCancel(android.animation.Animator a){cancelled=true;}
            @Override public void onAnimationEnd(android.animation.Animator a){if(!cancelled && close)finishDismiss();}
        });animator.start();
    }
    @Override public void dismiss(){if(!dismissing){if(isShowing())animateTo(0,true);else super.dismiss();}}
    private void finishDismiss(){super.dismiss();workspace.activity.setPageOverviewVisible(false);dismissing=false;}
    @Override protected void onStop(){if(animator!=null)animator.cancel();recyclePreviews();super.onStop();}
    private static float span(MotionEvent e){return e.getPointerCount()<2?0:(float)Math.hypot(e.getX(0)-e.getX(1),e.getY(0)-e.getY(1));}
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        if(dismissing)return true;
        if(event.getActionMasked()==MotionEvent.ACTION_POINTER_DOWN){dialogSpan=span(event);return true;}
        if(dialogSpan>0){
            if(event.getActionMasked()==MotionEvent.ACTION_MOVE && event.getPointerCount()>1){
                float ratio=span(event)/dialogSpan;
                if(ratio>1.08f){if(animator!=null)animator.cancel();setProgress(1-(ratio-1)/.6f);}return true;
            }
            if(event.getActionMasked()==MotionEvent.ACTION_POINTER_UP || event.getActionMasked()==MotionEvent.ACTION_UP || event.getActionMasked()==MotionEvent.ACTION_CANCEL){
                dialogSpan=0;if(progress<.75f)dismiss();else animateTo(1,false);return true;
            }
        }
        return interactive || super.dispatchTouchEvent(event);
    }
    private int dp(int value){return workspace.dp(value);}
    private TextView label(String text,int size){TextView v=new TextView(getContext());v.setText(text);v.setTextColor(Color.WHITE);v.setTextSize(size);v.setGravity(Gravity.CENTER);return v;}
    private Button button(String title,Runnable action){Button b=new dev.sense.framework.SenseButton(getContext());b.setText(title);b.setOnClickListener(v->action.run());return b;}
    private void recyclePreviews(){for(Bitmap bitmap:previews)bitmap.recycle();previews.clear();}
    private void refresh(){
        refreshGeneration++;
        leap.removeAllViews();recyclePreviews();actions.removeAllViews();
        bank=Math.max(0,Math.min(bank,(workspace.pages-1)/7));
        if(workspace.pages>7){
            Button previous=button("‹",()->{bank--;refresh();});previous.setEnabled(bank>0);actions.addView(previous,new LinearLayout.LayoutParams(dp(44),-1));
            Button next=button("›",()->{bank++;refresh();});next.setEnabled((bank+1)*7<workspace.pages);actions.addView(next,new LinearLayout.LayoutParams(dp(44),-1));
        }
        actions.addView(button("Add page",()->{workspace.pages++;workspace.save();workspace.render();bank=(workspace.pages-1)/7;refresh();}),new LinearLayout.LayoutParams(0,-1,1));
        actions.addView(button("Done",this::dismiss),new LinearLayout.LayoutParams(0,-1,1));
        for(int i=bank*7+1;i<=Math.min(workspace.pages,bank*7+7);i++){
            final int index=i;
            FrameLayout card=new FrameLayout(getContext());card.setTag(index);
            android.graphics.drawable.GradientDrawable surface=new android.graphics.drawable.GradientDrawable();
            surface.setColor(0x990c1410);surface.setCornerRadius(dp(5));
            card.setBackground(surface);
            Bitmap bitmap=workspace.pagePreview(index,308,460);previews.add(bitmap);
            ImageView image=new ImageView(getContext());image.setImageBitmap(bitmap);image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            FrameLayout.LayoutParams previewParams=new FrameLayout.LayoutParams(-1,-1);previewParams.bottomMargin=dp(22);
            image.setPadding(dp(3),dp(3),dp(3),0);card.addView(image,previewParams);
            if(index==workspace.page){
                View frame=new View(getContext()){
                    final Bitmap border=BitmapFactory.decodeResource(getResources(),R.drawable.sense_leap_frame);
                    final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
                    @Override protected void onDraw(Canvas canvas){
                        // Fusion's .9.png texture is a border, not a bitmap to scale as one quad.
                        int[] sx={0,23,border.getWidth()-23,border.getWidth()},sy={0,23,border.getHeight()-23,border.getHeight()};
                        float edge=Math.min(dp(6),Math.min(getWidth(),getHeight())/2f);
                        float[] dx={0,edge,getWidth()-edge,getWidth()},dy={0,edge,getHeight()-edge,getHeight()};
                        for(int y=0;y<3;y++)for(int x=0;x<3;x++)
                            canvas.drawBitmap(border,new Rect(sx[x],sy[y],sx[x+1],sy[y+1]),new RectF(dx[x],dy[y],dx[x+1],dy[y+1]),paint);
                    }
                };
                frame.setTag("leap_selection_border");frame.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);card.addView(frame,new FrameLayout.LayoutParams(-1,-1));
            }
            TextView number=label(index+(index==workspace.page?" · Current":""),11);
            number.setBackgroundColor(0x99000000);card.addView(number,new FrameLayout.LayoutParams(-1,dp(22),Gravity.BOTTOM));
            card.setContentDescription("Open home page "+index+(index==workspace.page?", current page":""));
            card.setOnClickListener(v->{selected=(index-1)%7;workspace.page=index;workspace.prefs.edit().putInt("currentPage",index).apply();workspace.save();workspace.render();dismiss();});
            card.setOnLongClickListener(v->{
                v.setScaleX(motion.dragScale(1));v.setScaleY(motion.dragScale(1));
                v.setAlpha(motion.dragOpacity(1));
                return v.startDragAndDrop(new ClipData("Home page",new String[]{"text/vnd.sense.page"},new ClipData.Item(Integer.toString(index))),new View.DragShadowBuilder(v),index,0);
            });
            card.setAccessibilityDelegate(new View.AccessibilityDelegate(){
                @Override public void onInitializeAccessibilityNodeInfo(View host,android.view.accessibility.AccessibilityNodeInfo info){
                    super.onInitializeAccessibilityNodeInfo(host,info);
                    if(index>1)info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(0x01020001,"Move page left"));
                    if(index<workspace.pages)info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(0x01020002,"Move page right"));
                }
                @Override public boolean performAccessibilityAction(View host,int action,android.os.Bundle args){
                    if(action==0x01020001 || action==0x01020002){reorder(index,index+(action==0x01020001?-1:1));return true;}
                    return super.performAccessibilityAction(host,action,args);
                }
            });
            Button remove=button("×",()->removePage(index));remove.setTextSize(18);remove.setPadding(0,0,0,0);remove.setMinimumHeight(0);remove.setMinHeight(0);remove.setContentDescription("Remove home page "+index);remove.setEnabled(workspace.pages>1);
            card.addView(remove,new FrameLayout.LayoutParams(dp(28),dp(28),Gravity.TOP|Gravity.RIGHT));
            card.setOnDragListener((v,event)->{
                switch(event.getAction()){
                    case DragEvent.ACTION_DRAG_STARTED:return event.getClipDescription()!=null && event.getClipDescription().hasMimeType("text/vnd.sense.page");
                    case DragEvent.ACTION_DRAG_ENTERED:card.setForeground(new android.graphics.drawable.ColorDrawable(0x559bc76b));break;
                    case DragEvent.ACTION_DRAG_EXITED:card.setForeground(null);break;
                    case DragEvent.ACTION_DRAG_ENDED:card.setForeground(null);card.animate().cancel();leap.position();break;
                    case DragEvent.ACTION_DROP:if(event.getLocalState() instanceof Integer from){reorder(from,index);}break;
                }return true;
            });
            leap.addView(card);
        }
        leap.requestLayout();
    }
    private void reorder(int from,int to){
        java.util.Map<Integer,float[]> origins=new java.util.HashMap<>();
        for(int i=0;i<leap.getChildCount();i++){
            View tile=leap.getChildAt(i);int old=(Integer)tile.getTag();
            origins.put(Workspace.reorderedPage(old,from,to),new float[]{tile.getTranslationX(),tile.getTranslationY()});
        }
        workspace.movePage(from,to);refresh();
        int generation=refreshGeneration;
        leap.post(()->{
            if(generation!=refreshGeneration || !isShowing())return;
            leap.position();
            for(int i=0;i<leap.getChildCount();i++){
                View tile=leap.getChildAt(i);float[] origin=origins.get((Integer)tile.getTag());if(origin==null)continue;
                float x=tile.getTranslationX(),y=tile.getTranslationY();
                tile.setTranslationX(origin[0]);tile.setTranslationY(origin[1]);
                tile.animate().translationX(x).translationY(y).setDuration(334).setInterpolator(motion::reorder).start();
            }
        });
    }
    private void removePage(int index){
        int count=0;for(int i=0;i<workspace.items.length();i++)if(workspace.items.optJSONObject(i).optInt("page")==index)count++;
        if(count==0){workspace.removePage(index);refresh();}
        else new android.app.AlertDialog.Builder(getContext()).setTitle("Remove page "+index+"?")
            .setMessage("This removes "+count+" placed item(s), including widgets. Installed apps are kept.")
            .setPositiveButton("Remove",(d,n)->{workspace.removePage(index);refresh();}).setNegativeButton("Cancel",null).show();
    }
    private final class LeapStage extends FrameLayout {
        LeapStage(){super(PageOverview.this.getContext());setClipChildren(false);setClipToPadding(false);}
        @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){
            float fit=Math.min(getWidth()/540f,getHeight()/960f);
            int w=Math.max(1,Math.round(154*fit)),h=Math.max(1,Math.round(230*fit));
            for(int i=0;i<getChildCount();i++){View tile=getChildAt(i);tile.measure(MeasureSpec.makeMeasureSpec(w,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(h,MeasureSpec.EXACTLY));tile.layout(0,0,w,h);tile.setPivotX(w/2f);tile.setPivotY(h/2f);tile.setCameraDistance(dp(2400));}
            position();
        }
        void position(){
            float fit=Math.min(getWidth()/540f,getHeight()/960f),ox=(getWidth()-540*fit)/2,oy=(getHeight()-960*fit)/2;
            for(int i=0;i<getChildCount();i++){
                View tile=getChildAt(i);float[] pose=motion.pose(i,selected,progress);
                tile.setTranslationX(ox+pose[0]*fit-tile.getWidth()/2f);tile.setTranslationY(oy+pose[1]*fit-tile.getHeight()/2f);
                tile.setScaleX(pose[2]);tile.setScaleY(pose[2]);tile.setRotationY(pose[3]);
                tile.setAlpha(Math.min(1,progress*3+(i==selected?1:0)));
                tile.setTranslationZ((pose[2]-1)*dp(8));
                View border=tile.findViewWithTag("leap_selection_border");
                if(border!=null)border.setAlpha(progress);
            }
        }
    }
}
