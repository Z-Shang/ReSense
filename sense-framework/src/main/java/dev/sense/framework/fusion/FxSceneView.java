package dev.sense.framework.fusion;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.View;

/** Lifecycle-aware frame host. Loading/decoding belongs off the UI thread; renderers own drawing. */
public final class FxSceneView extends View implements Choreographer.FrameCallback {
    public interface Renderer { void draw(Canvas canvas, FxScene scene, double frame, int width, int height); }
    private FxScene scene;
    private FxPlayback playback;
    private Renderer renderer;
    private boolean posted;

    public FxSceneView(Context context) { super(context); }
    public FxSceneView(Context context, AttributeSet attributes) { super(context, attributes); }

    public void setScene(FxScene scene, Renderer renderer, FxPlayback playback) {
        checkThread(); cancel(); this.scene = scene; this.renderer = renderer; this.playback = playback;
        if (playback != null) playback.rebase();
        invalidate(); schedule();
    }
    public void play() { checkThread(); if (playback != null) { playback.play(); invalidate(); schedule(); } }
    public void pause() { checkThread(); cancel(); if (playback != null) playback.pause(); }
    public void seek(double frame) { checkThread(); if (playback != null) { playback.seek(frame); invalidate(); schedule(); } }
    private static void checkThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("Fusion view requires main thread");
    }
    private boolean visible() { return isAttachedToWindow() && getWindowVisibility() == VISIBLE && isShown(); }
    private void schedule() {
        if (!posted && scene != null && renderer != null && playback != null && playback.isRunning() && visible()) {
            posted = true; Choreographer.getInstance().postFrameCallback(this);
        }
    }
    private void cancel() {
        if (posted) { Choreographer.getInstance().removeFrameCallback(this); posted = false; }
        if (playback != null) playback.rebase();
    }
    @Override public void doFrame(long frameTimeNanos) {
        posted = false;
        if (!visible()) { if (playback != null) playback.rebase(); return; }
        if (playback != null) playback.advance(frameTimeNanos);
        invalidate(); schedule();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (scene != null && renderer != null) {
            int save = canvas.save();
            try { renderer.draw(canvas, scene, playback == null ? 0 : playback.frame(), getWidth(), getHeight()); }
            finally { canvas.restoreToCount(save); }
        }
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); schedule(); }
    @Override protected void onDetachedFromWindow() { cancel(); super.onDetachedFromWindow(); }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility); if (visibility == VISIBLE) schedule(); else cancel();
    }
    @Override protected void onVisibilityChanged(View changed, int visibility) {
        super.onVisibilityChanged(changed, visibility); if (isShown()) schedule(); else cancel();
    }
}
