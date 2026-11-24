package dev.sense.framework.fusion;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Map;

/** Immutable, straight-alpha RGBA pixels. Decoding is CPU-only and belongs on a worker. */
public final class FxTexture {
    public final int width, height;
    private final byte[] rgba;
    private final boolean opaque;
    public FxTexture(int width, int height, byte[] rgba) {
        if (width <= 0 || height <= 0 || (long) width * height * 4 > Mode10.MAX_BYTES || rgba.length != (long) width * height * 4)
            throw new IllegalArgumentException("Texture dimensions/bytes");
        this.width = width; this.height = height; this.rgba = rgba.clone();
        boolean opaque = true;
        for (int i = 3; i < rgba.length; i += 4) if ((rgba[i] & 255) != 255) { opaque = false; break; }
        this.opaque = opaque;
    }
    public int byteCount() { return rgba.length; }
    public boolean isOpaque() {
        return opaque;
    }
    public ByteBuffer pixels() { return ByteBuffer.wrap(rgba).asReadOnlyBuffer(); }

    public static FxTexture decode(Mode10.Record record) {
        Map<Integer, Mode10.Record> p = record.properties();
        int width = required(p, "Width").integer(), height = required(p, "Height").integer();
        int format = required(p, "Format").integer();
        if (format != 6 && format != 14) throw new UnsupportedOperationException("Mode10 texture format " + format);
        if (width <= 0 || height <= 0 || width > 4096 || height > 4096) throw Mode10.invalid("Texture dimensions");
        Mode10.Record levels = required(p, "Levels");
        if (levels.kind != 2 || levels.size() != 16) throw new UnsupportedOperationException("Mode10 mip layout");
        ByteBuffer level = levels.bytes();
        int pw = level.getInt(), ph = level.getInt(), offset = level.getInt(), plane = level.getInt();
        if (pw < width || ph < height || pw > 4096 || ph > 4096 || offset != 0)
            throw Mode10.invalid("Texture padded extents");
        Mode10.Record size = record.child("DataDecompressedSize");
        if (size == null) throw Mode10.invalid("Missing decompressed size");
        long expected = format == 6 ? (long) pw * ph * 4 : (long) ((pw + 3) / 4) * ((ph + 3) / 4) * 16;
        if (size.integer() != expected || (format == 14 && plane != expected / 2)) throw Mode10.invalid("Texture plane sizes");
        Mode10.Record pixels = required(p, "Pixels");
        if (pixels.kind != 2) throw Mode10.invalid("Texture pixels container");
        byte[] raw = fastLz(pixels.bytes(), size.integer());
        byte[] result = new byte[width * height * 4];
        if (format == 6) {
            for (int y = 0; y < height; y++) System.arraycopy(raw, y * pw * 4, result, y * width * 4, width * 4);
        } else {
            byte[] color = etc1(Arrays.copyOfRange(raw, 0, plane), pw, ph);
            byte[] alpha = etc1(Arrays.copyOfRange(raw, plane, raw.length), pw, ph);
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                int src = (y * pw + x) * 3, dst = (y * width + x) * 4;
                System.arraycopy(color, src, result, dst, 3); result[dst + 3] = alpha[src];
            }
        }
        return new FxTexture(width, height, result);
    }
    private static Mode10.Record required(Map<Integer, Mode10.Record> p, String key) {
        Mode10.Record result = p.get(Mode10.hash(key));
        if (result == null) throw Mode10.invalid("Missing texture " + key);
        return result;
    }
    public static byte[] fastLz(ByteBuffer source, int expected) {
        ByteBuffer data = source.duplicate();
        if (!data.hasRemaining() || expected <= 0 || expected > Mode10.MAX_BYTES) throw Mode10.invalid("FastLZ size");
        int first = u8(data), level = (first >> 5) + 1, control = first & 31, position = 0;
        if (level != 1 && level != 2) throw new UnsupportedOperationException("FastLZ level " + level);
        byte[] out = new byte[expected];
        while (true) {
            if (control < 32) {
                int length = control + 1;
                if (data.remaining() < length || position > expected - length) throw Mode10.invalid("FastLZ literal");
                data.get(out, position, length); position += length;
            } else {
                int length = (control >> 5) - 1, high = (control & 31) << 8;
                if (length == 6) {
                    int extra;
                    do { extra = u8(data); length += extra; } while (level == 2 && extra == 255);
                }
                int low = u8(data), distance = high + low + 1;
                if (level == 2 && low == 255 && high == (31 << 8)) distance = (u8(data) << 8) + u8(data) + 8192;
                length += 3;
                if (distance > position || position > expected - length) throw Mode10.invalid("FastLZ backreference");
                for (int i = 0; i < length; i++) { out[position] = out[position - distance]; position++; }
            }
            if (!data.hasRemaining()) break;
            control = u8(data);
        }
        if (position != expected) throw Mode10.invalid("FastLZ output size");
        return out;
    }
    private static int u8(ByteBuffer data) {
        if (!data.hasRemaining()) throw Mode10.invalid("Truncated FastLZ");
        return data.get() & 255;
    }
    private static final int[][] MODIFIERS = {{2,8,-2,-8},{5,17,-5,-17},{9,29,-9,-29},{13,42,-13,-42},
            {18,60,-18,-60},{24,80,-24,-80},{33,106,-33,-106},{47,183,-47,-183}};
    public static byte[] etc1(byte[] blocks, int width, int height) {
        if (width <= 0 || height <= 0 || (long) width * height * 3 > Mode10.MAX_BYTES ||
                blocks.length != ((width + 3L) / 4) * ((height + 3L) / 4) * 8) throw Mode10.invalid("ETC1 size");
        ByteBuffer data = ByteBuffer.wrap(blocks).order(ByteOrder.BIG_ENDIAN);
        byte[] out = new byte[width * height * 3];
        for (int by = 0; by < height; by += 4) for (int bx = 0; bx < width; bx += 4) {
            int hi = data.getInt(), lo = data.getInt();
            int[][] colors = new int[2][3];
            if ((hi & 2) != 0) {
                for (int c = 0; c < 3; c++) {
                    int a = (hi >>> (27 - c * 8)) & 31, d = (hi >>> (24 - c * 8)) & 7;
                    int b = a + (d < 4 ? d : d - 8);
                    if (b < 0 || b > 31) throw Mode10.invalid("ETC1 differential");
                    colors[0][c] = (a << 3) | (a >> 2); colors[1][c] = (b << 3) | (b >> 2);
                }
            } else for (int c = 0; c < 3; c++) {
                colors[0][c] = ((hi >>> (28 - c * 8)) & 15) * 17;
                colors[1][c] = ((hi >>> (24 - c * 8)) & 15) * 17;
            }
            for (int y = 0; y < Math.min(4, height - by); y++) for (int x = 0; x < Math.min(4, width - bx); x++) {
                int sub = (hi & 1) != 0 ? (y >= 2 ? 1 : 0) : (x >= 2 ? 1 : 0), bit = x * 4 + y;
                int selector = ((lo >>> bit) & 1) | (((lo >>> (bit + 16)) & 1) << 1);
                int table = sub == 0 ? (hi >>> 5) & 7 : (hi >>> 2) & 7;
                int dst = ((by + y) * width + bx + x) * 3;
                for (int c = 0; c < 3; c++) out[dst + c] = (byte) Math.max(0, Math.min(255, colors[sub][c] + MODIFIERS[table][selector]));
            }
        }
        return out;
    }
}
