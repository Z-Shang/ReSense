package dev.sense.framework.fusion;

import java.nio.ByteBuffer;

/** Observed static/dense/keyed float tracks. Encoded HTC interpolation codes are preserved. */
public final class FxTrack {
    public enum Sampling { STEP, LINEAR }
    public final int dimensions, sampleCount;
    private final double[] frames;
    private final int[] interpolationCodes;
    private final float[] values;

    public FxTrack(float... constant) {
        if (constant.length < 1 || constant.length > 16) throw new IllegalArgumentException("Track dimensions");
        FxTransform.vector(constant, constant.length);
        dimensions = constant.length; sampleCount = 1; frames = new double[]{0};
        interpolationCodes = new int[]{-1}; values = constant.clone();
    }

    public FxTrack(Mode10.Record record, int dimensions) {
        if (dimensions < 1 || dimensions > 16) throw new IllegalArgumentException("Track dimensions");
        this.dimensions = dimensions;
        if (record.kind == 2) {
            if (record.size() != dimensions * 4) throw Mode10.invalid("Static vector size");
            sampleCount = 1; frames = new double[]{0}; interpolationCodes = new int[]{-1};
            values = new float[dimensions]; readValues(record.bytes(), values, 0, dimensions);
            return;
        }
        Mode10.Record size = record.child("Size"), samples = record.child("Samples");
        if (size == null || samples == null || samples.kind != 2) throw Mode10.invalid("Unsupported vector encoding");
        sampleCount = size.integer() & 0x7fffffff;
        if (sampleCount < 1 || sampleCount > 200_000) throw Mode10.invalid("Track sample count");
        boolean keyed = samples.size() == (long) sampleCount * (dimensions + 2) * 4;
        if (!keyed && samples.size() != (long) sampleCount * dimensions * 4)
            throw Mode10.invalid("Unsupported vector sample stride");
        frames = new double[sampleCount]; interpolationCodes = new int[sampleCount]; values = new float[sampleCount * dimensions];
        Mode10.Record start = record.child("StartFrame");
        long first = start == null ? 0 : Integer.toUnsignedLong(start.integer());
        ByteBuffer data = samples.bytes();
        for (int i = 0; i < sampleCount; i++) {
            frames[i] = keyed ? Integer.toUnsignedLong(data.getInt()) : first + i;
            interpolationCodes[i] = keyed ? data.getInt() : -1;
            if (i > 0 && frames[i] <= frames[i - 1]) throw Mode10.invalid("Non-increasing track frames");
            readValues(data, values, i * dimensions, dimensions);
        }
    }

    private static void readValues(ByteBuffer data, float[] values, int offset, int count) {
        for (int i = 0; i < count; i++) {
            float value = data.getFloat();
            if (!Float.isFinite(value)) throw Mode10.invalid("Non-finite track value");
            values[offset + i] = value;
        }
    }
    public double frame(int sample) { return frames[sample]; }
    public int interpolationCode(int sample) { return interpolationCodes[sample]; }

    /** Shortest-arc normalized quaternion interpolation, explicitly selected by the binding layer. */
    public void sampleQuaternion(double frame, float[] output) {
        if (dimensions != 4 || output.length < 4 || !Double.isFinite(frame)) throw new IllegalArgumentException("Quaternion sample");
        int low = 0, high = sampleCount - 1;
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (frames[mid] <= frame) low = mid; else high = mid - 1;
        }
        int next = Math.min(low + 1, sampleCount - 1);
        double t = next == low ? 0 : Math.max(0, Math.min(1, (frame - frames[low]) / (frames[next] - frames[low])));
        double aa = 0, bb = 0, dot = 0;
        for (int c = 0; c < 4; c++) { aa += (double) values[low*4+c]*values[low*4+c]; bb += (double) values[next*4+c]*values[next*4+c]; }
        if (aa == 0 || bb == 0) throw Mode10.invalid("Zero quaternion track");
        aa = Math.sqrt(aa); bb = Math.sqrt(bb);
        for (int c = 0; c < 4; c++) dot += (values[low*4+c]/aa)*(values[next*4+c]/bb);
        double sign = dot < 0 ? -1 : 1; dot = Math.min(1, Math.abs(dot));
        double a = 1-t, b = t;
        if (dot < .9995) {
            double angle = Math.acos(dot), sin = Math.sin(angle);
            a = Math.sin((1-t)*angle)/sin; b = Math.sin(t*angle)/sin;
        }
        double length = 0;
        for (int c = 0; c < 4; c++) { output[c] = (float) (a*values[low*4+c]/aa + b*sign*values[next*4+c]/bb); length += (double) output[c]*output[c]; }
        length = Math.sqrt(length);
        for (int c = 0; c < 4; c++) output[c] /= (float) length;
    }

    /** Caller explicitly chooses adaptation; LINEAR does not claim to reproduce unknown HTC easing. */
    public void sample(double frame, Sampling sampling, float[] output) {
        if (!Double.isFinite(frame) || sampling == null || output.length < dimensions)
            throw new IllegalArgumentException("Invalid sampling arguments");
        int low = 0, high = sampleCount - 1;
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (frames[mid] <= frame) low = mid; else high = mid - 1;
        }
        int next = Math.min(low + 1, sampleCount - 1);
        double t = sampling == Sampling.STEP || next == low ? 0 :
                Math.max(0, Math.min(1, (frame - frames[low]) / (frames[next] - frames[low])));
        for (int i = 0; i < dimensions; i++) {
            double a = values[low * dimensions + i], b = values[next * dimensions + i];
            output[i] = (float) (a + (b - a) * t);
        }
    }
}
