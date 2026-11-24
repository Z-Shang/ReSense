package dev.sense.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;

/** Shared arc for the dock top and controls, with the background bottom anchored straight. */
public final class BentDockLayout extends LinearLayout {
    private static final int COLUMNS = 48;
    private static final int ROWS = 4;
    private static final int EDGE_SCALE = 2;
    private final Bitmap background;
    private final Rect backgroundStrip;
    private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint continuationPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] vertices = new float[(COLUMNS + 1) * (ROWS + 1) * 2];
    private final RectF flatBounds = new RectF();
    private final RectF navigation = new RectF();
    private final float[] backgroundVertices = new float[(COLUMNS + 1) * (ROWS + 1) * 2];
    private final float curveQuadratic;
    private final float curveLinear;
    private final int referenceWidth;
    private final DockPageTrack pageTrack;
    private final int trackHeadroom;
    private float bodyTransition,railTransition;
    private boolean surfacesDirty=true;
    private Bitmap smoothSurfaces;
    private Canvas smoothCanvas;
    private Bitmap backgroundBitmap;
    private Bitmap flatBitmap;
    private Canvas flatCanvas;
    private int flatHeight;
    private float buttonBottom;
    private int bottomInset;

    public BentDockLayout(Context context) {
        super(context);
        pageTrack = new DockPageTrack(getResources().getDisplayMetrics().density);
        trackHeadroom = dp(6);
        setOrientation(HORIZONTAL);
        setWillNotDraw(false);
        background = BitmapFactory.decodeResource(getResources(), R.drawable.sense_dock_background);
        Bitmap curve = BitmapFactory.decodeResource(getResources(), R.drawable.sense_dock_curve);
        referenceWidth = curve.getWidth();
        int left = firstCoveredRow(curve, 0);
        int center = firstCoveredRow(curve, referenceWidth / 2);
        int right = firstCoveredRow(curve, referenceWidth - 1);
        curveQuadratic = (left + right) / 2f - center;
        curveLinear = (right - left) / 2f;
        int x = background.getWidth() / 2;
        // Keep the clean shading, excluding the embedded separators and bottom border.
        backgroundStrip = new Rect(x, 0, x + 1, background.getHeight() - 2);
        continuationPaint.setColor(background.getPixel(x, 0));
        setSystemInsets(0, 0, 0);
    }

    private static int firstCoveredRow(Bitmap bitmap, int x) {
        for (int y = 0; y < bitmap.getHeight(); y++) {
            if (Color.alpha(bitmap.getPixel(x, y)) > 0) return y;
        }
        return bitmap.getHeight();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float bend(float x) {
        float t = 2f * x / Math.max(1, getWidth()) - 1f;
        return Math.max(0, curveQuadratic * t * t + curveLinear * t)
                * getWidth() / referenceWidth;
    }

    public void setSystemInsets(int left, int right, int bottom) {
        if (bottomInset != bottom) {
            bottomInset = bottom;
            requestLayout();
        }
        // Controls stay above system navigation; the shadow continues behind it.
        setPadding(left + dp(8), dp(8), right + dp(8), dp(2));
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        flatHeight = dp(60);
        buttonBottom = flatHeight - getPaddingBottom();
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).getLayoutParams().height = dp(48);
        }
        int height = resolveSize(flatHeight + bottomInset + trackHeadroom, heightSpec);
        super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        // Lay out the flat controls, then deform their tops toward the common straight baseline.
        super.onLayout(changed, left, top, right, top + flatHeight);
        surfacesDirty=true;
        int width = right - left;
        flatBounds.set(0, 0, width, flatHeight);
        if (width > 0 && (flatBitmap == null || flatBitmap.getWidth() != width
                || flatBitmap.getHeight() != flatHeight)) {
            flatBitmap = Bitmap.createBitmap(width, flatHeight, Bitmap.Config.ARGB_8888);
            flatCanvas = new Canvas(flatBitmap);
            // drawBitmapMesh ignores ANTI_ALIAS_FLAG. Downsampling a 2x surface supplies coverage.
            smoothSurfaces = Bitmap.createBitmap(width * EDGE_SCALE, flatHeight * EDGE_SCALE, Bitmap.Config.ARGB_8888);
            smoothCanvas = new Canvas(smoothSurfaces);
            smoothCanvas.scale(EDGE_SCALE, EDGE_SCALE);
            backgroundBitmap = Bitmap.createBitmap(width, flatHeight, Bitmap.Config.ARGB_8888);
            Canvas backgroundCanvas = new Canvas(backgroundBitmap);
            backgroundCanvas.translate(0, flatHeight);
            backgroundCanvas.scale(1, -1);
            backgroundCanvas.drawBitmap(background, backgroundStrip, flatBounds, imagePaint);
        }
        float bodyBottom = flatHeight;
        int index = 0;
        for (int row = 0; row <= ROWS; row++) {
            for (int column = 0; column <= COLUMNS; column++) {
                float x = width * column / (float) COLUMNS;
                float fraction = row / (float) ROWS;
                backgroundVertices[index] = x;
                vertices[index++] = x;
                // The surface bends at the top and converges to a horizontal lower edge.
                backgroundVertices[index] = fraction * bodyBottom + (1 - fraction) * bend(x);
                float sourceY = flatHeight * fraction;
                vertices[index++] = sourceY + bend(x) * (1 - sourceY / buttonBottom);
            }
        }
        navigation.set(0, bodyBottom, width, bodyBottom + bottomInset);
        int edgeColor=background.getPixel(background.getWidth()/2,0);
        continuationPaint.setShader(new android.graphics.LinearGradient(0,bodyBottom,0,bodyBottom+Math.max(1,bottomInset),
                edgeColor,0xd0080c0a,android.graphics.Shader.TileMode.CLAMP));
        float trackInset = dp(12);
        pageTrack.layout(trackInset, bend(trackInset), width - trackInset,
                bend(width - trackInset), bend(width / 2f));
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        // Only surfaces enter the mesh. Native labels and compound icons are drawn afterward.
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            Drawable face = child instanceof DockButton button ? button.getSurfaceDrawable() : child.getBackground();
            if (child.getVisibility() != VISIBLE || face == null) continue;
            int saved = canvas.save();
            canvas.translate(child.getLeft(), child.getTop());
            face.setBounds(0, 0, child.getWidth(), child.getHeight());
            face.draw(canvas);
            canvas.restoreToCount(saved);
        }
    }

    @Override public void draw(Canvas canvas) {
        if (flatBitmap == null) return;
        if(surfacesDirty){
            surfacesDirty=false;
            flatBitmap.eraseColor(Color.TRANSPARENT);super.draw(flatCanvas);
            smoothSurfaces.eraseColor(Color.TRANSPARENT);
            smoothCanvas.drawBitmapMesh(backgroundBitmap,COLUMNS,ROWS,backgroundVertices,0,null,0,imagePaint);
            smoothCanvas.drawBitmapMesh(flatBitmap,COLUMNS,ROWS,vertices,0,null,0,imagePaint);
        }
        // Reserve only drawing headroom for the external track; the dock body stays compact.
        int bodyLayer = canvas.save();
        canvas.translate(0, trackHeadroom);
        int movingBody=canvas.save();canvas.translate(0,bodyTransition*getHeight());
        canvas.drawRect(navigation, continuationPaint);
        canvas.drawBitmap(smoothSurfaces, null, flatBounds, imagePaint);
        for (int i = 0; i < getChildCount(); i++) {
            if (!(getChildAt(i) instanceof DockButton button) || button.getVisibility() != VISIBLE) continue;
            float centerY = (button.getTop() + button.getBottom()) / 2f;
            float offset = bend((button.getLeft() + button.getRight()) / 2f) * (1 - centerY / buttonBottom);
            int saved = canvas.save();
            canvas.translate(button.getLeft(), button.getTop() + offset);
            button.drawContent(canvas);
            canvas.restoreToCount(saved);
        }
        canvas.restoreToCount(movingBody);
        int movingRail=canvas.save();canvas.translate(0,railTransition*getHeight()*96f/91f);
        pageTrack.draw(canvas);canvas.restoreToCount(movingRail);
        canvas.restoreToCount(bodyLayer);
    }

    public void setTransitionProgress(float body,float rail){
        bodyTransition=body;railTransition=rail;postInvalidateOnAnimation();
    }
    @Override public void onDescendantInvalidated(View child,View target){surfacesDirty=true;super.onDescendantInvalidated(child,target);}
    @Override public android.view.ViewParent invalidateChildInParent(int[] location,Rect dirty){surfacesDirty=true;return super.invalidateChildInParent(location,dirty);}
    public void setPageIndicator(float page,int count){
        if(pageTrack.setPage(page,count))postInvalidateOnAnimation();
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if(bodyTransition>.001f || railTransition>.001f)return false;
        // Inverse of y' = y + bend(x) * (1 - y / buttonBottom).
        MotionEvent flatEvent = MotionEvent.obtain(event);
        float offset = bend(event.getX());
        flatEvent.setLocation(event.getX(), (event.getY() - trackHeadroom - offset) / (1 - offset / buttonBottom));
        try {
            return super.dispatchTouchEvent(flatEvent);
        } finally {
            flatEvent.recycle();
        }
    }

    @Override public void onViewAdded(View child) {
        super.onViewAdded(child);
        child.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                Rect bounds = new Rect();
                info.getBoundsInScreen(bounds);
                float low = Math.min(bend(host.getLeft()), bend(host.getRight()));
                if (host.getLeft() <= getWidth() / 2 && host.getRight() >= getWidth() / 2) low = 0;
                float high = Math.max(bend(host.getLeft()), bend(host.getRight()));
                bounds.top += trackHeadroom + (int) Math.floor(low * (1 - host.getTop() / buttonBottom));
                bounds.bottom += trackHeadroom + (int) Math.ceil(high * (1 - host.getBottom() / buttonBottom));
                info.setBoundsInScreen(bounds);
            }
        });
    }
}
