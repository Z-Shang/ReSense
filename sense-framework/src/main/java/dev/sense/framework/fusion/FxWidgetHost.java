package dev.sense.framework.fusion;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.opengl.GLSurfaceView;
import android.os.Looper;
import android.view.Choreographer;
import android.view.View;
import android.widget.FrameLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** Explicit widget host: GPU scene below native controls, lifecycle and playback on the UI thread. */
public final class FxWidgetHost extends FrameLayout implements Choreographer.FrameCallback {
    public interface Widget {
        /** Subscribe to shared data here. Called once per visible host interval. */
        void onStart(FxWidgetHost host);
        /** Release observers/callbacks here; never retain an Activity after this call. */
        void onStop();
    }
    public interface PlaybackListener { void onFinished(); }
    private final GLSurfaceView surface;
    private final FrameLayout controls;
    private final Driver driver = new Driver();
    private final List<Binding> bindings = new ArrayList<>();
    private FxSceneInstance controlScene;
    private Content content;
    private FxPlayback playback;
    private Widget widget;
    private PlaybackListener listener;
    private boolean posted, resumed, started;
    private double frame;

    public FxWidgetHost(Context context) {
        super(context); setClipChildren(true);
        surface = new GLSurfaceView(context); surface.setEGLContextClientVersion(2);
        surface.setEGLConfigChooser(8,8,8,8,16,0); surface.getHolder().setFormat(PixelFormat.TRANSLUCENT);
        surface.setZOrderMediaOverlay(true); surface.setRenderer(driver); surface.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        addView(surface, new LayoutParams(-1,-1));
        controls = new FrameLayout(context); controls.setClipChildren(true); addView(controls, new LayoutParams(-1,-1));
    }
    /** Supply resources decoded off-thread. No parsing, IO or texture decompression happens while drawing. */
    public void setContent(FxScene scene, Map<Integer, FxTexture> textures, FxCamera camera, float[] view,
                           FxGlRenderer.BlendResolver blends, FxGlRenderer.Depth depth) {
        checkThread(); FxTransform.matrix(view);
        if (scene == null || camera == null || blends == null || depth == null) throw new IllegalArgumentException("Widget content");
        FxSceneInstance instance = new FxSceneInstance(scene);
        content = new Content(scene, Map.copyOf(textures), camera, view.clone(), blends, depth);
        controlScene = instance; bindings.clear(); controls.removeAllViews(); driver.content = content;
        render();
    }
    public void setWidget(Widget widget) {
        checkThread(); if (started && this.widget != null) this.widget.onStop();
        this.widget = widget; started = false; updateLifecycle();
    }
    public void setPlaybackListener(PlaybackListener listener) { checkThread(); this.listener = listener; }
    public void play(FxPlayback playback) {
        checkThread(); cancel(); this.playback = playback;
        if (playback != null) { playback.play(); frame = playback.frame(); }
        render(); schedule();
    }
    public void pause() { checkThread(); cancel(); if (playback != null) playback.pause(); }
    public void seek(double frame) {
        checkThread(); if (!Double.isFinite(frame)) throw new IllegalArgumentException("Frame");
        if (playback != null) { playback.seek(frame); this.frame = playback.frame(); } else this.frame = frame;
        render();
    }
    /** Bounds are in the node's local coordinates. Controls keep native text/input/accessibility semantics. */
    public void bind(FxScene.Node node, View view, RectF bounds) {
        checkThread();
        if (controlScene == null || view.getParent() != null || bounds.width() <= 0 || bounds.height() <= 0 ||
                !Float.isFinite(bounds.left) || !Float.isFinite(bounds.top) || !Float.isFinite(bounds.right) || !Float.isFinite(bounds.bottom))
            throw new IllegalArgumentException("Control binding");
        FxSceneInstance.State state = controlScene.state(node);
        Binding binding = new Binding(state, view, new RectF(bounds)); bindings.add(binding);
        controls.addView(view, new LayoutParams(Math.max(1,(int) Math.ceil(bounds.width())), Math.max(1,(int) Math.ceil(bounds.height()))));
        render();
    }
    private void render() {
        if (content == null) return;
        driver.frame = frame; surface.requestRender();
        if (getWidth() <= 0 || getHeight() <= 0) return;
        controlScene.evaluate(frame);
        for (Binding binding : bindings) binding.update(content, getWidth(), getHeight());
    }
    private boolean visible() { return isAttachedToWindow() && getWindowVisibility() == VISIBLE && isShown(); }
    private void updateLifecycle() {
        if (surface == null) return; // View superclass may invoke visibility callbacks during construction.
        boolean visible = visible();
        if (visible != resumed) {
            resumed = visible;
            if (visible) surface.onResume(); else { cancel(); surface.onPause(); }
        }
        if (visible && !started && widget != null) { started = true; widget.onStart(this); }
        else if (!visible && started) { started = false; if (widget != null) widget.onStop(); }
        if (visible) { render(); schedule(); }
    }
    private void schedule() {
        if (!posted && content != null && visible() && playback != null && playback.isRunning()) {
            posted = true; Choreographer.getInstance().postFrameCallback(this);
        }
    }
    private void cancel() {
        if (posted) Choreographer.getInstance().removeFrameCallback(this);
        posted = false; if (playback != null) playback.rebase();
    }
    @Override public void doFrame(long nanos) {
        posted = false;
        if (!visible()) { cancel(); return; }
        boolean wasRunning = playback != null && playback.isRunning();
        if (playback != null) frame = playback.advance(nanos);
        render();
        if (wasRunning && !playback.isRunning() && listener != null) listener.onFinished();
        schedule();
    }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) { super.onSizeChanged(w,h,oldw,oldh); render(); }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); updateLifecycle(); }
    @Override protected void onDetachedFromWindow() {
        cancel(); if (started && widget != null) widget.onStop(); started = false;
        if (resumed) { resumed = false; surface.onPause(); }
        super.onDetachedFromWindow();
    }
    @Override protected void onWindowVisibilityChanged(int visibility) { super.onWindowVisibilityChanged(visibility); updateLifecycle(); }
    @Override protected void onVisibilityChanged(View changed, int visibility) { super.onVisibilityChanged(changed, visibility); updateLifecycle(); }
    private static void checkThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("Widget host requires main thread");
    }
    private static final class Content {
        final FxScene scene; final Map<Integer, FxTexture> textures; final FxCamera camera;
        final float[] view; final FxGlRenderer.BlendResolver blends; final FxGlRenderer.Depth depth;
        Content(FxScene scene, Map<Integer,FxTexture> textures, FxCamera camera, float[] view, FxGlRenderer.BlendResolver blends, FxGlRenderer.Depth depth) {
            this.scene=scene; this.textures=textures; this.camera=camera; this.view=view; this.blends=blends; this.depth=depth;
        }
    }
    private static final class Driver implements GLSurfaceView.Renderer {
        volatile Content content; volatile double frame;
        private Content loaded; private FxSceneInstance instance; private FxGlRenderer renderer;
        private int width, height;
        @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            // Old GL names belong to the lost context. CPU resources remain valid.
            renderer = null; loaded = null;
        }
        @Override public void onSurfaceChanged(GL10 gl, int width, int height) { this.width=width; this.height=height; }
        @Override public void onDrawFrame(GL10 gl) {
            Content next = content; if (next == null || width <= 0 || height <= 0) return;
            if (loaded != next) {
                if (renderer != null) renderer.close();
                renderer = new FxGlRenderer(32*1024*1024, next.blends); instance = new FxSceneInstance(next.scene); loaded = next;
            }
            instance.evaluate(frame); renderer.draw(instance,next.textures,next.camera,next.view,width,height,next.depth);
        }
    }
    private static final class Binding {
        final FxSceneInstance.State state; final View view; final RectF bounds;
        final float[] destination = new float[8], point = new float[3], source = new float[8];
        final Matrix matrix = new Matrix();
        Binding(FxSceneInstance.State state, View view, RectF bounds) {
            this.state=state; this.view=view; this.bounds=bounds;
            float w = Math.max(1,(float) Math.ceil(bounds.width())), h = Math.max(1,(float) Math.ceil(bounds.height()));
            source[2]=source[4]=w; source[5]=source[7]=h;
        }
        void update(Content content, int width, int height) {
            boolean shown = state.opacity() > 0;
            float[] modelView = FxTransform.multiply(content.view, state.world());
            for (int i = 0; i < 4 && shown; i++) {
                float x = i == 0 || i == 3 ? bounds.left : bounds.right, y = i < 2 ? bounds.top : bounds.bottom;
                shown = content.camera.project(modelView,x,y,0,width,height,point);
                destination[i*2]=point[0]; destination[i*2+1]=point[1];
            }
            if (shown) shown = matrix.setPolyToPoly(source,0,destination,0,4);
            view.setVisibility(shown ? VISIBLE : INVISIBLE); view.setAlpha(state.opacity());
            if (shown) view.setAnimationMatrix(matrix);
        }
    }
}
