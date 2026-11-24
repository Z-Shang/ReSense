package dev.sense.framework.fusion;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded reader for the observed Mode10 v2 record format. Unknown tags remain intact. */
public final class Mode10 {
    public static final int MAX_BYTES = 64 * 1024 * 1024;
    private Mode10() {}

    public static int hash(String name) {
        int hash = 5381;
        for (int i = 0; i < name.length(); i++) hash = (hash * 33 + name.charAt(i)) & 0x7fffffff;
        return hash;
    }

    public static Record read(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(MAX_BYTES + 1);
        return read(bytes);
    }

    public static Record read(byte[] input) {
        if (input.length < 16 || input.length > MAX_BYTES) throw invalid("Input size");
        // Own the bytes: callers cannot change an already validated document.
        ByteBuffer data = ByteBuffer.wrap(input.clone()).order(ByteOrder.LITTLE_ENDIAN);
        if (data.getInt(0) != 0x6d313021 || data.getInt(4) != 2) throw invalid("Expected Mode10 v2");
        List<Record> roots = records(data, 8, data.limit(), 0, new int[1]);
        if (roots.size() != 1 || roots.get(0).tag != hash("Root") || roots.get(0).kind != 1)
            throw invalid("Expected one Root container");
        return roots.get(0);
    }

    private static List<Record> records(ByteBuffer data, int start, int end, int depth, int[] count) {
        if (depth > 128) throw invalid("Excessive nesting");
        List<Record> result = new ArrayList<>();
        while (start < end) {
            if (end - start < 8) throw invalid("Truncated header at " + start);
            int word = data.getInt(start), length = word >>> 4, kind = word & 15;
            long stop = (long) start + 8 + length, aligned = (long) start + 8 + ((length + 3L) & ~3L);
            if ((kind != 1 && kind != 2) || aligned > end || ++count[0] > 200_000)
                throw invalid("Invalid record at " + start);
            List<Record> children = kind == 1 ? records(data, start + 8, (int) stop, depth + 1, count) : List.of();
            ByteBuffer payload = data.duplicate();
            payload.position(start + 8).limit((int) stop);
            result.add(new Record(start, data.getInt(start + 4), kind, payload.slice(), children));
            start = (int) aligned;
        }
        return List.copyOf(result);
    }

    static IllegalArgumentException invalid(String detail) { return new IllegalArgumentException("Mode10: " + detail); }

    public static final class Record {
        public final int offset, tag, kind;
        public final List<Record> children;
        private final ByteBuffer payload;
        private Record(int offset, int tag, int kind, ByteBuffer payload, List<Record> children) {
            this.offset = offset; this.tag = tag; this.kind = kind;
            this.payload = payload.asReadOnlyBuffer(); this.children = children;
        }
        public ByteBuffer bytes() { return payload.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN); }
        public int size() { return payload.capacity(); }
        public Record child(String name) {
            int wanted = hash(name);
            for (Record child : children) if (child.tag == wanted) return child;
            return null;
        }
        public int integer() {
            if (kind != 2 || size() != 4) throw invalid("Expected scalar at " + offset);
            return bytes().getInt();
        }
        public float scalar() {
            float value = Float.intBitsToFloat(integer());
            if (!Float.isFinite(value)) throw invalid("Non-finite scalar at " + offset);
            return value;
        }
        public String text() {
            if (kind != 2 || size() % 2 != 0) throw invalid("Expected UTF-16 at " + offset);
            try {
                String text = StandardCharsets.UTF_16LE.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .decode(bytes()).toString();
                int end = text.length();
                while (end > 0 && text.charAt(end - 1) == 0) end--;
                return text.substring(0, end);
            } catch (CharacterCodingException error) { throw invalid("Invalid UTF-16 at " + offset); }
        }
        public Map<Integer, Record> properties() {
            Map<Integer, Record> result = new LinkedHashMap<>();
            for (Record child : children) {
                if (child.tag != hash("Property") || child.kind != 1) continue;
                if (child.children.size() != 2 || child.children.get(0).tag != hash("Name"))
                    throw invalid("Invalid property at " + child.offset);
                int key = child.children.get(0).integer();
                if (result.put(key, child.children.get(1)) != null) throw invalid("Duplicate property at " + child.offset);
            }
            return Collections.unmodifiableMap(result);
        }
    }
}
