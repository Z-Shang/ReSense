package dev.sense.launcher;

import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import com.google.android.libraries.launcherclient.ILauncherOverlay;
import com.google.android.libraries.launcherclient.ILauncherOverlayCallback;

/** Google owns the feed window; this client supplies its host window and gestures. */
final class DiscoverOverlay implements ServiceConnection, View.OnAttachStateChangeListener {
    static final String GOOGLE_PACKAGE="com.google.android.googlequicksearchbox";
    static final String ACTION="com.android.launcher3.WINDOW_OVERLAY";
    interface Listener {
        void onDiscoverProgress(float progress);
        void onDiscoverAvailabilityChanged();
    }
    private final Activity activity;
    private final Listener listener;
    private final Handler main=new Handler(Looper.getMainLooper());
    private ILauncherOverlay remote;
    private boolean bound, attached, ready, destroyed;
    private int activityState, apiVersion=1;
    private float progress;
    private String status="Connecting to Google Discover…";
    private final Runnable handshakeTimeout=()->{
        if(!destroyed && bound && !ready)unavailable("Google Discover did not respond. Open the Google app to complete setup, then retry. Some builds require a compatible Discover bridge.");
    };
    private final ILauncherOverlayCallback callback=new ILauncherOverlayCallback.Stub() {
        @Override public void overlayScrollChanged(float value) {
            main.post(()->{
                if(destroyed || remote==null || !ready || !Float.isFinite(value))return;
                progress=Math.max(0,Math.min(1,value));
                listener.onDiscoverProgress(progress);
            });
        }
        @Override public void overlayStatusChanged(int value) {
            main.post(()->{
                if(destroyed || remote==null)return;
                main.removeCallbacks(handshakeTimeout);
                ready=(value&1)!=0;
                status=ready ? "Google Discover connected" : "Google Discover is unavailable. Open the Google app to complete setup, then retry.";
                if(!ready)resetProgress();
                Log.i("SenseDiscover","Overlay status="+value+" ready="+ready);
                listener.onDiscoverAvailabilityChanged();
            });
        }
    };
    DiscoverOverlay(Activity activity, Listener listener) {
        this.activity=activity;this.listener=listener;
        View decor=activity.getWindow().getDecorView();
        decor.addOnAttachStateChangeListener(this);
        attached=decor.isAttachedToWindow();
    }
    boolean isReady(){return ready;}
    boolean isVisible(){return progress>0.001f;}
    String status(){return status;}
    void start(){activityState|=1;connect();sendState();}
    void resume(){activityState|=2;connect();sendState();}
    void pause(){activityState&=~2;sendState();}
    void stop(){close();activityState&=~1;sendState();unbind();}
    void retry(){unbind();connect();}
    private Intent serviceIntent() {
        return new Intent(ACTION).setPackage(GOOGLE_PACKAGE).setData(
                Uri.parse("app://"+activity.getPackageName()+":"+android.os.Process.myUid())
                        .buildUpon().appendQueryParameter("v","7").appendQueryParameter("cv","9").build());
    }
    private void connect() {
        if(destroyed || bound || (activityState&1)==0)return;
        Intent intent=serviceIntent();
        var info=activity.getPackageManager().resolveService(intent,PackageManager.GET_META_DATA);
        if(info==null){unavailable("Install or enable the Google app to use Discover.");return;}
        apiVersion=info.serviceInfo.metaData==null ? 1 : info.serviceInfo.metaData.getInt("service.api.version",1);
        try {
            status="Connecting to Google Discover…";
            main.removeCallbacks(handshakeTimeout);
            bound=activity.bindService(intent,this,Context.BIND_AUTO_CREATE);
            if(bound)main.postDelayed(handshakeTimeout,10000);
            if(!bound)unavailable("The Google app did not accept the Discover connection.");
        }catch(SecurityException exception){
            Log.w("SenseDiscover","Google rejected overlay binding",exception);
            unavailable("This Google app requires a compatible Discover bridge. You can still open the Google app.");
        }
    }
    @Override public void onServiceConnected(ComponentName name,IBinder binder) {
        if(destroyed)return;
        remote=ILauncherOverlay.Stub.asInterface(binder);
        Log.i("SenseDiscover","Connected to "+name+" API "+apiVersion);
        attach();
    }
    @Override public void onServiceDisconnected(ComponentName name) {
        remote=null;unavailable("Google Discover disconnected. Swipe right again to retry.");
    }
    @Override public void onBindingDied(ComponentName name){unbind();if((activityState&1)!=0)connect();}
    @Override public void onNullBinding(ComponentName name){unbind();unavailable("Google Discover rejected this launcher. A compatible Discover bridge may be required.");}
    private void attach() {
        if(remote==null || !attached)return;
        try {
            WindowManager.LayoutParams params=new WindowManager.LayoutParams();
            params.copyFrom(activity.getWindow().getAttributes());
            if(params.token==null)params.token=activity.getWindow().getDecorView().getWindowToken();
            if(apiVersion>=3){
                Bundle config=new Bundle();
                config.putParcelable("layout_params",params);
                config.putParcelable("configuration",activity.getResources().getConfiguration());
                config.putInt("client_options",1);
                remote.windowAttached2(config,callback);
            }else remote.windowAttached(params,callback,1);
            sendState();
        }catch(RemoteException exception){failed(exception);}
    }
    private void sendState() {
        if(remote==null || !attached)return;
        try {
            if(apiVersion>=4)remote.setActivityState(activityState);
            else if((activityState&2)!=0)remote.onResume();else remote.onPause();
        }catch(RemoteException exception){failed(exception);}
    }
    boolean beginScroll() {
        if(!ready || remote==null)return false;
        try{remote.startScroll();return true;}catch(RemoteException exception){failed(exception);return false;}
    }
    void scroll(float value) {
        if(!ready || remote==null)return;
        try{remote.onScroll(Math.max(0,Math.min(1,value)));}catch(RemoteException exception){failed(exception);}
    }
    void endScroll(){if(remote!=null)try{remote.endScroll();}catch(RemoteException exception){failed(exception);}}
    void open(){if(ready && remote!=null)try{remote.openOverlay(1);}catch(RemoteException exception){failed(exception);}}
    void close(){if(remote!=null)try{remote.closeOverlay(0);}catch(RemoteException exception){Log.w("SenseDiscover","Cannot close overlay",exception);}resetProgress();}
    private void resetProgress(){progress=0;listener.onDiscoverProgress(0);}
    private void failed(RemoteException exception){Log.w("SenseDiscover","Overlay connection failed",exception);remote=null;unavailable("Google Discover disconnected. Retry to reconnect.");}
    private void unavailable(String message){ready=false;status=message;resetProgress();listener.onDiscoverAvailabilityChanged();}
    private void unbind(){
        main.removeCallbacks(handshakeTimeout);
        if(remote!=null && attached)try{remote.windowDetached(activity.isChangingConfigurations());}catch(RemoteException ignored){}
        remote=null;
        if(bound){activity.unbindService(this);bound=false;}
        ready=false;resetProgress();
    }
    @Override public void onViewAttachedToWindow(View view){attached=true;attach();}
    @Override public void onViewDetachedFromWindow(View view){
        if(remote!=null)try{remote.windowDetached(activity.isChangingConfigurations());}catch(RemoteException ignored){}
        attached=false;ready=false;resetProgress();
    }
    void destroy(){destroyed=true;activity.getWindow().getDecorView().removeOnAttachStateChangeListener(this);unbind();main.removeCallbacksAndMessages(null);}
}
