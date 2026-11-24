package dev.sense.launcher;

import android.app.NotificationManager;
import android.content.*;
import android.graphics.*;
import android.media.MediaMetadata;
import android.media.session.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** One callback-driven active-session subscription shared by attached music widgets. */
final class MediaRepository {
    record State(boolean access,MediaController controller,MediaMetadata metadata,PlaybackState playback,Bitmap artwork){
        boolean playing(){return playback!=null && playback.getState()==PlaybackState.STATE_PLAYING;}
        boolean supports(long action){return controller!=null && playback!=null && (playback.getActions()&action)!=0;}
        long duration(){return metadata==null?0:Math.max(0,metadata.getLong(MediaMetadata.METADATA_KEY_DURATION));}
        long position(){
            if(playback==null)return 0;
            long position=playback.getPosition();
            if(playing() && playback.getLastPositionUpdateTime()>0)position+=(long)((SystemClock.elapsedRealtime()-playback.getLastPositionUpdateTime())*playback.getPlaybackSpeed());
            return Math.max(0,duration()>0?Math.min(duration(),position):position);
        }
    }
    private static MediaRepository instance;
    static synchronized MediaRepository get(Context context){if(instance==null)instance=new MediaRepository(context.getApplicationContext());return instance;}
    private final Context context;
    private final MediaSessionManager manager;
    private final ComponentName component;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Set<Consumer<State>> listeners=new LinkedHashSet<>();
    private final Map<MediaSession.Token,MediaController> controllers=new LinkedHashMap<>();
    private final MediaSessionManager.OnActiveSessionsChangedListener sessions=this::setControllers;
    private final MediaController.Callback changed=new MediaController.Callback(){
        @Override public void onMetadataChanged(MediaMetadata metadata){publish();}
        @Override public void onPlaybackStateChanged(PlaybackState state){publish();}
        @Override public void onSessionDestroyed(){refreshAccess();}
    };
    private State state=new State(false,null,null,null,null);
    private boolean registered;
    private ExecutorService artworkWorker;
    private String artworkUri;
    private Bitmap decodedArtwork;
    private int artworkGeneration;
    private MediaRepository(Context context){this.context=context;manager=context.getSystemService(MediaSessionManager.class);component=new ComponentName(context,MediaAccessService.class);}
    State state(){return state;}
    void subscribe(Consumer<State> listener){listeners.add(listener);refreshAccess();listener.accept(state);}
    void unsubscribe(Consumer<State> listener){listeners.remove(listener);if(listeners.isEmpty()){stop();clearArtwork();}}
    boolean hasAccess(){return context.getSystemService(NotificationManager.class).isNotificationListenerAccessGranted(component);}
    void requestAccess(Context activity){
        Intent intent=new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,component.flattenToString());
        if(!(activity instanceof android.app.Activity))intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try{activity.startActivity(intent);}catch(ActivityNotFoundException error){activity.startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    }
    void refreshAccess(){
        if(Looper.myLooper()!=Looper.getMainLooper()){main.post(this::refreshAccess);return;}
        if(listeners.isEmpty())return;
        if(!hasAccess()){stop();clearArtwork();state=new State(false,null,null,null,null);notifyListeners();return;}
        try{
            if(!registered){manager.addOnActiveSessionsChangedListener(sessions,component,main);registered=true;}
            setControllers(manager.getActiveSessions(component));
        }catch(SecurityException error){stop();clearArtwork();state=new State(false,null,null,null,null);notifyListeners();}
    }
    private void stop(){
        if(registered){manager.removeOnActiveSessionsChangedListener(sessions);registered=false;}
        for(MediaController controller:controllers.values())controller.unregisterCallback(changed);controllers.clear();
    }
    private void setControllers(List<MediaController> active){
        if(listeners.isEmpty())return;
        Set<MediaSession.Token> live=new HashSet<>();
        for(MediaController controller:active){
            MediaSession.Token token=controller.getSessionToken();live.add(token);
            if(!controllers.containsKey(token)){controllers.put(token,controller);controller.registerCallback(changed,main);}
        }
        for(MediaSession.Token token:new ArrayList<>(controllers.keySet()))if(!live.contains(token))controllers.remove(token).unregisterCallback(changed);
        // Preserve the system's priority order for equally active players.
        Map<MediaSession.Token,MediaController> ordered=new LinkedHashMap<>();for(MediaController controller:active)ordered.put(controller.getSessionToken(),controllers.get(controller.getSessionToken()));
        controllers.clear();controllers.putAll(ordered);publish();
    }
    private static int priority(PlaybackState state){
        if(state==null)return 0;
        return switch(state.getState()){
            case PlaybackState.STATE_PLAYING->4;
            case PlaybackState.STATE_BUFFERING,PlaybackState.STATE_CONNECTING->3;
            case PlaybackState.STATE_PAUSED->2;
            default->1;
        };
    }
    private void publish(){
        MediaController selected=null;int score=-1;
        for(MediaController controller:controllers.values())if(priority(controller.getPlaybackState())>score){selected=controller;score=priority(controller.getPlaybackState());}
        MediaMetadata metadata=selected==null?null:selected.getMetadata();PlaybackState playback=selected==null?null:selected.getPlaybackState();
        Bitmap artwork=null;
        if(metadata!=null){
            artwork=metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if(artwork==null)artwork=metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if(artwork==null)artwork=metadata.getDescription().getIconBitmap();
        }
        String uri=null;
        if(artwork==null && metadata!=null){
            uri=metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI);
            if(uri==null)uri=metadata.getString(MediaMetadata.METADATA_KEY_ART_URI);
            if(uri==null && metadata.getDescription().getIconUri()!=null)uri=metadata.getDescription().getIconUri().toString();
        }
        String key=uri==null?null:selected.getSessionToken().hashCode()+":"+uri;
        if(!Objects.equals(key,artworkUri)){clearArtwork();artworkUri=key;if(uri!=null)loadArtwork(uri,artworkGeneration);}
        if(artwork==null)artwork=decodedArtwork;
        state=new State(true,selected,metadata,playback,artwork);notifyListeners();
    }
    private void clearArtwork(){
        artworkGeneration++;artworkUri=null;decodedArtwork=null;
        if(artworkWorker!=null){artworkWorker.shutdownNow();artworkWorker=null;}
    }
    private void loadArtwork(String value,int generation){
        Uri uri=Uri.parse(value);if(!"content".equals(uri.getScheme()) && !"android.resource".equals(uri.getScheme()))return;
        artworkWorker=Executors.newSingleThreadExecutor();
        artworkWorker.execute(()->{
            Bitmap result=null;
            try{
                BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
                try(var input=context.getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(input,null,options);}
                if(options.outWidth>0 && options.outHeight>0 && options.outWidth<=32768 && options.outHeight<=32768){
                    options.inJustDecodeBounds=false;options.inSampleSize=1;
                    while(options.outWidth/options.inSampleSize>1024 || options.outHeight/options.inSampleSize>1024)options.inSampleSize*=2;
                    try(var input=context.getContentResolver().openInputStream(uri)){result=BitmapFactory.decodeStream(input,null,options);}
                }
            }catch(Exception ignored){}
            Bitmap decoded=result;
            main.post(()->{if(generation!=artworkGeneration || listeners.isEmpty())return;decodedArtwork=decoded;state=new State(state.access(),state.controller(),state.metadata(),state.playback(),decoded);notifyListeners();});
        });
    }
    private void notifyListeners(){for(Consumer<State> listener:new ArrayList<>(listeners))listener.accept(state);}
    void playPause(){
        State s=state;if(s.controller()==null)return;
        if(s.playing()){if(s.supports(PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE))s.controller().getTransportControls().pause();}
        else if(s.supports(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PLAY_PAUSE))s.controller().getTransportControls().play();
    }
    void previous(){if(state.supports(PlaybackState.ACTION_SKIP_TO_PREVIOUS))state.controller().getTransportControls().skipToPrevious();}
    void next(){if(state.supports(PlaybackState.ACTION_SKIP_TO_NEXT))state.controller().getTransportControls().skipToNext();}
    void seek(long position){if(state.supports(PlaybackState.ACTION_SEEK_TO))state.controller().getTransportControls().seekTo(Math.max(0,Math.min(state.duration(),position)));}
    void openPlayer(Context context){
        MediaController source=state.controller();
        if(source==null){android.widget.Toast.makeText(context,"Start playback in a music app first",android.widget.Toast.LENGTH_SHORT).show();return;}
        // Prefer the player's own now-playing destination; only this explicit tap
        // grants our foreground launch privileges to the session PendingIntent.
        android.app.PendingIntent intent=source.getSessionActivity();
        if(intent!=null)try{
            var options=android.app.ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
            intent.send(context,0,null,null,null,null,options.toBundle());return;
        }catch(android.app.PendingIntent.CanceledException | SecurityException ignored){}
        // Some players publish metadata/controls without a session activity.
        // Open that exact package, rather than a generic music-app chooser.
        Intent launch=context.getPackageManager().getLaunchIntentForPackage(source.getPackageName());
        if(launch!=null)try{
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return;
        }catch(ActivityNotFoundException | SecurityException ignored){}
        android.widget.Toast.makeText(context,"This media source has no screen to open",android.widget.Toast.LENGTH_SHORT).show();
    }
}
