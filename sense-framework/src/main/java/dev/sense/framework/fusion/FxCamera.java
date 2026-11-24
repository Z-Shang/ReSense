package dev.sense.framework.fusion;

/** Explicit OpenGL-style camera projection; no guessed HTC camera defaults. */
public final class FxCamera {
    private final float[] projection;
    private FxCamera(float[] projection) { this.projection = projection; }
    public static FxCamera perspective(float verticalFovDegrees, float aspect, float near, float far) {
        FxTransform.vector(new float[]{verticalFovDegrees, aspect, near, far}, 4);
        if (verticalFovDegrees <= 0 || verticalFovDegrees >= 180 || aspect <= 0 || near <= 0 || far <= near)
            throw new IllegalArgumentException("Invalid perspective camera");
        double f = 1 / Math.tan(Math.toRadians(verticalFovDegrees) / 2);
        float[] m = new float[16];
        m[0] = FxTransform.finite(f / aspect); m[5] = FxTransform.finite(f);
        m[10] = FxTransform.finite(((double) far + near) / (near - (double) far));
        m[11] = -1; m[14] = FxTransform.finite(2d * far * near / (near - (double) far));
        return new FxCamera(m);
    }
    public static FxCamera orthographic(float left, float right, float bottom, float top, float near, float far) {
        FxTransform.vector(new float[]{left, right, bottom, top, near, far}, 6);
        if (left == right || bottom == top || far <= near) throw new IllegalArgumentException("Invalid orthographic camera");
        float[] m = FxTransform.identity();
        m[0] = FxTransform.finite(2d / (right - (double) left));
        m[5] = FxTransform.finite(2d / (top - (double) bottom));
        m[10] = FxTransform.finite(-2d / (far - (double) near));
        m[12] = FxTransform.finite(-((double) right + left) / (right - (double) left));
        m[13] = FxTransform.finite(-((double) top + bottom) / (top - (double) bottom));
        m[14] = FxTransform.finite(-((double) far + near) / (far - (double) near));
        return new FxCamera(m);
    }
    public float[] projection() { return projection.clone(); }
    /** Returns false outside the clip volume. A renderer must clip polygons that cross it. */
    public boolean project(float[] view, float x, float y, float z, int width, int height, float[] output) {
        if (width <= 0 || height <= 0 || output.length < 3) throw new IllegalArgumentException("Invalid viewport");
        float[] clip = FxTransform.point(FxTransform.multiply(projection, view), x, y, z);
        float w = clip[3];
        if (w <= 0 || clip[0] < -w || clip[0] > w || clip[1] < -w || clip[1] > w || clip[2] < -w || clip[2] > w)
            return false;
        output[0] = (clip[0] / w + 1) * .5f * width;
        output[1] = (1 - clip[1] / w) * .5f * height;
        output[2] = clip[2] / w;
        return true;
    }
}
