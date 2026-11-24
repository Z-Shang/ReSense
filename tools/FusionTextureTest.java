import dev.sense.framework.fusion.*;
import java.nio.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

public final class FusionTextureTest {
    public static void main(String[] args) throws Exception {
        int checked = 0;
        for (String line : Files.readAllLines(Path.of(args[0]))) {
            String[] fields = line.split("\\t");
            Mode10.Record document = Mode10.read(Files.readAllBytes(Path.of(fields[0])));
            FxTexture image = FxTexture.decode(document.children.get(0));
            MessageDigest digest = MessageDigest.getInstance("SHA-256"); digest.update(image.pixels());
            FusionCoreTest.check(HexFormat.of().formatHex(digest.digest()).equals(fields[1]), "Pixel mismatch: " + fields[0]);
            int id = document.children.get(0).properties().get(Mode10.hash("ID")).integer();
            FxResources shared = new FxResources(document, null, image.byteCount());
            FxResources child = new FxResources(Mode10.read(FusionCoreTest.file(new byte[0])), shared, 0);
            FxTexture cached = shared.texture(id);
            FusionCoreTest.check(cached == child.texture(id), "Parent cache not shared");
            FusionCoreTest.check(shared.cachedBytes() == image.byteCount(), "Cache budget");
            shared.clear(); FusionCoreTest.check(shared.cachedBytes() == 0, "Cache clear");
            FxResources uncached = new FxResources(document, null, 0);
            uncached.texture(id); FusionCoreTest.check(uncached.cachedBytes() == 0, "Oversize cache entry");
            try { shared.texture(id ^ 123); throw new AssertionError("Missing texture accepted"); }
            catch (NoSuchElementException expected) { }
            checked++;
        }
        byte[] black = HexFormat.of().parseHex("00000002ffffffff");
        FusionCoreTest.check(Arrays.equals(FxTexture.etc1(black, 4, 4), new byte[48]), "ETC1 block");
        FusionCoreTest.check(Arrays.equals(FxTexture.fastLz(ByteBuffer.wrap(new byte[]{2,97,98,99,32,2}), 6), "abcabc".getBytes()), "FastLZ overlap");
        FusionCoreTest.rejects(() -> FxTexture.fastLz(ByteBuffer.wrap(new byte[]{0,97,32,(byte)255}), 4));
        FusionCoreTest.rejects(() -> FxTexture.fastLz(ByteBuffer.wrap(new byte[]{0,97}), 2));
        System.out.println("PASS " + checked + " original RGBA/dual-ETC1 textures match Python pixel SHA-256; cache and malformed codecs");
    }
}
