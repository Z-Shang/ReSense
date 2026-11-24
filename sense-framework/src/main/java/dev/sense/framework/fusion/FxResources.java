package dev.sense.framework.fusion;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/** Per-document resource scope. Embedded IDs override a shared parent; decoded cache is byte bounded. */
public final class FxResources {
    private final Map<Integer, Mode10.Record> records = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, FxTexture> cache = new LinkedHashMap<>(16, .75f, true);
    private final FxResources parent;
    private final int budget;
    private int bytes;
    public FxResources(Mode10.Record document, FxResources parent, int budget) {
        if (budget < 0 || budget > Mode10.MAX_BYTES) throw new IllegalArgumentException("Texture cache budget");
        this.parent = parent; this.budget = budget; index(document);
    }
    private void index(Mode10.Record record) {
        if (record.kind == 1 && record.tag == Mode10.hash("Type") && declaresPixels(record)) {
            Map<Integer, Mode10.Record> properties = record.properties();
            Mode10.Record id = properties.get(Mode10.hash("ID"));
            if (id != null && properties.containsKey(Mode10.hash("Pixels"))) {
                if (records.put(id.integer(), record) != null) throw Mode10.invalid("Duplicate local texture ID");
            }
        }
        for (Mode10.Record child : record.children) index(child);
    }
    private static boolean declaresPixels(Mode10.Record record) {
        // Font/type descriptors also use Property records, including name-only declarations.
        // Classify by the Pixels name before applying the strict texture property schema.
        for (Mode10.Record child : record.children) {
            if (child.tag != Mode10.hash("Property") || child.kind != 1 || child.children.isEmpty()) continue;
            Mode10.Record name = child.children.get(0);
            if (name.tag == Mode10.hash("Name") && name.kind == 2 && name.size() == 4 && name.integer() == Mode10.hash("Pixels")) return true;
        }
        return false;
    }
    public synchronized FxTexture texture(int id) {
        FxTexture found = cache.get(id);
        if (found != null) return found;
        Mode10.Record record = records.get(id);
        if (record == null) {
            if (parent != null) return parent.texture(id);
            throw new NoSuchElementException("Missing Mode10 texture " + Integer.toUnsignedString(id, 16));
        }
        found = FxTexture.decode(record);
        if (found.byteCount() <= budget) {
            while (bytes + found.byteCount() > budget) {
                Integer oldest = cache.keySet().iterator().next(); bytes -= cache.remove(oldest).byteCount();
            }
            cache.put(id, found); bytes += found.byteCount();
        }
        return found;
    }
    public synchronized void clear() { cache.clear(); bytes = 0; }
    public synchronized int cachedBytes() { return bytes; }
    public int localTextureCount() { return records.size(); }
}
