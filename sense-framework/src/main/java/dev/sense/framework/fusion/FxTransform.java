package dev.sense.framework.fusion;

/** Column-major 4x4 math, shared by render backends. Quaternions use x/y/z/w order. */
public final class FxTransform {
    private FxTransform() {}
    public static float[] identity() {
        return new float[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
    }
    public static float[] multiply(float[] left, float[] right) {
        float[] result = new float[16];
        multiplyInto(left, right, result);
        return result;
    }
    public static void multiplyInto(float[] left, float[] right, float[] result) {
        matrix(left); matrix(right);
        if (result.length != 16 || result == left || result == right) throw new IllegalArgumentException("Distinct matrix output required");
        for (int col = 0; col < 4; col++) for (int row = 0; row < 4; row++) {
            double value = 0;
            for (int k = 0; k < 4; k++) value += (double) left[k * 4 + row] * right[col * 4 + k];
            result[col * 4 + row] = finite(value);
        }
    }
    /** Translation * normalized quaternion rotation * scale. Does not infer stock pivot semantics. */
    public static float[] compose(float[] position, float[] quaternion, float[] scale) {
        float[] result = new float[16];
        composeInto(position, quaternion, scale, result);
        return result;
    }
    public static void composeInto(float[] position, float[] quaternion, float[] scale, float[] result) {
        vector(position, 3); vector(quaternion, 4); vector(scale, 3);
        if (result.length != 16) throw new IllegalArgumentException("Matrix output");
        double length = 0;
        for (float v : quaternion) length += (double) v * v;
        if (length == 0) throw new IllegalArgumentException("Zero quaternion");
        length = Math.sqrt(length);
        double x = quaternion[0] / length, y = quaternion[1] / length,
                z = quaternion[2] / length, w = quaternion[3] / length;
        result[3] = result[7] = result[11] = 0; result[15] = 1;
        result[0] = finite((1 - 2 * (y*y + z*z)) * scale[0]);
        result[1] = finite(2 * (x*y + z*w) * scale[0]);
        result[2] = finite(2 * (x*z - y*w) * scale[0]);
        result[4] = finite(2 * (x*y - z*w) * scale[1]);
        result[5] = finite((1 - 2 * (x*x + z*z)) * scale[1]);
        result[6] = finite(2 * (y*z + x*w) * scale[1]);
        result[8] = finite(2 * (x*z + y*w) * scale[2]);
        result[9] = finite(2 * (y*z - x*w) * scale[2]);
        result[10] = finite((1 - 2 * (x*x + y*y)) * scale[2]);
        System.arraycopy(position, 0, result, 12, 3);
    }
    public static float[] point(float[] matrix, float x, float y, float z) {
        matrix(matrix); vector(new float[]{x, y, z}, 3);
        return new float[]{finite((double) matrix[0]*x + (double) matrix[4]*y + (double) matrix[8]*z + matrix[12]),
                finite((double) matrix[1]*x + (double) matrix[5]*y + (double) matrix[9]*z + matrix[13]),
                finite((double) matrix[2]*x + (double) matrix[6]*y + (double) matrix[10]*z + matrix[14]),
                finite((double) matrix[3]*x + (double) matrix[7]*y + (double) matrix[11]*z + matrix[15])};
    }
    static void matrix(float[] matrix) { vector(matrix, 16); }
    static void vector(float[] values, int length) {
        if (values == null || values.length != length) throw new IllegalArgumentException("Vector size");
        for (float value : values) if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite vector");
    }
    static float finite(double value) {
        float result = (float) value;
        if (!Float.isFinite(result)) throw new IllegalArgumentException("Transform overflow");
        return result;
    }
}
