package dev.sense.launcher;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.*;
import android.widget.TextView;
import java.util.function.BooleanSupplier;

/** Attached two-finger gestures and non-destructive page operations in the Leap window. */
final class LeapInstrumentation {
    private final Instrumentation test;
    private Workspace workspace,original;
    private ViewGroup parent;
    private int index;
    private PageOverview overview;
    LeapInstrumentation(Instrumentation test){this.test=test;}
    void run(){
        Bundle report=new Bundle();
        try{
            MainActivity activity=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            main(()->{
                original=find(activity.getWindow().getDecorView(),Workspace.class);parent=(ViewGroup)original.getParent();index=parent.indexOfChild(original);
                var prefs=activity.getSharedPreferences("leap_instrumentation",0);
                prefs.edit().clear().putString("items","[]").putInt("pages",7).putInt("currentPage",4).commit();
                workspace=new Workspace(activity,new SenseWidgetHost(activity,9020),prefs);
                var params=original.getLayoutParams();parent.removeView(original);parent.addView(workspace,index,params);
            });test.waitForIdleSync();
            main(()->{
                android.graphics.Bitmap clean=workspace.pagePreview(4,308,460);
                org.json.JSONObject stale=new org.json.JSONObject();
                Workspace.put(stale,"type","widget");Workspace.put(stale,"id",Integer.MAX_VALUE);
                Workspace.put(stale,"page",4);Workspace.put(stale,"x",0);Workspace.put(stale,"y",0);Workspace.put(stale,"w",2);Workspace.put(stale,"h",2);
                workspace.items.put(stale);
                android.graphics.Bitmap current=workspace.pagePreview(4,308,460);
                Workspace.put(stale,"page",5);
                android.graphics.Bitmap other=workspace.pagePreview(5,308,460);
                if(!clean.sameAs(current) || !clean.sameAs(other))throw new AssertionError("Stale widget appeared in Leap preview");
                float expected=workspace.getWidth()/(float)workspace.getHeight(),actual=clean.getWidth()/(float)clean.getHeight();
                if(Math.abs(expected-actual)>.005f)throw new AssertionError("Thumbnail distorted workspace aspect ratio");
                clean.recycle();current.recycle();other.recycle();workspace.items=new org.json.JSONArray();
                org.json.JSONObject shortcut=new org.json.JSONObject();
                Workspace.put(shortcut,"type","app");Workspace.put(shortcut,"component",new android.content.ComponentName(activity,MainActivity.class).flattenToString());
                Workspace.put(shortcut,"page",4);Workspace.put(shortcut,"x",1);Workspace.put(shortcut,"y",2);Workspace.put(shortcut,"w",1);Workspace.put(shortcut,"h",1);
                workspace.items.put(shortcut);workspace.render();
                android.graphics.Bitmap rebuilt=workspace.pagePreview(4,308,460);
                Workspace.put(shortcut,"page",5);android.graphics.Bitmap offscreen=workspace.pagePreview(5,308,460);
                if(!rebuilt.sameAs(offscreen))throw new AssertionError("Current and offscreen shortcut layouts disagree");
                boolean visible=false;for(int y=0;y<rebuilt.getHeight();y++)for(int x=0;x<rebuilt.getWidth();x++)if(android.graphics.Color.alpha(rebuilt.getPixel(x,y))>0)visible=true;
                if(!visible)throw new AssertionError("Preview after render was blank before the next layout");
                rebuilt.recycle();offscreen.recycle();workspace.items=new org.json.JSONArray();workspace.render();
            });
            main(()->{
                touch(MotionEvent.ACTION_DOWN,1,.15f,.85f);
                touch(MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),2,.15f,.85f);
                touch(MotionEvent.ACTION_MOVE,2,.3f,.7f);
                overview=activeOverview();
                if(overview==null || !overview.isShowing() || overview.progress()<=0 || overview.progress()>=1)throw new AssertionError("Pinch did not drive partial Leap progress");
                touch(MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),2,.3f,.7f);
            });
            await(()->overview.progress()>.999f,"Pinch completion");
            main(()->{
                View root=overview.getWindow().getDecorView();
                ViewGroup holder=root.findViewById(android.R.id.content);View shade=holder.getChildAt(0);int[] origin=new int[2];shade.getLocationOnScreen(origin);
                var screen=activity.getWindowManager().getCurrentWindowMetrics().getBounds();
                if(origin[1]!=screen.top || origin[1]+shade.getHeight()!=screen.bottom)throw new AssertionError("Leap shade does not cover the complete screen");
                int top=root.getRootWindowInsets().getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()).top;
                int[] titlePosition=new int[2];text(root,"Home pages").getLocationOnScreen(titlePosition);
                if(titlePosition[1]<top)throw new AssertionError("Leap title overlaps status bar");
                View four=description(root,"Open home page 4, current page");
                View one=description(root,"Open home page 1");View three=description(root,"Open home page 3");View five=description(root,"Open home page 5");
                if(four==null || one==null || three==null || five==null)throw new AssertionError("Seven-slot thumbnails missing");
                if(!(one.getTranslationY()<four.getTranslationY() && three.getTranslationX()<four.getTranslationX() && five.getTranslationX()>four.getTranslationX()))throw new AssertionError("Leap lost its original 2/3/2 layout");
                one.performAccessibilityAction(0x01020002,null);
                if(workspace.pages!=7 || workspace.page!=4)throw new AssertionError("Page reorder changed count or selected identity");
                text(root,"Add page").performClick();
                if(workspace.pages!=8 || description(root,"Open home page 8")==null)throw new AssertionError("Additional pages became inaccessible");
                description(root,"Remove home page 8").performClick();
                if(workspace.pages!=7)throw new AssertionError("Empty page removal failed");
                overview.dismiss();
            });await(()->!overview.isShowing(),"Leap dismissal");
            main(()->{
                touch(MotionEvent.ACTION_DOWN,1,.15f,.85f);
                touch(MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),2,.15f,.85f);
                touch(MotionEvent.ACTION_MOVE,2,.19f,.81f);overview=activeOverview();
                if(overview==null || !overview.isShowing())throw new AssertionError("Short pinch did not begin");
                touch(MotionEvent.ACTION_CANCEL,2,.19f,.81f);
            });await(()->!overview.isShowing(),"Cancelled pinch");
            main(()->{
                if(workspace.pages!=7 || workspace.page!=4)throw new AssertionError("Cancelled pinch changed pages");
                overview=new PageOverview(workspace);overview.show();
            });await(()->overview.progress()>.999f,"Normal entry");
            main(()->description(overview.getWindow().getDecorView(),"Open home page 2").performClick());
            await(()->!overview.isShowing(),"Page selection zoom");
            main(()->{if(workspace.page!=2 || workspace.prefs.getInt("currentPage",0)!=2)throw new AssertionError("Selected page was not saved");});
            test.waitForIdleSync();
            nativeTouch(MotionEvent.ACTION_DOWN,1,.15f,.85f);
            nativeTouch(MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),2,.15f,.85f);
            nativeTouch(MotionEvent.ACTION_MOVE,2,.3f,.7f);
            await(()->{overview=activeOverview();return overview!=null && overview.isShowing();},"Native pinch enters Leap");
            nativeTouch(MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),2,.3f,.7f);
            nativeTouch(MotionEvent.ACTION_UP,1,.3f,.7f);
            await(()->overview.progress()>.999f && overview.isShowing(),"Native window gesture completes Leap");
            View[] border={null};
            main(()->{border[0]=overview.getWindow().getDecorView().findViewWithTag("leap_selection_border");overview.dismiss();});
            if(android.animation.ValueAnimator.areAnimatorsEnabled()){
                await(()->border[0]!=null && border[0].getAlpha()<.95f && border[0].getAlpha()>0,"Selection border fades during closing");
            }
            await(()->!overview.isShowing(),"Native gesture dismissal");
            main(this::restore);
            report.putString("result","Full-screen shade including system bars, safely inset controls, pinch-driven Leap progress, commit/cancel, stock seven-slot layout, page reorder, extra-page bank, add/remove and persisted selection passed");test.finish(Activity.RESULT_OK,report);
        }catch(Throwable error){main(()->{if(overview!=null)overview.dismiss();restore();});report.putString("error",android.util.Log.getStackTraceString(error));test.finish(Activity.RESULT_CANCELED,report);}
    }
    private PageOverview activeOverview(){try{var field=Workspace.class.getDeclaredField("pageOverview");field.setAccessible(true);return (PageOverview)field.get(workspace);}catch(Exception e){throw new AssertionError(e);}}
    private void restore(){if(parent!=null && workspace!=null && workspace.getParent()==parent){var params=workspace.getLayoutParams();parent.removeView(workspace);parent.addView(original,index,params);}if(workspace!=null)workspace.prefs.edit().clear().commit();}
    private void touch(int action,int count,float left,float right){
        MotionEvent.PointerProperties[] props=new MotionEvent.PointerProperties[count];MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[count];
        for(int i=0;i<count;i++){props[i]=new MotionEvent.PointerProperties();props[i].id=i;props[i].toolType=MotionEvent.TOOL_TYPE_FINGER;coords[i]=new MotionEvent.PointerCoords();coords[i].x=workspace.getWidth()*(i==0?left:right);coords[i].y=workspace.getHeight()*.5f;coords[i].pressure=1;coords[i].size=1;}
        long time=SystemClock.uptimeMillis();MotionEvent e=MotionEvent.obtain(time,time,action,count,props,coords,0,0,1,1,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0);workspace.dispatchTouchEvent(e);e.recycle();
    }
    private long nativeDown;
    private void nativeTouch(int action,int count,float left,float right){
        int[] origin=new int[2];main(()->workspace.getLocationOnScreen(origin));
        if(action==MotionEvent.ACTION_DOWN)nativeDown=SystemClock.uptimeMillis();
        MotionEvent.PointerProperties[] props=new MotionEvent.PointerProperties[count];MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[count];
        for(int i=0;i<count;i++){props[i]=new MotionEvent.PointerProperties();props[i].id=i;props[i].toolType=MotionEvent.TOOL_TYPE_FINGER;coords[i]=new MotionEvent.PointerCoords();coords[i].x=origin[0]+workspace.getWidth()*(i==0?left:right);coords[i].y=origin[1]+workspace.getHeight()*.5f;coords[i].pressure=1;coords[i].size=1;}
        MotionEvent e=MotionEvent.obtain(nativeDown,SystemClock.uptimeMillis(),action,count,props,coords,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);if(!test.getUiAutomation().injectInputEvent(e,true))throw new AssertionError("Native pinch injection failed");e.recycle();
    }
    private void main(Runnable action){Throwable[] error={null};test.runOnMainSync(()->{try{action.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError(error[0]);}
    private void await(BooleanSupplier condition,String message)throws Exception{long end=SystemClock.uptimeMillis()+5000;while(SystemClock.uptimeMillis()<end){boolean[] result={false};main(()->result[0]=condition.getAsBoolean());if(result[0])return;Thread.sleep(30);}throw new AssertionError(message);}
    private static <T extends View>T find(View root,Class<T> type){if(type.isInstance(root))return type.cast(root);if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){T found=find(group.getChildAt(i),type);if(found!=null)return found;}return null;}
    private static View description(View root,String value){if(value.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))return root;if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=description(group.getChildAt(i),value);if(found!=null)return found;}return null;}
    private static View text(View root,String value){if(root instanceof TextView label && value.contentEquals(label.getText()))return root;if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=text(group.getChildAt(i),value);if(found!=null)return found;}return null;}
}
