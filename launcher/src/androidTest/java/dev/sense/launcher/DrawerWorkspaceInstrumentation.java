package dev.sense.launcher;

import dev.sense.framework.SenseTabBar;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import dev.sense.launcher.ui.*;
import java.util.function.BooleanSupplier;

/** Tests isolated workspace touch state and real drawer placement, cache and pull animation. */
final class DrawerWorkspaceInstrumentation {
    private final Instrumentation test;
    private MainActivity activity;
    private Workspace workspace,original;
    private ViewGroup parent;
    private int index;
    private SenseDrawerLayout openedPanel;
    private int openingFrames;
    private String openingFailure;
    private View sourceClock,incomingIcon;
    DrawerWorkspaceInstrumentation(Instrumentation test){this.test=test;}
    void run(){
        Bundle report=new Bundle();
        try{
            activity=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            main(()->{
                original=find(activity.getWindow().getDecorView(),Workspace.class);parent=(ViewGroup)original.getParent();index=parent.indexOfChild(original);
                var preferences=activity.getSharedPreferences("swipe_instrumentation",0);
                preferences.edit().clear().putString("items","[]").putInt("pages",3).putInt("currentPage",1).commit();
                workspace=new Workspace(activity,new SenseWidgetHost(activity,9010),preferences);
                org.json.JSONObject clock=new org.json.JSONObject(),icon=new org.json.JSONObject();
                Workspace.put(clock,"type","clock");Workspace.put(clock,"page",1);Workspace.put(clock,"x",0);Workspace.put(clock,"y",0);Workspace.put(clock,"w",4);Workspace.put(clock,"h",2);
                Workspace.put(icon,"type","app");Workspace.put(icon,"component",new ComponentName(activity,MainActivity.class).flattenToString());Workspace.put(icon,"page",2);Workspace.put(icon,"x",0);Workspace.put(icon,"y",4);Workspace.put(icon,"w",1);Workspace.put(icon,"h",1);
                workspace.items.put(clock);workspace.items.put(icon);workspace.save();workspace.render();sourceClock=workspace.getChildAt(0);
                var params=original.getLayoutParams();parent.removeView(original);parent.addView(workspace,index,params);
            });
            test.waitForIdleSync();
            main(()->{
                swipe(MotionEvent.ACTION_DOWN,.8f,.6f);swipe(MotionEvent.ACTION_MOVE,.45f,.6f);
                if(workspace.page!=2)throw new AssertionError("Page did not follow the finger before release");
                if(workspace.prefs.getInt("currentPage",-1)!=1)throw new AssertionError("Unfinished swipe was persisted");
                swipe(MotionEvent.ACTION_CANCEL,.45f,.6f);
            });
            await(()->workspace.page==1,"Cancelled page swipe rollback");
            Thread.sleep(400);
            main(()->{
                if(workspace.getChildAt(0)!=sourceClock)throw new AssertionError("Rollback rebuilt source widget");
                swipe(MotionEvent.ACTION_DOWN,.8f,.6f);swipe(MotionEvent.ACTION_MOVE,.4f,.6f);swipe(MotionEvent.ACTION_UP,.4f,.6f);
                swipe(MotionEvent.ACTION_DOWN,.75f,.6f);swipe(MotionEvent.ACTION_MOVE,.65f,.6f);
                if(workspace.prefs.getInt("currentPage",-1)!=1)throw new AssertionError("Grabbing a settling page forced a commit");
                swipe(MotionEvent.ACTION_CANCEL,.65f,.6f);
            });
            await(()->workspace.page==1,"Interrupted settle rollback");
            main(()->{
                if(workspace.getChildAt(0)!=sourceClock)throw new AssertionError("Interrupted settle rebuilt source widget");
                swipe(MotionEvent.ACTION_DOWN,.8f,.6f);swipe(MotionEvent.ACTION_MOVE,.35f,.6f);
                incomingIcon=workspace.getChildAt(0);
                android.graphics.Bitmap preview=android.graphics.Bitmap.createBitmap(240,400,android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas canvas=new android.graphics.Canvas(preview);canvas.scale(240f/workspace.getWidth(),400f/workspace.getHeight());workspace.draw(canvas);
                int visible=0;for(int y=0;y<400;y+=8)for(int x=0;x<240;x+=8)if(android.graphics.Color.alpha(preview.getPixel(x,y))>20)visible++;
                preview.recycle();if(visible<4)throw new AssertionError("Recorded transition was blank");
                swipe(MotionEvent.ACTION_UP,.35f,.6f);
            });
            await(()->workspace.prefs.getInt("currentPage",-1)==2,"Committed page swipe");
            main(()->{if(workspace.getChildAt(0)!=incomingIcon)throw new AssertionError("Commit rebuilt the incoming page");});
            await(()->AppCatalog.get(activity).loaded(),"Warm app cache");
            main(()->{
                var source=AppCatalog.get(activity).entries();
                if(source.isEmpty())throw new AssertionError("No apps to exercise recycling");
                var many=new java.util.ArrayList<AppCatalog.Entry>();
                for(int i=0;i<500;i++)many.add(source.get(i%source.size()));
                for(boolean listMode:new boolean[]{false,true}){
                var viewport=new DrawerAppViewport(activity,many,listMode,()->listMode?new SenseAppRow(activity):new SenseAppIcon(activity),(view,entry)->{
                    view.setApp(entry.label(),entry.drawable(activity));view.setTag(entry);
                });
                int width=activity.getResources().getDisplayMetrics().widthPixels;
                int height=activity.getResources().getDisplayMetrics().heightPixels/2;
                viewport.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
                viewport.layout(0,0,width,height);
                ViewGroup cells=(ViewGroup)viewport.getChildAt(0);
                int cellHeight=Math.round((listMode?72:104)*activity.getResources().getDisplayMetrics().density);
                int columns=listMode?1:4,fullHeight=((many.size()+columns-1)/columns)*cellHeight;
                if(cells.getHeight()!=fullHeight)throw new AssertionError("Scroll extent follows visible rows instead of total apps: "+cells.getHeight()+" vs "+fullHeight);
                var originalCells=new java.util.HashSet<View>();
                for(int i=0;i<cells.getChildCount();i++)originalCells.add(cells.getChildAt(i));
                if(viewport.boundCount()>=100)throw new AssertionError("Drawer inflated too many of 500 apps");
                viewport.jumpTo(400);
                if(viewport.getScrollY()==0)throw new AssertionError("Alphabet target did not scroll");
                boolean reused=false;
                for(int i=0;i<cells.getChildCount();i++){
                    View view=cells.getChildAt(i);reused|=originalCells.contains(view);
                    int position=viewport.positionOf(view);
                    if(position<0 || view.getTag()!=many.get(position))throw new AssertionError("Recycled icon bound to wrong app");
                }
                if(!reused || viewport.boundCount()>=100)throw new AssertionError("App views were not recycled");
                viewport.jumpTo(0);
                if(viewport.getScrollY()!=0)throw new AssertionError("Recycled viewport failed to return to top");
                for(int target:new int[]{100,400,499,200,0}){
                    viewport.jumpTo(target);int expected=Math.min((target/columns)*cellHeight,fullHeight-height);
                    viewport.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
                    viewport.layout(0,0,width,height);
                    if(cells.getHeight()!=fullHeight || viewport.getScrollY()!=expected)throw new AssertionError("Recycling changed scroll extent/position at "+target+" in "+(listMode?"list":"grid"));
                    var event=android.view.accessibility.AccessibilityEvent.obtain(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SCROLLED);
                    viewport.onInitializeAccessibilityEvent(event);
                    if(event.getMaxScrollY()!=fullHeight-height)throw new AssertionError("Native scroll range changed at "+target);event.recycle();
                    if(viewport.boundCount()>=100)throw new AssertionError("Stable scroll range disabled recycling");
                }
                }
            });
            await(activity::drawerPrepared,"Drawer prepared while home is idle");
            main(()->{
                restore();workspace.prefs.edit().clear().commit();activity.showApps();
                openedPanel=find(activity.getWindow().getDecorView(),SenseDrawerLayout.class);
                if(find(openedPanel,SenseAppIcon.class)==null)throw new AssertionError("Prepared app views missing at animation start");
                SenseAppIcon preparedIcon=find(openedPanel,SenseAppIcon.class);
                View dockLayer=(View)find(activity.getWindow().getDecorView(),BentDockLayout.class).getParent();
                android.view.ViewGroup stage=(android.view.ViewGroup)openedPanel.getParent();
                if(dockLayer.getParent()!=stage || stage.indexOfChild(dockLayer)<=stage.indexOfChild(openedPanel))
                    throw new AssertionError("Dock is not layered above the animated drawer");
                var observer=openedPanel.getViewTreeObserver();
                observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){
                    @Override public boolean onPreDraw(){
                        if(openedPanel.isTransitioning()){
                            openingFrames++;
                            if(find(openedPanel,SenseAppIcon.class)!=preparedIcon)openingFailure="Prepared icon was replaced during opening";
                        }else if(observer.isAlive())observer.removeOnPreDrawListener(this);
                        return true;
                    }
                });
            });
            await(()->{SenseDrawerLayout panel=find(activity.getWindow().getDecorView(),SenseDrawerLayout.class);return panel!=null && !panel.isTransitioning() && find(panel,AlphabetIndex.class)!=null;},"Indexed drawer");
            AlphabetIndex[] rail={null};int[] beforeScrub={0};
            main(()->{
                rail[0]=find(openedPanel,AlphabetIndex.class);
                beforeScrub[0]=find(openedPanel,android.widget.ScrollView.class).getScrollY();
                long time=SystemClock.uptimeMillis();
                MotionEvent down=MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,rail[0].getWidth()/2f,rail[0].getHeight()*.8f,0);
                rail[0].dispatchTouchEvent(down);down.recycle();
                if(rail[0].isActivated() || !rail[0].getSystemGestureExclusionRects().isEmpty())throw new AssertionError("Rail captured edge before hold");
                MotionEvent up=MotionEvent.obtain(time,time+10,MotionEvent.ACTION_UP,rail[0].getWidth()/2f,rail[0].getHeight()*.8f,0);
                rail[0].dispatchTouchEvent(up);up.recycle();
                if(find(openedPanel,android.widget.ScrollView.class).getScrollY()!=beforeScrub[0])throw new AssertionError("Quick tap scrubbed rail");
                down=MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,rail[0].getWidth()/2f,rail[0].getHeight()*.8f,0);
                rail[0].dispatchTouchEvent(down);down.recycle();
                MotionEvent move=MotionEvent.obtain(time,time+20,MotionEvent.ACTION_MOVE,-100,rail[0].getHeight()*.8f,0);
                rail[0].dispatchTouchEvent(move);move.recycle();
            });
            SystemClock.sleep(ViewConfiguration.getLongPressTimeout()+100);
            main(()->{
                if(rail[0].isActivated())throw new AssertionError("Back-like swipe activated scrubber");
                long time=SystemClock.uptimeMillis();
                MotionEvent down=MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,rail[0].getWidth()/2f,rail[0].getHeight()*.8f,0);
                rail[0].dispatchTouchEvent(down);down.recycle();
            });
            await(()->rail[0].isActivated(),"Hold activates alphabet scrubber");
            main(()->{
                if(rail[0].getSystemGestureExclusionRects().isEmpty())throw new AssertionError("Active scrubber has no local gesture exclusion");
                if(!rail[0].getContentDescription().toString().startsWith("Jump to "))throw new AssertionError("Held letter was not selected");
            });
            // Inspect the actual root overlay, not just the rail's selected state.
            boolean greenLetter=false;int radius=Math.round(40*activity.getResources().getDisplayMetrics().density);
            long previewDeadline=SystemClock.uptimeMillis()+1000;
            while(!greenLetter && SystemClock.uptimeMillis()<previewDeadline){
                test.waitForIdleSync();
                android.graphics.Bitmap letterShot=test.getUiAutomation().takeScreenshot();
                int centerX=letterShot.getWidth()/2,centerY=letterShot.getHeight()/2;
                for(int y=centerY-radius;y<centerY+radius;y++)for(int x=centerX-radius;x<centerX+radius;x++){
                    int pixel=letterShot.getPixel(x,y);
                    if(android.graphics.Color.red(pixel)==180 && android.graphics.Color.green(pixel)==228 && android.graphics.Color.blue(pixel)==119)greenLetter=true;
                }
                letterShot.recycle();if(!greenLetter)SystemClock.sleep(50);
            }
            if(!greenLetter)throw new AssertionError("Centered letter preview was not rendered");
            main(()->{
                long time=SystemClock.uptimeMillis();
                MotionEvent cancel=MotionEvent.obtain(time,time,MotionEvent.ACTION_CANCEL,0,0,0);rail[0].dispatchTouchEvent(cancel);cancel.recycle();
                if(rail[0].isActivated() || !rail[0].getSystemGestureExclusionRects().isEmpty())throw new AssertionError("Cancelled scrubber retained gesture capture");
                find(openedPanel,android.widget.ScrollView.class).scrollTo(0,0);
            });
            main(()->{
                SenseDrawerLayout panel=find(activity.getWindow().getDecorView(),SenseDrawerLayout.class);
                if(panel!=openedPanel)throw new AssertionError("App loading replaced the animated drawer");
                if(openingFailure!=null)throw new AssertionError(openingFailure);
                if(android.animation.ValueAnimator.areAnimatorsEnabled() && openingFrames==0)throw new AssertionError("No opening frames rendered before app binding");
                if(find(activity.getWindow().getDecorView(),BentDockLayout.class).getVisibility()!=View.INVISIBLE)throw new AssertionError("Stock dock did not finish hiding under drawer");
                var insets=panel.getRootWindowInsets();
                if(insets!=null){
                    int safe=Math.max(insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()).bottom,
                            insets.getInsets(WindowInsets.Type.mandatorySystemGestures()).bottom);
                    if(panel.getPaddingBottom()<safe)throw new AssertionError("Drawer tabs overlap system navigation");
                }
                SenseTabBar tabs=find(panel,SenseTabBar.class);
                if(tabs==null || Math.abs(tabs.getBottom()-(panel.getHeight()-panel.getPaddingBottom()))>1)throw new AssertionError("Tabs are not bottom anchored");
                var snapshot=AppCatalog.get(activity).entries();AppCatalog.get(activity).ensureLoaded();
                if(snapshot!=AppCatalog.get(activity).entries())throw new AssertionError("Unchanged catalog was rebuilt");
                float y=panel.getHeight()*.3f;
                MotionEvent down=MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,100,y,0);panel.onInterceptTouchEvent(down);down.recycle();
                MotionEvent move=MotionEvent.obtain(0,16,MotionEvent.ACTION_MOVE,100,y+200,0);panel.onInterceptTouchEvent(move);panel.onTouchEvent(move);move.recycle();
                if(panel.getTranslationY()<190)throw new AssertionError("Drawer pull did not follow touch");
                MotionEvent cancel=MotionEvent.obtain(0,32,MotionEvent.ACTION_CANCEL,100,y+200,0);panel.onTouchEvent(cancel);cancel.recycle();
            });
            await(()->{SenseDrawerLayout panel=find(activity.getWindow().getDecorView(),SenseDrawerLayout.class);return panel!=null && panel.getTranslationY()==0 && !panel.isTransitioning();},"Cancelled drawer pull");
            android.widget.ScrollView[] cachedScroll={null};int[] originalMinimum={0};
            main(()->{
                cachedScroll[0]=find(openedPanel,android.widget.ScrollView.class);
                originalMinimum[0]=cachedScroll[0].getChildAt(0).getMinimumHeight();
                cachedScroll[0].getChildAt(0).setMinimumHeight(cachedScroll[0].getHeight()*3);
            });test.waitForIdleSync();
            main(()->{
                cachedScroll[0].scrollTo(0,400);
                if(cachedScroll[0].getScrollY()==0)throw new AssertionError("Drawer scroll reset test did not start scrolled");
                SenseAppIcon cachedIcon=find(openedPanel,SenseAppIcon.class);
                AlphabetIndex cachedIndex=find(openedPanel,AlphabetIndex.class);
                activity.onNewIntent(new Intent(activity,MainActivity.class));
                activity.showApps();
                SenseDrawerLayout reopened=find(activity.getWindow().getDecorView(),SenseDrawerLayout.class);
                if(find(reopened,android.widget.ScrollView.class)!=cachedScroll[0] || find(reopened,AlphabetIndex.class)!=cachedIndex)throw new AssertionError("Reopening rebuilt the cached app views");
                if(cachedIcon==null)throw new AssertionError("No populated drawer to cache");
                if(cachedScroll[0].getScrollY()!=0)throw new AssertionError("Fresh drawer opening did not reset to top");
            });
            await(()->{SenseDrawerLayout panel=find(activity.getWindow().getDecorView(),SenseDrawerLayout.class);return panel!=null && !panel.isTransitioning();},"Reopened drawer");
            android.window.OnBackAnimationCallback[] back={null};
            SenseDrawerLayout[] closing={null};Workspace[] home={null};
            main(()->{
                try{var field=MainActivity.class.getDeclaredField("back");field.setAccessible(true);back[0]=(android.window.OnBackAnimationCallback)field.get(activity);}catch(ReflectiveOperationException exception){throw new AssertionError(exception);}
                closing[0]=find(activity.getWindow().getDecorView(),SenseDrawerLayout.class);
                home[0]=find(activity.getWindow().getDecorView(),Workspace.class);
                cachedScroll[0].scrollTo(0,400);
                back[0].onBackStarted(new android.window.BackEvent(0,300,0,android.window.BackEvent.EDGE_LEFT));
                back[0].onBackProgressed(new android.window.BackEvent(100,300,.5f,android.window.BackEvent.EDGE_LEFT));
                if(closing[0].getTranslationY()<closing[0].getHeight()*.49f)throw new AssertionError("Predictive Back did not move the drawer");
                back[0].onBackCancelled();
            });
            await(()->closing[0].getTranslationY()==0 && !closing[0].isTransitioning(),"Cancelled predictive Back");
            main(()->{
                if(cachedScroll[0].getScrollY()!=400)throw new AssertionError("Cancelled Back reset drawer scroll");
                back[0].onBackInvoked();
                if(android.animation.ValueAnimator.areAnimatorsEnabled() && !closing[0].isTransitioning())throw new AssertionError("Committed Back skipped drawer closing animation");
                if(find(activity.getWindow().getDecorView(),Workspace.class)!=home[0])throw new AssertionError("Back rebuilt the home workspace before closing");
            });
            await(()->closing[0].getParent()==null,"Predictive Back completion");
            main(()->{
                if(cachedScroll[0].getScrollY()!=0)throw new AssertionError("Closing drawer did not reset cached scroll");
                cachedScroll[0].getChildAt(0).setMinimumHeight(originalMinimum[0]);
            });
            main(activity::showApps);
            await(()->{SenseDrawerLayout panel=find(activity.getWindow().getDecorView(),SenseDrawerLayout.class);return panel!=null && !panel.isTransitioning();},"Drawer before Back key");
            main(()->{
                SenseDrawerLayout panel=find(activity.getWindow().getDecorView(),SenseDrawerLayout.class);
                activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK));
                activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK));
                if(android.animation.ValueAnimator.areAnimatorsEnabled() && !panel.isTransitioning())throw new AssertionError("Back key skipped closing animation");
            });
            await(()->find(activity.getWindow().getDecorView(),SenseDrawerLayout.class)==null,"Back key closes drawer");
            PageOverview[] overview={null};View[] editHeader={null};
            main(()->{
                overview[0]=new PageOverview(find(activity.getWindow().getDecorView(),Workspace.class));overview[0].show();
                ViewGroup container=overview[0].getWindow().getDecorView().findViewById(android.R.id.content);
                ViewGroup content=(ViewGroup)container.getChildAt(0);editHeader[0]=content.getChildAt(0);
                if(android.animation.ValueAnimator.areAnimatorsEnabled() && editHeader[0].getAlpha()>.99f)throw new AssertionError("Edit header skipped entry animation");
            });
            await(()->editHeader[0].getAlpha()>.999f,"Stock edit header entry");
            main(()->{
                overview[0].dismiss();
                if(android.animation.ValueAnimator.areAnimatorsEnabled() && !overview[0].isShowing())throw new AssertionError("Edit header skipped exit animation");
            });
            await(()->!overview[0].isShowing(),"Stock edit header exit");
            await(()->find(activity.getWindow().getDecorView(),BentDockLayout.class).getVisibility()==View.VISIBLE,"Dock restoration after overview");
            report.putString("result","Touch-following pages, recorded drawing, widget reuse, interrupted settle, cancel rollback, saved commit, asynchronous warm-cache opening without drawer replacement, app data and populated view reuse across reopening, alphabet index, bottom tabs, interactive drawer pull, predictive Back commit/cancel Back key closing, recovered dock hide/show and edit-header entry/exit passed");
            test.finish(Activity.RESULT_OK,report);
        }catch(Throwable error){main(()->{restore();if(workspace!=null)workspace.prefs.edit().clear().commit();});report.putString("error",android.util.Log.getStackTraceString(error));test.finish(Activity.RESULT_CANCELED,report);}
    }
    private void restore(){if(parent!=null && workspace!=null && workspace.getParent()==parent){var params=workspace.getLayoutParams();parent.removeView(workspace);parent.addView(original,index,params);}}
    private void swipe(int action,float x,float y){
        MotionEvent event=MotionEvent.obtain(0,SystemClock.uptimeMillis(),action,workspace.getWidth()*x,workspace.getHeight()*y,0);
        workspace.dispatchTouchEvent(event);event.recycle();
    }
    private void main(Runnable action){final Throwable[] failure={null};test.runOnMainSync(()->{try{action.run();}catch(Throwable error){failure[0]=error;}});if(failure[0]!=null)throw new AssertionError(failure[0]);}
    private void await(BooleanSupplier condition,String label)throws Exception{
        long deadline=SystemClock.uptimeMillis()+10000;
        while(SystemClock.uptimeMillis()<deadline){boolean[] ready={false};main(()->ready[0]=condition.getAsBoolean());if(ready[0])return;Thread.sleep(50);}
        throw new AssertionError(label+" timed out");
    }
    private <T extends View>T find(View view,Class<T> type){
        if(type.isInstance(view))return type.cast(view);
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){T result=find(group.getChildAt(i),type);if(result!=null)return result;}return null;
    }
}
