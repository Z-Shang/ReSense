package dev.sense.launcher;

import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;

/** Curved rail divided into one equal segment per page. */
final class DockPageTrack {
    private final float density;
    private final Path rail = new Path();
    private final Path thumb = new Path();
    private final PathMeasure measure = new PathMeasure();
    private final Paint recess = stroke(0x66121b19);
    private final Paint highlight = stroke(0xfff4f8ed);
    private static final int SAMPLE_SCALE = 4;
    private final RectF imageBounds = new RectF();
    private final RectF curveBounds = new RectF();
    private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private Bitmap smoothTrack;
    private Canvas smoothCanvas;
    private boolean dirty = true;
    private float page;
    private int count = 1;

    DockPageTrack(float density) {
        this.density = density;
        recess.setStrokeWidth(2.6f * density);
        highlight.setStrokeWidth(1.4f * density);
    }

    private static Paint stroke(int color) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        return paint;
    }

    void layout(float left, float leftY, float right, float rightY, float middleY) {
        float offset = -3.2f * density;
        rail.reset();
        rail.moveTo(left, leftY + offset);
        rail.quadTo((left + right) / 2, 2 * middleY - (leftY + rightY) / 2 + offset,
                right, rightY + offset);
        measure.setPath(rail, false);
        rail.computeBounds(curveBounds, true);
        float padding = Math.max(recess.getStrokeWidth(), highlight.getStrokeWidth()) / 2 + 1;
        imageBounds.set((float) Math.floor(curveBounds.left - padding),
                (float) Math.floor(curveBounds.top - padding),
                (float) Math.ceil(curveBounds.right + padding),
                (float) Math.ceil(curveBounds.bottom + padding));
        int width = Math.max(1, Math.round(imageBounds.width()) * SAMPLE_SCALE);
        int height = Math.max(1, Math.round(imageBounds.height()) * SAMPLE_SCALE);
        if (smoothTrack == null || smoothTrack.getWidth() != width || smoothTrack.getHeight() != height) {
            if (smoothTrack != null) smoothTrack.recycle();
            smoothTrack = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            smoothCanvas = new Canvas(smoothTrack);
        }
        updateThumb();
    }

    boolean setPage(float page, int count) {
        if (count < 1 || page < 0 || page >= count) throw new IllegalArgumentException("Invalid page position");
        if(this.page==page && this.count==count)return false;
        this.page = page;
        this.count = count;
        updateThumb();
        return true;
    }

    private void updateThumb() {
        dirty = true;
        float length = measure.getLength();
        float thumbLength = length / count;
        float position = count == 1 ? .5f : page / (float) (count - 1);
        float start = position * (length - thumbLength);
        thumb.reset();
        if (length > 0) measure.getSegment(start, start + thumbLength, thumb, true);
    }

    void draw(Canvas canvas) {
        if (smoothTrack == null) return;
        if (dirty) {
            smoothTrack.eraseColor(Color.TRANSPARENT);
            int saved = smoothCanvas.save();
            smoothCanvas.scale(SAMPLE_SCALE, SAMPLE_SCALE);
            smoothCanvas.translate(-imageBounds.left, -imageBounds.top);
            smoothCanvas.drawPath(rail, recess);
            smoothCanvas.drawPath(thumb, highlight);
            smoothCanvas.restoreToCount(saved);
            dirty = false;
        }
        // Supersample the complete strokes, including rounded ends, then filter to screen pixels.
        canvas.drawBitmap(smoothTrack, null, imageBounds, imagePaint);
    }
}
