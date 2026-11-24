package dev.sense.weather;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.TextureView;
import android.widget.FrameLayout;
import android.widget.ImageView;

/** Original full-scene footage, hardware decoded and center-cropped behind native UI. */
public final class WeatherSceneView extends FrameLayout implements TextureView.SurfaceTextureListener {
    private final ImageView poster;
    private final TextureView texture;
    private MediaPlayer player;
    private Surface surface;
    private String movie;
    private boolean active, visible, prepared;
    private int videoWidth=960,videoHeight=960;
    public WeatherSceneView(Context context,AttributeSet attrs){
        super(context,attrs);setBackgroundColor(0xff17222c);
        poster=new ImageView(context);poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        addView(poster,new LayoutParams(-1,-1));
        texture=new TextureView(context);texture.setOpaque(false);texture.setSurfaceTextureListener(this);
        addView(texture,new LayoutParams(-1,-1));setOnClickListener(v->{release();prepare();});
    }
    static String movie(int code,boolean day){
        return switch(code){
            case 0,1 -> day?"sunny":"clear";
            case 2 -> day?"partly_sunny":"partly_cloud_night";
            case 3 -> day?"cloudy_day":"cloudy_night";
            case 45,48 -> day?"fog_day":"fog_night";
            case 51,53,55,56,57,61,63,65,66,67,80,81,82 -> day?"rain":"rain_night";
            case 71,73,75,77,85,86 -> day?"snow_day":"snow_night";
            case 95,96,97,99 -> day?"thunderstorm_day":"thunderstorm_night";
            default -> null;
        };
    }
    void condition(int code,boolean day){
        String next=movie(code,day);if(java.util.Objects.equals(movie,next))return;
        release();movie=next;
        String still="rain".equals(movie)?"rain_day":movie;
        int image=still==null?0:getResources().getIdentifier("wx_video_"+still,"drawable",getContext().getPackageName());
        poster.setImageResource(image);prepare();
    }
    void setActive(boolean value){active=value;if(value)prepare();else release();}
    boolean playing(){return player!=null && prepared && player.isPlaying();}
    String currentMovie(){return movie;}
    private void prepare(){
        if(player!=null || movie==null || !active || !visible || !texture.isAvailable()
                || !android.animation.ValueAnimator.areAnimatorsEnabled())return;
        int id=getResources().getIdentifier("wx_video_"+movie,"raw",getContext().getPackageName());
        if(id==0)return;
        MediaPlayer next=new MediaPlayer();player=next;
        try(var file=getResources().openRawResourceFd(id)){
            surface=new Surface(texture.getSurfaceTexture());next.setSurface(surface);
            next.setVolume(0,0);next.setLooping(true);
            next.setDataSource(file.getFileDescriptor(),file.getStartOffset(),file.getLength());
            next.setOnVideoSizeChangedListener((p,w,h)->{if(player==p){videoWidth=w;videoHeight=h;crop();}});
            next.setOnPreparedListener(p->{if(player==p && active && visible){prepared=true;p.start();}});
            next.setOnInfoListener((p,what,extra)->{if(player==p && what==MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START)texture.setAlpha(1f);return false;});
            next.setOnErrorListener((p,what,extra)->{if(player==p)release();return true;});
            next.prepareAsync();
        }catch(java.io.IOException|RuntimeException error){release();}
    }
    private void release(){
        prepared=false;
        if(player!=null){MediaPlayer old=player;player=null;old.reset();old.release();}
        if(surface!=null){surface.release();surface=null;}
        texture.setAlpha(0f);
    }
    private void crop(){
        int w=texture.getWidth(),h=texture.getHeight();if(w==0||h==0||videoWidth==0||videoHeight==0)return;
        float scale=Math.max(w/(float)videoWidth,h/(float)videoHeight);
        Matrix transform=new Matrix();transform.setScale(videoWidth*scale/w,videoHeight*scale/h,w/2f,h/2f);
        texture.setTransform(transform);
    }
    @Override public void onVisibilityAggregated(boolean value){super.onVisibilityAggregated(value);visible=value;if(value)prepare();else release();}
    @Override protected void onDetachedFromWindow(){release();super.onDetachedFromWindow();}
    @Override public void onSurfaceTextureAvailable(SurfaceTexture value,int w,int h){crop();prepare();}
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture value,int w,int h){crop();}
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture value){release();return true;}
    @Override public void onSurfaceTextureUpdated(SurfaceTexture value){}
}
