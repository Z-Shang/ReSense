package dev.sense.framework.fusion;

import android.opengl.GLES20;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/** GLES2 renderer. Create/use/close on the owning GL context thread; supply predecoded textures. */
public final class FxGlRenderer implements AutoCloseable {
    public enum Blend { SOURCE_OVER, ADD }
    public interface BlendResolver { Blend resolve(int stockCode); }
    public enum Depth { LAYERS, SORTED_3D }
    private final int program, position, uv, matrix, alpha, sampler, tint;
    private static final FxTexture WHITE = new FxTexture(1,1,new byte[]{-1,-1,-1,-1});
    private final int budget;
    private final long owner = Thread.currentThread().getId();
    private final BlendResolver blends;
    private final LinkedHashMap<FxTexture, Integer> textures = new LinkedHashMap<>(16, .75f, true);
    private final IdentityHashMap<FxSceneInstance.State, FloatBuffer> meshes = new IdentityHashMap<>();
    private FxSceneInstance previous;
    private int textureBytes;
    private boolean closed;

    public FxGlRenderer(int textureBudget, BlendResolver blends) {
        if (textureBudget <= 0 || textureBudget > Mode10.MAX_BYTES || blends == null) throw new IllegalArgumentException("Renderer configuration");
        this.budget = textureBudget; this.blends = blends;
        int vertex = shader(GLES20.GL_VERTEX_SHADER, "attribute vec3 aPosition; attribute vec2 aUv; uniform mat4 uMatrix; varying vec2 vUv; void main(){gl_Position=uMatrix*vec4(aPosition,1.0);vUv=aUv;}");
        int fragment;
        try { fragment = shader(GLES20.GL_FRAGMENT_SHADER, "precision mediump float; varying vec2 vUv; uniform sampler2D uTexture; uniform float uAlpha; uniform vec4 uTint; void main(){vec4 c=texture2D(uTexture,vUv)*uTint;float a=c.a*uAlpha;gl_FragColor=vec4(c.rgb*a,a);}"); }
        catch (RuntimeException error) { GLES20.glDeleteShader(vertex); throw error; }
        program = GLES20.glCreateProgram(); GLES20.glAttachShader(program, vertex); GLES20.glAttachShader(program, fragment); GLES20.glLinkProgram(program);
        GLES20.glDeleteShader(vertex); GLES20.glDeleteShader(fragment);
        int[] status = new int[1]; GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0);
        if (status[0] == 0) { String log = GLES20.glGetProgramInfoLog(program); GLES20.glDeleteProgram(program); throw new IllegalStateException(log); }
        position = GLES20.glGetAttribLocation(program, "aPosition"); uv = GLES20.glGetAttribLocation(program, "aUv");
        matrix = GLES20.glGetUniformLocation(program, "uMatrix"); alpha = GLES20.glGetUniformLocation(program, "uAlpha"); sampler = GLES20.glGetUniformLocation(program, "uTexture");
        tint = GLES20.glGetUniformLocation(program, "uTint");
    }
    private static int shader(int type, String source) {
        int shader = GLES20.glCreateShader(type); GLES20.glShaderSource(shader, source); GLES20.glCompileShader(shader);
        int[] status = new int[1]; GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) { String log = GLES20.glGetShaderInfoLog(shader); GLES20.glDeleteShader(shader); throw new IllegalStateException(log); }
        return shader;
    }
    /** Clears transparent. Caller selects camera and blend interpretation explicitly. */
    public void draw(FxSceneInstance instance, Map<Integer, FxTexture> resources, FxCamera camera,
                     float[] view, int width, int height, Depth depth) {
        check(); FxTransform.matrix(view);
        if (width <= 0 || height <= 0 || depth == null) throw new IllegalArgumentException("Viewport/depth");
        if (previous != instance) { meshes.clear(); previous = instance; }
        GLES20.glViewport(0, 0, width, height); GLES20.glDisable(GLES20.GL_SCISSOR_TEST); GLES20.glDisable(GLES20.GL_CULL_FACE);
        GLES20.glDepthMask(true); GLES20.glClearColor(0,0,0,0); GLES20.glClearDepthf(1);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        GLES20.glUseProgram(program); GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glUniform1i(sampler, 0);
        GLES20.glEnableVertexAttribArray(position); GLES20.glEnableVertexAttribArray(uv); GLES20.glEnable(GLES20.GL_BLEND);
        float[] projectionView = FxTransform.multiply(camera.projection(), view);
        List<FxSceneInstance.State> states = new ArrayList<>(instance.drawOrder());
        if (depth == Depth.SORTED_3D) {
            // Straight-alpha sprites: depth test against opaque geometry, then transparent back-to-front.
            GLES20.glEnable(GLES20.GL_DEPTH_TEST); GLES20.glDepthFunc(GLES20.GL_LEQUAL);
            List<FxSceneInstance.State> transparent = new ArrayList<>();
            for (FxSceneInstance.State state : states) {
                if (!drawable(state,resources)) continue;
                FxTexture image = image(resources, state);
                if (state.opacity()*state.tintData()[3] >= 1 && image.isOpaque() && blends.resolve(state.blendCode) == Blend.SOURCE_OVER)
                    drawState(state, image, projectionView);
                else transparent.add(state);
            }
            transparent.sort(Comparator.comparingDouble(s -> centerDepth(s, view)));
            GLES20.glDepthMask(false);
            for (FxSceneInstance.State state : transparent) drawState(state, image(resources, state), projectionView);
        } else {
            GLES20.glDisable(GLES20.GL_DEPTH_TEST); GLES20.glDepthMask(false);
            for (FxSceneInstance.State state : states) if (drawable(state,resources))
                drawState(state, image(resources, state), projectionView);
        }
        GLES20.glDepthMask(true); GLES20.glDisableVertexAttribArray(position); GLES20.glDisableVertexAttribArray(uv);
        int error = GLES20.glGetError();
        if (error != GLES20.GL_NO_ERROR) throw new IllegalStateException("Fusion GL error " + error);
    }
    private static FxTexture image(Map<Integer, FxTexture> resources, FxSceneInstance.State state) {
        if (!state.usesTexture()) return WHITE;
        FxTexture image = resources.get(state.textureId);
        if (image == null) throw new NoSuchElementException("Unresolved texture for " + state.node.name);
        return image;
    }
    private static boolean drawable(FxSceneInstance.State state, Map<Integer,FxTexture> resources) {
        return state.hasMesh() && state.opacity() > 0 &&
                !(state.usesTexture() && state.textureId == 0 && !resources.containsKey(0));
    }
    private static double centerDepth(FxSceneInstance.State state, float[] view) {
        float[] v = state.meshData(); float x = 0, y = 0, z = 0;
        for (int i = 0; i < 4; i++) { x += v[i*5]; y += v[i*5+1]; z += v[i*5+2]; }
        return FxTransform.point(FxTransform.multiply(view, state.worldData()), x/4, y/4, z/4)[2];
    }
    private void drawState(FxSceneInstance.State state, FxTexture image, float[] projectionView) {
        Blend blend = blends.resolve(state.blendCode);
        if (blend == null) throw new UnsupportedOperationException("Unmapped stock blend code " + state.blendCode);
        GLES20.glBlendFunc(GLES20.GL_ONE, blend == Blend.ADD ? GLES20.GL_ONE : GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture(image));
        GLES20.glUniformMatrix4fv(matrix, 1, false, FxTransform.multiply(projectionView, state.worldData()), 0);
        GLES20.glUniform1f(alpha, state.opacity());
        GLES20.glUniform4fv(tint,1,state.tintData(),0);
        FloatBuffer mesh = meshes.get(state);
        if (mesh == null) {
            // Original quad order is top-left, top-right, bottom-right, bottom-left.
            float[] v = state.meshData(); mesh = ByteBuffer.allocateDirect(6*5*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            for (int index : new int[]{0,1,2,0,2,3}) mesh.put(v, index*5, 5);
            mesh.position(0); meshes.put(state, mesh);
        }
        mesh.position(0); GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 20, mesh);
        mesh.position(3); GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 20, mesh);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);
    }
    private int texture(FxTexture image) {
        Integer found = textures.get(image); if (found != null) return found;
        if (image.byteCount() > budget) throw new IllegalArgumentException("Texture exceeds GPU cache budget");
        while (textureBytes + image.byteCount() > budget) {
            FxTexture first = textures.keySet().iterator().next(); deleteTexture(textures.remove(first)); textureBytes -= first.byteCount();
        }
        int[] id = new int[1]; GLES20.glGenTextures(1, id, 0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        ByteBuffer pixels = ByteBuffer.allocateDirect(image.byteCount()); pixels.put(image.pixels()).position(0);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, image.width, image.height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixels);
        textures.put(image, id[0]); textureBytes += image.byteCount(); return id[0];
    }
    private static void deleteTexture(int id) { GLES20.glDeleteTextures(1, new int[]{id}, 0); }
    private void check() {
        if (closed || Thread.currentThread().getId() != owner) throw new IllegalStateException("Renderer closed or wrong GL thread");
    }
    @Override public void close() {
        check(); for (int id : textures.values()) deleteTexture(id);
        textures.clear(); meshes.clear(); previous = null; textureBytes = 0; GLES20.glDeleteProgram(program); closed = true;
    }
}
