package dev.sense.framework.probe;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.opengl.*;
import android.os.Bundle;
import android.view.View;
import android.graphics.RectF;
import dev.sense.framework.fusion.*;
import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Actual GLES pixel checks and widget-host lifecycle checks, isolated from user launcher state. */
public final class FusionRuntimeProbe extends Instrumentation {
    public static final class ProbeActivity extends Activity { }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static byte[] join(byte[]... parts) { ByteArrayOutputStream out = new ByteArrayOutputStream(); for (byte[] p:parts) out.writeBytes(p); return out.toByteArray(); }
    static byte[] ints(int... values) { ByteBuffer b=ByteBuffer.allocate(values.length*4).order(ByteOrder.LITTLE_ENDIAN); for(int v:values)b.putInt(v);return b.array(); }
    static byte[] floats(float... values) { ByteBuffer b=ByteBuffer.allocate(values.length*4).order(ByteOrder.LITTLE_ENDIAN);for(float v:values)b.putFloat(v);return b.array(); }
    static byte[] record(String name,int kind,byte[] data) { ByteBuffer b=ByteBuffer.allocate(8+((data.length+3)&~3)).order(ByteOrder.LITTLE_ENDIAN); b.putInt((data.length<<4)|kind).putInt(Mode10.hash(name)).put(data);return b.array(); }
    static byte[] property(String name,byte[] bytes) {return record("Property",1,join(record("Name",2,ints(Mode10.hash(name))),record("Value",2,bytes)));}
    static byte[] quad(String name,int layer,int texture,float z) {
        return record("Node",1,join(property("Name",name.getBytes(java.nio.charset.StandardCharsets.UTF_16LE)),
            property("Layer",ints(layer)),property("DiffuseMap",ints(texture)),property("Position",floats(0,0,z)),
            property("Vertices",floats(0,0,0,0,0,100,0,0,1,0,100,100,0,1,1,0,100,0,0,1))));
    }
    static FxScene scene(byte[]... nodes) {return new FxScene(Mode10.read(join(ints(0x6d313021,2),record("Root",1,join(nodes)))));}
    static FxTexture pixel(int r,int g,int b,int a) {return new FxTexture(1,1,new byte[]{(byte)r,(byte)g,(byte)b,(byte)a});}
    static float[] view() {float[] m=FxTransform.identity();m[14]=-10;return m;}
    static ByteBuffer pixels() {ByteBuffer b=ByteBuffer.allocateDirect(100*100*4);GLES20.glReadPixels(0,0,100,100,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,b);check(GLES20.glGetError()==0,"Readback GL error");return b;}
    static void center(ByteBuffer data,int r,int g,int b,int a) {
        int index=(50*100+50)*4;int[] expected={r,g,b,a};
        for(int c=0;c<4;c++)check(Math.abs((data.get(index+c)&255)-expected[c])<=2,"Pixel channel "+c+": "+(data.get(index+c)&255)+" vs "+expected[c]);
    }
    @Override public void onCreate(Bundle arguments) {super.onCreate(arguments);start();}
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {gpu();host();result.putString("result","PASS GPU alpha, layer/depth order, clipping, ROM scenes, context recreation and native host lifecycle");finish(Activity.RESULT_OK,result);}
        catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));finish(Activity.RESULT_CANCELED,result);}
    }
    private void gpu() throws Exception {
        android.opengl.EGLDisplay display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        int[] version=new int[2];check(EGL14.eglInitialize(display,version,0,version,1),"EGL initialize");
        android.opengl.EGLConfig[] configs=new android.opengl.EGLConfig[1];int[] count=new int[1];
        check(EGL14.eglChooseConfig(display,new int[]{EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT,EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_DEPTH_SIZE,16,EGL14.EGL_NONE},0,configs,0,1,count,0)&&count[0]>0,"EGL config");
        for(int pass=0;pass<2;pass++) {
            android.opengl.EGLContext context=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);
            android.opengl.EGLSurface surface=EGL14.eglCreatePbufferSurface(display,configs[0],new int[]{EGL14.EGL_WIDTH,100,EGL14.EGL_HEIGHT,100,EGL14.EGL_NONE},0);
            check(EGL14.eglMakeCurrent(display,surface,surface,context),"EGL current");
            try(FxGlRenderer renderer=new FxGlRenderer(16*1024*1024,code->FxGlRenderer.Blend.SOURCE_OVER)) {
                FxCamera camera=FxCamera.orthographic(0,100,100,0,1,100);
                FxSceneInstance one=new FxSceneInstance(scene(quad("red",0,1,0)));
                renderer.draw(one,Map.of(1,pixel(255,0,0,128)),camera,view(),100,100,FxGlRenderer.Depth.LAYERS);
                center(pixels(),128,0,0,128);
                byte[] mesh=floats(0,0,0,0,0,100,0,0,1,0,100,100,0,1,1,0,100,0,0,1);
                FxScene solid=scene(record("Node",1,join(property("DiffuseColor",floats(0,1,0,.5f)),property("Vertices",mesh))));
                renderer.draw(new FxSceneInstance(solid),Map.of(),camera,view(),100,100,FxGlRenderer.Depth.LAYERS);
                center(pixels(),0,128,0,128);
                FxScene empty=scene(record("Node",1,join(property("DiffuseMap",ints(0)),property("Vertices",mesh))));
                renderer.draw(new FxSceneInstance(empty),Map.of(),camera,view(),100,100,FxGlRenderer.Depth.LAYERS);
                center(pixels(),0,0,0,0);
                FxSceneInstance both=new FxSceneInstance(scene(quad("blue",10,2,-1),quad("red",0,1,0)));
                renderer.draw(both,Map.of(1,pixel(255,0,0,128),2,pixel(0,0,255,255)),camera,view(),100,100,FxGlRenderer.Depth.LAYERS);
                center(pixels(),128,0,127,255);
                FxSceneInstance depth=new FxSceneInstance(scene(quad("red",10,1,0),quad("blue",0,2,-1)));
                renderer.draw(depth,Map.of(1,pixel(255,0,0,255),2,pixel(0,0,255,255)),camera,view(),100,100,FxGlRenderer.Depth.SORTED_3D);
                center(pixels(),255,0,0,255);
                float[] behind=view();behind[14]=10;
                renderer.draw(one,Map.of(1,pixel(255,0,0,255)),FxCamera.perspective(90,1,1,100),behind,100,100,FxGlRenderer.Depth.LAYERS);
                center(pixels(),0,0,0,0);
                FxSceneInstance clipped=new FxSceneInstance(scene(record("Node",1,join(property("DiffuseMap",ints(1)),
                    property("Vertices",floats(-1,-1,-.5f,0,0,1,-1,-2,1,0,1,1,-2,1,1,-1,1,-.5f,0,1))))));
                renderer.draw(clipped,Map.of(1,pixel(255,0,0,255)),FxCamera.perspective(90,1,1,100),FxTransform.identity(),100,100,FxGlRenderer.Depth.LAYERS);
                ByteBuffer clippedPixels=pixels();int visible=0;
                for(int i=3;i<clippedPixels.capacity();i+=4)if((clippedPixels.get(i)&255)>10)visible++;
                check(visible>100&&visible<9000,"Near-plane crossing polygon incorrectly clipped: "+visible);
                if(pass==0)originals(renderer);
            } finally {
                EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
                EGL14.eglDestroySurface(display,surface);EGL14.eglDestroyContext(display,context);
            }
        }
        EGL14.eglTerminate(display);
    }
    private void originals(FxGlRenderer renderer)throws Exception {
        Mode10.Record shared;
        try(InputStream in=getContext().getAssets().open("fusion/shared.m10")){shared=Mode10.read(in);}
        FxResources resources=new FxResources(shared,null,32*1024*1024);int rendered=0;
        String[] entries=getContext().getAssets().list("fusion/scenes");
        for(String entry:entries) {
            FxSceneAsset asset;
            try(InputStream in=getContext().getAssets().open("fusion/scenes/"+entry)){asset=FxSceneAsset.load(in,resources,32*1024*1024);}
            FxSceneInstance instance=new FxSceneInstance(asset.scene);Map<Integer,FxTexture> images=asset.textures;
            // Layouts contain dynamic control/scene placeholders, not a standalone weather composition.
            if(entry.startsWith("Weather_"))continue;
            if(images.isEmpty())continue;
            boolean visible=false;float[] view=FxTransform.identity();view[14]=-1000;
            for(double frame:new double[]{0,30,60,120,300}) {
                instance.evaluate(frame);renderer.draw(instance,images,FxCamera.orthographic(0,480,480,0,1,5000),view,100,100,FxGlRenderer.Depth.LAYERS);
                ByteBuffer pixels=pixels();
                for(int i=3;i<pixels.capacity();i+=4)if((pixels.get(i)&255)>10){visible=true;break;}
            }
            check(visible,"Blank original condition scene "+entry);rendered++;
        }
        check(rendered==80,"Expected 80 condition scenes, got "+rendered);
        Bundle status=new Bundle();status.putString("result","PASS 80 original condition scenes rendered with explicit adapted camera/blending");sendStatus(0,status);
    }
    private void host() throws Exception {
        Activity activity=startActivitySync(new Intent(getTargetContext(),ProbeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        FxWidgetHost[] host=new FxWidgetHost[1];int[] events=new int[3];CountDownLatch finished=new CountDownLatch(1), stopped=new CountDownLatch(1);
        try {
            runOnMainSync(()->{
                host[0]=new FxWidgetHost(activity);activity.setContentView(host[0]);
                FxScene scene=scene(quad("surface",0,1,0));
                host[0].setContent(scene,Map.of(1,pixel(0,0,255,255)),FxCamera.orthographic(0,100,100,0,1,100),view(),
                    code->FxGlRenderer.Blend.SOURCE_OVER,FxGlRenderer.Depth.LAYERS);
                host[0].setWidget(new FxWidgetHost.Widget(){public void onStart(FxWidgetHost h){events[0]++;}public void onStop(){events[1]++;if(events[1]>=2)stopped.countDown();}});
                FxControls.Button button=new FxControls.Button(activity);button.setText("Test");button.setOnClickListener(v->events[2]++);
                host[0].bind(scene.nodes.get(0),button,new RectF(10,10,50,30));button.performClick();
                host[0].setPlaybackListener(finished::countDown);host[0].play(new FxPlayback(0,3,30,false));
            });
            check(finished.await(8,TimeUnit.SECONDS),"Host playback did not finish");
            waitForIdleSync();
            runOnMainSync(()->{
                check(events[0]==1&&events[2]==1,"Host start/native click");
                host[0].setVisibility(View.GONE);check(events[1]==1,"Host did not stop");
                host[0].setVisibility(View.VISIBLE);check(events[0]==2,"Host did not restart");
                check(host[0].getChildCount()==2,"GPU/control layers missing");
            });
        } finally {runOnMainSync(activity::finish);waitForIdleSync();}
        check(stopped.await(8,TimeUnit.SECONDS),"Detach did not stop widget");
    }
}
