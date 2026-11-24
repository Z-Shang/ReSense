package dev.sense.framework.fusion;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import dev.sense.framework.SenseButton;

/** Accessible native equivalents of the Fusion controls; no dependency on HTC runtime classes. */
public final class FxControls {
    private FxControls() {}
    public static final class TextLabel extends TextView {
        public TextLabel(Context context) {
            super(context); setTextColor(Color.WHITE); setGravity(Gravity.CENTER); setIncludeFontPadding(false);
        }
    }
    public static final class DynamicImage extends ImageView {
        public DynamicImage(Context context) { super(context); setScaleType(ScaleType.FIT_CENTER); }
    }
    public static final class Button extends SenseButton {
        public Button(Context context) { super(context); }
    }
    public static final class SceneContainer extends FrameLayout {
        public SceneContainer(Context context) { super(context); setClipChildren(true); }
    }
    public static final class Hitbox extends View {
        public interface SwipeListener { void onSwipe(float velocityX, float velocityY); }
        private SwipeListener swipeListener;
        private final GestureDetector detector;
        public Hitbox(Context context) {
            super(context); setClickable(true); setFocusable(true);
            detector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onDown(MotionEvent event) { return true; }
                @Override public boolean onSingleTapUp(MotionEvent event) { return performClick(); }
                @Override public boolean onFling(MotionEvent first, MotionEvent last, float vx, float vy) {
                    if (swipeListener == null) return false;
                    swipeListener.onSwipe(vx, vy); return true;
                }
            });
        }
        public void setSwipeListener(SwipeListener listener) { swipeListener = listener; }
        @Override public boolean onTouchEvent(MotionEvent event) { return detector.onTouchEvent(event); }
        @Override public boolean performClick() { super.performClick(); return true; }
    }
}
