package dev.sense.framework.fusion;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/** Worker-thread preparation boundary. All mesh texture dependencies are resolved before publishing. */
public final class FxSceneAsset {
    public final FxScene scene;
    public final Map<Integer, FxTexture> textures;
    private FxSceneAsset(FxScene scene, Map<Integer, FxTexture> textures) {
        this.scene = scene; this.textures = Map.copyOf(textures);
    }
    /** Caller owns the stream. Budget bounds total unique decoded texture bytes retained by this asset. */
    public static FxSceneAsset load(InputStream input, FxResources shared, int budget) throws IOException {
        if (budget <= 0 || budget > Mode10.MAX_BYTES) throw new IllegalArgumentException("Asset byte budget");
        FxScene scene = new FxScene(Mode10.read(input));
        FxSceneInstance instance = new FxSceneInstance(scene);
        FxResources scope = new FxResources(scene.document, shared, 0);
        Map<Integer, FxTexture> images = new LinkedHashMap<>(); long bytes = 0;
        for (FxSceneInstance.State state : instance.drawOrder()) {
            if (!state.hasMesh() || !state.usesTexture() || state.textureId == 0 || images.containsKey(state.textureId)) continue;
            FxTexture image = scope.texture(state.textureId); bytes += image.byteCount();
            if (bytes > budget) throw new IllegalArgumentException("Scene textures exceed asset budget");
            images.put(state.textureId, image);
        }
        return new FxSceneAsset(scene, images);
    }
}
