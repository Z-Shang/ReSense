package dev.sense.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.text.TextUtils;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.Button;

/** The parent bends the surface; foreground glyphs and icons use an untransformed Canvas. */
public final class DockButton extends Button {
    private Drawable surface;

    public DockButton(Context context) {
        super(context);
    }

    public void setSurfaceDrawable(Drawable drawable) {
        surface = drawable;
        surface.setCallback(this);
        surface.setState(getDrawableState());
        setBackground(null);
        invalidate();
    }

    public Drawable getSurfaceDrawable() {
        return surface;
    }

    public void drawContent(Canvas canvas) {
        TextPaint paint = getPaint();
        paint.setColor(getCurrentTextColor());
        paint.setTextAlign(Paint.Align.LEFT);
        Drawable[] icons = getCompoundDrawablesRelative();
        int gap = getCompoundDrawablePadding();
        int startWidth = icons[0] == null ? 0 : icons[0].getBounds().width();
        int endWidth = icons[2] == null ? 0 : icons[2].getBounds().width();
        int topHeight = icons[1] == null ? 0 : icons[1].getBounds().height() + gap;
        int bottomHeight = icons[3] == null ? 0 : icons[3].getBounds().height() + gap;
        int gaps = (icons[0] == null ? 0 : gap) + (icons[2] == null ? 0 : gap);
        float available = Math.max(0, getWidth() - getPaddingLeft() - getPaddingRight()
                - startWidth - endWidth - gaps);
        String label = TextUtils.ellipsize(getText(), paint, available, TextUtils.TruncateAt.END).toString();
        float textWidth = paint.measureText(label);
        float x = (getWidth() - textWidth - startWidth - endWidth - gaps) / 2f;
        float centerY = (getHeight() + topHeight - bottomHeight) / 2f;
        if (icons[0] != null) {
            drawIcon(canvas, icons[0], x, centerY - icons[0].getBounds().height() / 2f);
            x += startWidth + gap;
        }
        canvas.drawText(label, x, centerY - (paint.ascent() + paint.descent()) / 2f, paint);
        if (icons[2] != null) drawIcon(canvas, icons[2], x + textWidth + gap,
                centerY - icons[2].getBounds().height() / 2f);
        float textHeight = paint.descent() - paint.ascent();
        if (icons[1] != null) drawIcon(canvas, icons[1],
                (getWidth() - icons[1].getBounds().width()) / 2f,
                centerY - textHeight / 2f - topHeight);
        if (icons[3] != null) drawIcon(canvas, icons[3],
                (getWidth() - icons[3].getBounds().width()) / 2f,
                centerY + textHeight / 2f + gap);
    }

    private void drawIcon(Canvas canvas, Drawable icon, float x, float y) {
        int saved = canvas.save();
        canvas.translate(x, y);
        icon.draw(canvas);
        canvas.restoreToCount(saved);
    }

    @Override protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (surface != null) surface.setState(getDrawableState());
    }

    @Override public void drawableHotspotChanged(float x, float y) {
        super.drawableHotspotChanged(x, y);
        if (surface != null) surface.setHotspot(x, y);
    }

    @Override protected boolean verifyDrawable(Drawable drawable) {
        return drawable == surface || super.verifyDrawable(drawable);
    }

    @Override public void invalidateDrawable(Drawable drawable) {
        super.invalidateDrawable(drawable);
        if (getParent() instanceof View parent) parent.invalidate();
    }
}
