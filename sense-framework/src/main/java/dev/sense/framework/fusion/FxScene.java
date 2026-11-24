package dev.sense.framework.fusion;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Immutable structural scene. Structural parents are not assumed to be Fusion layer links. */
public final class FxScene {
    public final Mode10.Record document;
    public final List<Node> roots, nodes;

    public FxScene(Mode10.Record document) {
        this.document = document;
        List<Node> all = new ArrayList<>(), roots = new ArrayList<>();
        collect(document, null, roots, all);
        this.roots = List.copyOf(roots); this.nodes = List.copyOf(all);
    }

    public List<Node> find(String name) { return nodes.stream().filter(n -> name.equals(n.name)).toList(); }

    private static void collect(Mode10.Record record, Node parent, List<Node> siblings, List<Node> all) {
        if (record.kind == 1 && record.tag == Mode10.hash("Node")) {
            Node node = new Node(record, parent); siblings.add(node); all.add(node);
            List<Node> children = new ArrayList<>();
            for (Mode10.Record child : record.children) collect(child, node, children, all);
            node.children = List.copyOf(children);
        } else for (Mode10.Record child : record.children) collect(child, parent, siblings, all);
    }

    public static final class Node {
        public final int offset, type;
        public final String name;
        public final Node parent;
        public final Map<Integer, Mode10.Record> properties;
        private List<Node> children = List.of();
        private Node(Mode10.Record record, Node parent) {
            offset = record.offset; this.parent = parent; properties = record.properties();
            Mode10.Record typeRecord = record.child("Type"); type = typeRecord == null ? 0 : typeRecord.integer();
            Mode10.Record nameRecord = property("Name"); name = nameRecord == null ? null : nameRecord.text();
        }
        public List<Node> children() { return children; }
        public Mode10.Record property(String key) { return properties.get(Mode10.hash(key)); }
        public float scalar(String key, float fallback) {
            Mode10.Record value = property(key); return value == null ? fallback : value.scalar();
        }
        public List<Marker> markers() {
            Mode10.Record value = property("Markers");
            if (value == null) return List.of();
            if (value.kind != 2) throw Mode10.invalid("Animated marker table");
            ByteBuffer data = value.bytes();
            if (data.remaining() < 4) throw Mode10.invalid("Truncated markers");
            int count = data.getInt();
            if (count < 0 || count > 1024) throw Mode10.invalid("Marker count");
            List<Marker> result = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                if (data.remaining() < 12) throw Mode10.invalid("Truncated marker");
                long start = Integer.toUnsignedLong(data.getInt()), length = Integer.toUnsignedLong(data.getInt());
                int chars = data.getInt();
                if (chars < 0 || chars > 1024 || data.remaining() < chars * 2) throw Mode10.invalid("Marker name");
                StringBuilder name = new StringBuilder(chars);
                for (int c = 0; c < chars; c++) name.append(data.getChar());
                result.add(new Marker(name.toString(), start, length));
            }
            if (data.hasRemaining()) throw Mode10.invalid("Trailing marker bytes");
            return List.copyOf(result);
        }
    }

    /** A zero-length stock marker is a pose; frameCount is retained without inventing a duration. */
    public static final class Marker {
        public final String name;
        public final long start, frameCount;
        public Marker(String name, long start, long frameCount) {
            if (start < 0 || frameCount < 0) throw new IllegalArgumentException("Negative marker span");
            this.name = name; this.start = start; this.frameCount = frameCount;
        }
        public long lastFrame() { return start + Math.max(0, frameCount - 1); }
    }
}
