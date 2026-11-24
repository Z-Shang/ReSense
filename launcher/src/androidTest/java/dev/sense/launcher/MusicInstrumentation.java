package dev.sense.launcher;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.media.*;
import android.media.session.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.function.BooleanSupplier;

/** Real platform sessions and transport callbacks; access is restored after emulator testing. */
final class MusicInstrumentation {
    private final Instrumentation test;
    private MainActivity activity;
    private Workspace workspace,original;
    private ViewGroup parent;
    private int index;
    private SenseMusicView widget;
    private MediaSession first,second;
    private MediaRepository media;
    private int played,paused,next,previous,held;
    private long sought=-1;
    private boolean originalAccess;
    MusicInstrumentation(Instrumentation test){this.test=test;}
    void run(){
        Bundle report=new Bundle();
        try{
            activity=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            main(()->{
                media=MediaRepository.get(activity);originalAccess=media.hasAccess();
                var providers=android.appwidget.AppWidgetManager.getInstance(activity).getInstalledProviders();
                var rows=WidgetPicker.groupedRows(activity,providers);
                int choices=0,music=0;String group=null;java.util.Set<String> seen=new java.util.HashSet<>();
                for(var row:rows){
                    if(row.header()){
                        if(!seen.add(row.group()))throw new AssertionError("Provider group split into multiple sections");
                        group=row.group();
                    }else{
                        if(!row.group().equals(group))throw new AssertionError("Widget is under the wrong provider heading");
                        choices++;if(row.music())music++;else if(!row.search() && !providers.contains(row.info()))throw new AssertionError("Widget selection lost provider identity");
                    }
                }
                if(choices!=providers.size()+4 || music!=3)throw new AssertionError("Grouped picker lost or duplicated widget choices");
            });
            access(true);await(()->media.hasAccess(),"Notification-listener grant");
            main(()->{
                first=new MediaSession(activity,"Sense music regression");first.setCallback(new MediaSession.Callback(){
                    @Override public void onPlay(){played++;playback(first,PlaybackState.STATE_PLAYING,1000,actions());}
                    @Override public void onPause(){paused++;playback(first,PlaybackState.STATE_PAUSED,1000,actions());}
                    @Override public void onSkipToNext(){next++;metadata(first,"Second song",Color.BLUE);}
                    @Override public void onSkipToPrevious(){previous++;metadata(first,"First song",Color.RED);}
                    @Override public void onSeekTo(long position){sought=position;playback(first,PlaybackState.STATE_PAUSED,position,actions());}
                },new Handler(Looper.getMainLooper()));
                metadata(first,"First song",Color.RED);playback(first,PlaybackState.STATE_PAUSED,1000,actions());first.setActive(true);
                original=find(activity.getWindow().getDecorView(),Workspace.class);parent=(ViewGroup)original.getParent();index=parent.indexOfChild(original);
                var prefs=activity.getSharedPreferences("music_instrumentation",0);prefs.edit().clear().putString("items","[]").putInt("pages",1).putInt("currentPage",1).commit();
                workspace=new Workspace(activity,new SenseWidgetHost(activity,9030),prefs);
                var params=original.getLayoutParams();parent.removeView(original);parent.addView(workspace,index,params);
                workspace.addMusic();widget=find(workspace,SenseMusicView.class);
                widget.setOnLongClickListener(v->{held++;return true;});
            });
            test.waitForIdleSync();
            await(()->media.state().controller()!=null && "First song".contentEquals(media.state().metadata().getDescription().getTitle()),"Track metadata");
            main(()->{
                if(media.state().artwork()==null || media.state().artwork().getPixel(0,0)!=Color.RED)throw new AssertionError("Album artwork missing");
                if(!workspace.prefs.getString("items","").contains("music"))throw new AssertionError("Music widget not persisted");
                View play=description(widget,"Play");if(play==null || !play.isEnabled())throw new AssertionError("Play control unavailable");play.performClick();
            });await(()->played==1 && media.state().playing(),"Play transport callback");
            main(()->description(widget,"Pause").performClick());await(()->paused==1 && !media.state().playing(),"Pause transport callback");
            main(()->description(widget,"Next track").performClick());await(()->next==1 && media.state().artwork()!=null && media.state().artwork().getPixel(0,0)==Color.BLUE,"Next and new artwork");
            main(()->description(widget,"Previous track").performClick());await(()->previous==1 && "First song".contentEquals(media.state().metadata().getDescription().getTitle()) && media.state().artwork()!=null && media.state().artwork().getPixel(0,0)==Color.RED,"Previous transport callback");
            main(()->media.seek(25000));await(()->sought==25000 && media.state().position()==25000,"Seek transport callback");
            // The cover opens the source's explicit session destination.
            PendingIntent[] destination={null};
            var opened=test.addMonitor(LauncherSettingsActivity.class.getName(),null,false);
            main(()->{
                destination[0]=PendingIntent.getActivity(activity,7301,new Intent(activity,LauncherSettingsActivity.class)
                        .putExtra("section","integrations"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
                first.setSessionActivity(destination[0]);
            });
            await(()->media.state().controller().getSessionActivity()!=null,"Session launch destination");
            main(()->description(widget,"Open current media app").performClick());
            Activity player=test.waitForMonitorWithTimeout(opened,5000);test.removeMonitor(opened);
            if(player==null)throw new AssertionError("Cover did not open session activity");
            main(player::finish);
            await(()->media.state().controller()!=null,"Media reconnected after source launch");
            main(()->{
                Intent[] fallback={null};
                Context captureLaunch=new ContextWrapper(activity){
                    @Override public void startActivity(Intent intent){fallback[0]=intent;}
                };
                // A canceled session destination must fall back to its source package.
                destination[0].cancel();media.openPlayer(captureLaunch);
                if(fallback[0]==null || !activity.getPackageName().equals(fallback[0].getComponent().getPackageName()))
                    throw new AssertionError("Canceled session destination did not open source package");
                first.setSessionActivity(null);
            });
            await(()->media.state().controller().getSessionActivity()==null,"Missing session destination");
            main(()->{
                Intent[] fallback={null};
                media.openPlayer(new ContextWrapper(activity){@Override public void startActivity(Intent intent){fallback[0]=intent;}});
                if(fallback[0]==null || !activity.getPackageName().equals(fallback[0].getComponent().getPackageName()))
                    throw new AssertionError("Missing session destination did not open source package");
            });
            capture(2);
            main(()->{
                second=new MediaSession(activity,"Other player");metadata(second,"Other player song",Color.GREEN);playback(second,PlaybackState.STATE_PLAYING,5000,actions());second.setActive(true);
            });await(()->media.state().controller()!=null && media.state().controller().getSessionToken().equals(second.getSessionToken()),"Playing session preferred over paused");
            main(()->second.release());await(()->media.state().controller()!=null && media.state().controller().getSessionToken().equals(first.getSessionToken()),"Session destruction fallback");
            main(()->{
                View play=description(widget,"Play");
                downX=play.getLeft()+play.getWidth()/2f;downY=play.getTop()+play.getHeight()/2f;
                touch(MotionEvent.ACTION_DOWN);
            });Thread.sleep(ViewConfiguration.getLongPressTimeout()+180);
            main(()->{touch(MotionEvent.ACTION_UP);if(held!=1 || played!=1)throw new AssertionError("Music button consumed launcher long press");
                Bitmap preview=workspace.pagePreview(workspace.page,308,460);boolean visible=false;
                for(int y=0;y<preview.getHeight();y+=4)for(int x=0;x<preview.getWidth();x+=4)if(Color.alpha(preview.getPixel(x,y))>0)visible=true;
                preview.recycle();if(!visible)throw new AssertionError("Music missing from Leap preview");
                playback(first,PlaybackState.STATE_PAUSED,25000,0);
            });await(()->!description(widget,"Play").isEnabled() && !description(widget,"Next track").isEnabled(),"Unsupported actions disabled");
            for(int style:new int[]{1,4}){
                main(()->{
                    playback(first,PlaybackState.STATE_PAUSED,25000,actions());
                    workspace.items=new org.json.JSONArray();workspace.page=1;workspace.pages=1;workspace.addMusic(style);
                    widget=find(workspace,SenseMusicView.class);
                    if(widget.styleRows!=style || workspace.items.optJSONObject(0).optInt("h")!=style)throw new AssertionError("Wrong music variant or grid span");
                    if(workspace.items.optJSONObject(0).optInt("musicRows")!=style)throw new AssertionError("Music style was not saved");
                    workspace.render();widget=find(workspace,SenseMusicView.class);
                    if(widget.styleRows!=style)throw new AssertionError("Music style lost during reload");
                });
                test.waitForIdleSync();await(()->description(widget,"Play").isEnabled(),"Variant "+style+" media binding");
                int[] before=new int[2];
                main(()->{before[0]=played;before[1]=paused;description(widget,"Play").performClick();});
                await(()->played==before[0]+1 && media.state().playing(),"Variant "+style+" play");
                main(()->description(widget,"Pause").performClick());await(()->paused==before[1]+1 && !media.state().playing(),"Variant "+style+" pause");
                main(()->{
                    Bitmap preview=workspace.pagePreview(workspace.page,308,460);
                    if(preview.getWidth()==0 || preview.getHeight()==0)throw new AssertionError("Variant preview missing");preview.recycle();
                });
                capture(style);
            }
            access(false);main(()->media.refreshAccess());await(()->!media.state().access() && media.state().controller()==null,"Access revocation");
            main(()->{if(text(widget,"Enable media access")==null)throw new AssertionError("Missing media setup state");});
            cleanup();if(originalAccess)access(true);
            report.putString("result","All three music sizes, persisted styles, grid spans, artwork layering and playback controls; music widget placement/preview, real metadata/artwork, play/pause/next/previous/seek callbacks, playing-session selection, session destruction, unsupported actions, long-press priority and access revocation passed");test.finish(Activity.RESULT_OK,report);
        }catch(Throwable error){try{cleanup();access(originalAccess);}catch(Exception ignored){}report.putString("error",android.util.Log.getStackTraceString(error));test.finish(Activity.RESULT_CANCELED,report);}
    }
    private void capture(int style)throws Exception{
        test.waitForIdleSync();
        java.util.concurrent.CountDownLatch drawn=new java.util.concurrent.CountDownLatch(1);
        main(()->widget.postOnAnimation(()->widget.postOnAnimation(drawn::countDown)));
        if(!drawn.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("Music artwork frame was not drawn");
        Bitmap screen=test.getUiAutomation().takeScreenshot();
        if(screen==null)throw new AssertionError("Missing music screenshot");
        int[] location=new int[2];main(()->widget.getLocationOnScreen(location));
        int refHeight=style==1?142:style==4?748:344;
        float scale=Math.min(widget.getWidth()/530f,widget.getHeight()/(float)refHeight);
        int x=Math.round(location[0]+(widget.getWidth()-530*scale)/2+(style==1?90:style==4?260:150)*scale);
        int y=Math.round(location[1]+(widget.getHeight()-refHeight*scale)/2+(style==1?60:style==4?160:100)*scale);
        int pixel=screen.getPixel(x,y);
        if(Color.red(pixel)<180 || Color.blue(pixel)>80 || Color.green(pixel)>80)throw new AssertionError("Album artwork hidden in variant "+style+": "+Integer.toHexString(pixel));
        try(var output=new java.io.FileOutputStream(new java.io.File(test.getTargetContext().getFilesDir(),"music-widget-"+style+".png"))){screen.compress(Bitmap.CompressFormat.PNG,100,output);}screen.recycle();
    }
    private float downX,downY;private long downTime;
    private void touch(int action){if(action==MotionEvent.ACTION_DOWN)downTime=SystemClock.uptimeMillis();MotionEvent event=MotionEvent.obtain(downTime,SystemClock.uptimeMillis(),action,downX,downY,0);widget.dispatchTouchEvent(event);event.recycle();}
    private void cleanup(){main(()->{if(first!=null)first.release();if(second!=null)second.release();if(parent!=null && workspace!=null && workspace.getParent()==parent){var params=workspace.getLayoutParams();parent.removeView(workspace);parent.addView(original,index,params);workspace.prefs.edit().clear().commit();}});}
    private void access(boolean allowed)throws Exception{
        String command="cmd notification "+(allowed?"allow_listener ":"disallow_listener ")+"dev.sense.launcher/dev.sense.launcher.MediaAccessService";
        try(var fd=test.getUiAutomation().executeShellCommand(command);var input=new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)){input.readAllBytes();}
    }
    private static long actions(){return PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE|PlaybackState.ACTION_SKIP_TO_NEXT|PlaybackState.ACTION_SKIP_TO_PREVIOUS|PlaybackState.ACTION_SEEK_TO;}
    private static void playback(MediaSession session,int state,long position,long actions){session.setPlaybackState(new PlaybackState.Builder().setActions(actions).setState(state,position,state==PlaybackState.STATE_PLAYING?1:0,SystemClock.elapsedRealtime()).build());}
    private static void metadata(MediaSession session,String title,int color){Bitmap art=Bitmap.createBitmap(20,20,Bitmap.Config.ARGB_8888);art.eraseColor(color);session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,title).putString(MediaMetadata.METADATA_KEY_ARTIST,"Test artist").putLong(MediaMetadata.METADATA_KEY_DURATION,90000).putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART,art).build());}
    private void main(Runnable action){Throwable[] error={null};test.runOnMainSync(()->{try{action.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError(error[0]);}
    private void await(BooleanSupplier condition,String message)throws Exception{long end=SystemClock.uptimeMillis()+7000;while(SystemClock.uptimeMillis()<end){boolean[] result={false};main(()->result[0]=condition.getAsBoolean());if(result[0])return;Thread.sleep(40);}throw new AssertionError(message);}
    private static <T extends View>T find(View root,Class<T> type){if(type.isInstance(root))return type.cast(root);if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){T found=find(group.getChildAt(i),type);if(found!=null)return found;}return null;}
    private static View description(View root,String value){if(value.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))return root;if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=description(group.getChildAt(i),value);if(found!=null)return found;}return null;}
    private static View text(View root,String value){if(root instanceof TextView label && value.contentEquals(label.getText()))return root;if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=text(group.getChildAt(i),value);if(found!=null)return found;}return null;}
}
